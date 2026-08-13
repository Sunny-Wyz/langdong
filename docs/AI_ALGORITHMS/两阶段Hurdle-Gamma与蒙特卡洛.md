# 两阶段 Hurdle-Gamma 预测与蒙特卡洛安全库存

**最后更新**：2026-08-13  
**口径**：以当前生产代码为准，不是旧 RF / SBA 文档。  
**主链路**：`StockThresholdService` → Python `/api/algorithm/train|predict` → `/api/algorithm/inventory-calc` → 落库 `ai_forecast_result` / `biz_part_classify`

本文说明两件事：

1. **Gamma 预测**：两阶段 Hurdle-Gamma 如何把「下个月会不会消耗、消耗多少」变成分布参数 \(p_t, \mu_t, k\)。
2. **蒙特卡洛模拟**：如何用这些参数仿真提前期累计需求，算出补货点（ROP）和安全库存（SS）。

---

## 1. 为什么这样拆

工业备件月度消耗大多是**间歇需求**：很多月为 0，少数月为正整数，且正需求右偏。

若直接对 \(y\) 做均方误差回归：

- 大量零会把点预测拉向 0；
- 得不到可用的概率分布，后面无法按服务水平备库。

因此生产模型把问题拆成两段（Hurdle / 栅栏）：

| 阶段 | 问题 | 模型 | 输出 |
|---|---|---|---|
| 一 | 这个月会不会发生需求？ | `XGBClassifier(objective='binary:logistic')` | 发生概率 \(p_t \in [0,1]\) |
| 二 | 一旦发生，量是多少？ | `XGBRegressor(objective='reg:gamma')` | 正需求条件均值 \(\mu_t > 0\) |

正需求再按 **Gamma 分布**刻画波动，形状参数 \(k\) 用标准化残差做极大似然，并按 XYZ 分组共享。

点预测：

\[
\hat{D}_t = p_t \cdot \mu_t
\]

库存决策不直接用 \(\hat{D}_t\)，而是把 \((p_t, \mu_t, k)\) 送进蒙特卡洛，对提前期内的累计需求抽样，再取服务水平分位数。

---

## 2. 端到端流水线

目标月统一为**当前自然月的下一个月**（`ForecastTargetMonths.defaultTargetMonth()`）。定时、手动重算、任务中心共用这一口径。

```
领用出库（历史月消耗）
        │
        ▼
ABC×XYZ 分类（截止上月，防泄露）
        │
        ▼
Java FeatureBuilder 构造训练 / 推理特征
        │
        ├─ POST /api/algorithm/train     全量样本拟合 clf + reg + k
        │
        └─ POST /api/algorithm/predict   得到 p_t, μ_t, k, [L, U]
                    │
                    ▼
Java 用 90% 区间反推蒙特卡洛用的 k
                    │
                    ▼
POST /api/algorithm/inventory-calc
        工作日比例分配 × M=10000 次仿真
                    │
                    ▼
ROP = ceil(Q_α)    SS = ROP − ceil(Mean)
                    │
                    ▼
写 ai_forecast_result + biz_part_classify
        │
        ▼
ReplenishmentService：库存 ≤ ROP 则生成补货建议
```

触发入口：

| 入口 | 类 | 说明 |
|---|---|---|
| 月初定时 | `MonthlyForecastScheduler` | cron 默认 `0 0 1 1 * ?`（每月 1 日 01:00） |
| 手动重算 | `AiForecastController` `/api/ai/forecast/trigger` | 全量 |
| 任务中心 | `HurdleGammaJobService` | 可按备件编码过滤推理；**训练仍用全量样本** |

---

## 3. 特征（月度主链路）

特征由 Java `FeatureBuilder` 构造，对应论文表 3-2。  
**防泄露**：为目标月 \(t\) 构造特征时，只读严格早于 \(t\) 的历史；月份编码用 \(t\) 的日历月（事先可知，不算泄露）。目标月之前没有任何消耗历史的备件标记为数据不足，跳过。

生产训练 / 推理实际发送的是 **11 维阶段二向量**（`FeatureVector.toStage2Array()`）：

| # | 字段 | 含义 |
|---:|---|---|
| 1 | `lag_1` | 上月消耗 |
| 2 | `lag_3_mean` | 近 3 月消耗均值 |
| 3 | `lag_3_std` | 近 3 月样本标准差 |
| 4 | `zero_ratio_6` | 近 6 月零需求月份占比 |
| 5 | `EquipHr` | 上月关联设备运行小时 |
| 6 | `RepairCnt` | 上月关联设备维修工单数 |
| 7 | `Month` | 目标月日历月 1–12 |
| 8 | `ABC_code` | 上月分类：A=3, B=2, C=1 |
| 9 | `XYZ_code` | 上月分类：X=1, Y=2, Z=3 |
| 10 | `pos_lag_1` | 最近一次正需求量 |
| 11 | `pos_lag_3_mean` | 最近至多三次正需求均值 |

训练窗口默认 **36 个月**，标签月止于目标月的上一个月。每个（备件, 历史月）一条样本，标签是该月真实消耗。

---

## 4. 两阶段 Hurdle-Gamma

实现：`python-ai-service/app/models/demand_forecast.py` 的 `HurdleGammaModel`。  
超参对齐论文表 3-3，随机种子 `42`。

### 4.1 阶段一：会不会发生需求

标签：

\[
I_t = \mathbf{1}(y_t > 0)
\]

分类器输出：

\[
p_t = P(I_t = 1 \mid x_t)
\]

代码：`XGBClassifier(objective='binary:logistic')`，取 `predict_proba[:, 1]`。

若训练集标签全 0 或全正，分类器退化，日志告警，但仍会 `fit`。

### 4.2 阶段二：正需求服从 Gamma

只在 \(y > 0\) 的样本上训练回归器。

XGBoost `reg:gamma` 使用 log 链接，预测的是正需求**条件均值** \(\mu_t = \mathbb{E}[Y \mid Y>0, x_t]\)。

约定参数化（与 NumPy / SciPy 一致）：

\[
Y \mid Y>0 \;\sim\; \mathrm{Gamma}(k,\; \theta),\qquad \theta = \frac{\mu}{k}
\]

其中：

- \(k\)：形状（shape）
- \(\theta\)：尺度（scale）
- \(\mathbb{E}[Y]=\mu=k\theta\)
- \(\mathrm{Var}(Y)=\mu^2 / k\)

因此 \(k\) 越大，正需求越集中；\(k\) 越小，右尾越重。

正样本不足 2 条时，回归退化为在全样本上 `fit`，\(k\) 全部置 `1.0`。

### 4.3 形状参数 \(k\) 的极大似然

对正样本标准化残差：

\[
r_i = \frac{y_i}{\hat\mu_i},\qquad \hat\mu_i=\max(\mathrm{reg.predict}(x_i),\; 10^{-5})
\]

若 \(Y\sim\mathrm{Gamma}(k,\mu/k)\) 且模型无偏，则 \(r\sim\mathrm{Gamma}(k,1/k)\)，均值为 1。  
对数似然对 \(k\) 求导后，MLE 满足：

\[
\ln k - \psi(k) = c,\qquad c=\ln(\bar r)-\overline{\ln r}
\]

\(\psi\) 是 digamma。`solve_gamma_k()` 用 Newton-Raphson：

\[
g(k)=\ln k-\psi(k)-c,\qquad
g'(k)=\frac{1}{k}-\psi_1(k)
\]

\[
k \leftarrow k - \frac{g(k)}{g'(k)}
\]

- 初值用矩估计 \(k_0=\bar r^2 / \mathrm{Var}(r)\)
- \(c\le 0\)、样本 \(<2\)、方差为 0：返回 `1.0`
- \(k\) 越界则折半；最终裁剪到 \([10^{-3}, 10^5]\)

### 4.4 XYZ 分组共享 \(k\)

备件级正样本往往很少，逐件估 \(k\) 不稳定。实现与论文表 3-11 一致：**同一 XYZ 组共享一个 \(k\)**。

```
global_k = MLE(全部正样本残差)

对 group in {X, Y, Z}:
    若该组正样本 ≥ 2:  k[group] = MLE(该组残差)
    否则:              k[group] = global_k
```

推理时按该备件的 XYZ 取 \(k\)，再裁剪：

```python
k_val = clip(k_val, 0.8, 12.0)
```

对应变异系数大约 \(0.29\sim 1.12\)，避免小样本把 \(k\) 估得过大、90% 区间塌缩。

XYZ 本身由 CV² 划分（Python 周模型与 Java 月度分类阈值一致）：

| 条件 | 类别 |
|---|---|
| 正样本 \(< 3\) | Z |
| \(\mathrm{CV}^2 < 0.5\) | X（稳） |
| \(0.5 \le \mathrm{CV}^2 < 1.0\) | Y（波动） |
| \(\mathrm{CV}^2 \ge 1.0\) | Z（乱） |

### 4.5 90% 预测区间

对**正需求 Gamma** 取 5% / 95% 分位（SciPy `gamma.ppf`）：

\[
L = F^{-1}_{Y}(0.05),\qquad
U = F^{-1}_{Y}(0.95)
\]

其中 \(Y\sim\mathrm{Gamma}(k,\;\mu/k)\)。

注意：这个区间是**条件于发生需求**的量区间，不是把零膨胀后的无条件区间。落库字段：

| 字段 | 含义 |
|---|---|
| `occurrence_prob` | \(p_t\) |
| `positive_qty` | \(\mu_t\) |
| `predict_qty` | \(\hat D_t=p_t\mu_t\) |
| `lower_bound` / `upper_bound` | 正需求 90% 区间 |
| `algo_type` | `TWO_STAGE`（前端展示「两阶段 Hurdle-Gamma」） |

### 4.6 训练超参（表 3-3）

| 项 | 分类器 | 回归器 |
|---|---|---|
| objective | `binary:logistic` | `reg:gamma` |
| n_estimators | 100 | 150 |
| max_depth | 4 | 5 |
| learning_rate | 0.1 | 0.08 |
| min_child_weight | 3 | 2 |
| subsample / colsample_bytree | 0.8 / 0.8 | 0.8 / 0.8 |
| reg_alpha / reg_lambda | 0 / 1.0 | 0.01 / 1.0 |
| random_state | 42 | 42 |

---

## 5. 蒙特卡洛提前期需求（算法 3-2）

实现：`python-ai-service/app/services/inventory_calc.py` 的 `simulate_lead_time_demand()`。  
Java 入口：`LeadTimeDemandSimulator.calculateSafetyStock()`，HTTP `POST /api/algorithm/inventory-calc`。

### 5.1 要仿真什么

采购提前期 \(L\) 一般不是整月。订单一天可能从某月任意工作日发出，提前期会跨月。跨月后，累计需求是若干个月度需求的**按工作日比例切片再相加**。

月度需求本身是零膨胀的：

\[
D = I \cdot Y,\qquad I\sim\mathrm{Bernoulli}(p),\quad Y\sim\mathrm{Gamma}(k,\;\mu/k)
\]

多期比例和没有简单闭式，所以用蒙特卡洛抽经验分布。

### 5.2 默认参数

| 符号 | 配置项 | 默认 | 含义 |
|---|---|---|---|
| \(M\) | `forecast.monte-carlo.simulations` | 10000 | 仿真次数 |
| \(W\) | `forecast.monte-carlo.workingDays` | 22 | 一个月工作日 |
| \(L\) | `spare_part.lead_time` | 缺省 30 | 提前期（天） |
| \(\alpha\) | 按 ABC | A=0.99 / B=0.95 / C=0.90 | 周期服务水平（CSL） |
| \(z\) | `forecast.monte-carlo.intervalZ` | 1.645 | 用 90% 区间反推标准差 |

\(L=0\) 或 \(\mu\le 0\) 或 \(k\le 0\)：直接返回 ROP=0, SS=0。

### 5.3 Java 如何把区间变成蒙特卡洛的 \(k\)

Python `predict` 已经返回了 MLE \(k\)，但 **`StockThresholdService` 落库存时没有把这个 \(k\) 传给蒙特卡洛**。  
`LeadTimeDemandSimulator` 用 90% 区间按正态近似重算：

\[
\hat\sigma = \frac{U-L}{2\times 1.645}
\]

\[
k_{\mathrm{MC}} =
\begin{cases}
\mu^2 / \hat\sigma^2 & \hat\sigma > 0 \\
1 & \text{否则}
\end{cases}
\]

非法 / 非正则回退 \(k_{\mathrm{MC}}=1\)。随后请求体是：

```json
{
  "p_t": 发生概率,
  "mu_t": 正需求均值,
  "k": k_MC,
  "L": 提前期天数,
  "W": 22,
  "M": 10000,
  "alpha": 服务水平
}
```

这是当前实现口径：蒙特卡洛用的 \(k\) 是**区间反推值**，不是磁盘模型里的 MLE \(k\)。两者通常接近，但不保证相等。

### 5.4 工作日比例分配

对 \(m=1,\ldots,M\) 独立仿真一次：

1. 订单发出日 \(s \sim \mathrm{Uniform}\{1,\ldots,W\}\)
2. 当月还能覆盖的天数  
   \(d_1=\min(L,\; W-s+1)\)
3. 抽当月需求  
   \(I_1\sim\mathrm{Bernoulli}(p),\quad Y_1\sim\mathrm{Gamma}(k,\;\mu/k)\)  
   计入  
   \(D_L \mathrel{+}= (d_1/W)\cdot I_1\cdot Y_1\)
4. 剩余天数 \(L_{\mathrm{rem}}=L-d_1\)。只要还有剩余，就再开一个完整工作月：  
   \(d=\min(L_{\mathrm{rem}}, W)\)，独立再抽一对 \((I,Y)\)，加上 \((d/W)\cdot I\cdot Y\)，直到 \(L_{\mathrm{rem}}=0\)。

实现是 NumPy 向量化：一次生成 \(M\) 条路径，用 `while any(L_rem>0)` 处理跨月。

直观理解：一个月的需求 \(I\cdot Y\) 均匀摊在 \(W\) 个工作日上；提前期覆盖到该月多少天，就按比例拿走多少需求。不同月独立抽样，因此跨月是独立 Hurdle-Gamma 的加权和。

### 5.5 从样本到 ROP / SS

得到 \(M\) 个提前期累计需求样本 \(\{D_L^{(m)}\}\) 后：

\[
Q_\alpha = \mathrm{Percentile}(\{D_L\},\; \alpha)
\]

\[
\mathrm{ROP} = \lceil Q_\alpha \rceil
\]

\[
\mathrm{SS} = \mathrm{ROP} - \lceil \overline{D_L} \rceil
\]

含义：

- **ROP**：提前期需求的 \(\alpha\) 分位数（向上取整）。库存降到这个点再订，大约有 \(\alpha\) 的概率能撑过到货。
- **SS**：相对「提前期平均需求」多备的缓冲。  
  期望提前期需求近似 \(\overline{D_L}\approx (L/W)\cdot p\cdot\mu\)（订单日起点随机时的一阶近似）。

`LeadTimeDemandSimulator` 把返回的 `rop` 同时写入诊断字段 `leadTimeDemandQuantile`（已取整后的值）。

### 5.6 服务水平怎么取

`ForecastProperties.Classify.serviceLevelOf(abc)`：

| ABC | \(\alpha\) | 含义 |
|---|---|---|
| A | 0.99 | 关键件，几乎不允许缺货 |
| B | 0.95 | 普通件 |
| C / 未知 | 0.90 | 宽松 |

分类权重（与论文 F9 / 业务实现一致，写入 `forecast.classify.*`）：

- 年消耗金额 0.40
- 设备关键度 0.25
- 提前期 0.20
- 替代难度 0.15
- 帕累托：累计前 70% 为 A，70%–90% 为 B，其余 C

---

## 6. 落库与补货

对每个有效备件：

1. 删除该目标月、该备件旧的预测 / 分类行（幂等重算）。
2. 插入 `ai_forecast_result`：`predict_qty`、区间、\(p_t\)、\(\mu_t\)、`lead_time_quantile`、`TWO_STAGE`、`model_version=two-stage-python-yyyy-MM`。
3. 插入 `biz_part_classify`：ABC/XYZ、综合分、CV²、**本轮算出的 SS / ROP / 服务水平%**、策略码如 `AY`。
4. 全量任务才把模型注册为 `demand-forecaster-two-stage` / `PRODUCTION`；任务中心单备件任务不刷注册表。
5. `ReplenishmentService` 比较当前库存与 ROP，低于阈值则生成补货建议。

数据不足（无特征、无分类、无历史）只在内存里标 `ForecastResult.insufficient`，不写预测行。

---

## 7. 数值例子（手算 intuition）

设某 B 类备件：

- \(p_t=0.40\)，\(\mu_t=10\)，\(k=4\)
- 则无条件月均 \(\hat D=4\) 件
- \(L=11\) 天，\(W=22\)，\(\alpha=0.95\)

一次仿真若抽到 \(s=12\)：当月只剩 11 天，恰好 \(d_1=11=L\)，不跨月。  
抽到 \(I=1,Y=12\) 时，本次累计需求 \(= (11/22)\times 12=6\)。

重复 10000 次后，若 95% 分位数是 9.2、样本均值是 2.1，则：

\[
\mathrm{ROP}=\lceil 9.2\rceil=10,\qquad
\mathrm{SS}=10-\lceil 2.1\rceil=8
\]

库存降到 10 再订；其中约 8 件是为波动准备的安全库存。

---

## 8. 代码与接口对照

| 步骤 | 位置 |
|---|---|
| 目标月 = 下月 | `forecast/util/ForecastTargetMonths.java` |
| 11 维特征 / 防泄露 | `forecast/feature/FeatureBuilder.java` |
| 编排训练、推理、落库 | `forecast/service/StockThresholdService.java` |
| 组装 MC 请求 | `forecast/montecarlo/LeadTimeDemandSimulator.java` |
| 分类 + 服务水平 | `forecast/config/ForecastProperties.java` |
| 两阶段模型 + \(k\) MLE | `python-ai-service/app/models/demand_forecast.py` |
| 蒙特卡洛抽样 | `python-ai-service/app/services/inventory_calc.py` |
| HTTP | `/api/algorithm/train`、`/predict`、`/inventory-calc` |
| 滚动回测（不覆盖生产模型） | `/api/algorithm/fit_predict_ephemeral` |

配置默认值（`application.yml` 的 `forecast.*` 可覆盖）：

```yaml
forecast:
  history-months: 36
  scheduler:
    cron: 0 0 1 1 * ?
  monte-carlo:
    seed: 20260518          # Java 配置项；见下一节
    simulations: 10000
    working-days: 22
    interval-z: 1.645
  classify:
    service-level-a: 0.99
    service-level-b: 0.95
    service-level-c: 0.90
```

Python 进程内模型文件：`MODEL_DIR`（默认 `/tmp/langdong_models/algorithm_model/hurdle_gamma_model.pkl`）。

---

## 9. 实现注意（读代码时不要误判）

1. **生产月度主链路是 Java 特征 + Python 模型**。`DemandForecaster` 里还有一套 19 维周粒度递归预测，给周接口 / 旧包装用，**不是**月初全量重算路径。
2. **蒙特卡洛用的 \(k\) 是区间反推值**，不是 `predict` 返回的 MLE \(k\)。`StockThresholdService` 解析预测时只取了 `p_t / mu_t / lower_bound / upper_bound`。
3. **`forecast.monte-carlo.seed=20260518` 目前没有传到 Python**。`simulate_lead_time_demand()` 使用 NumPy 全局 RNG，未 `seed`，两次全量重算的 ROP/SS 可能差 1 件量级。论文复现若要求逐路径可重复，需要把种子加入 `/inventory-calc` 请求。
4. **90% 区间是正需求条件区间**，点预测 `predict_qty` 才是无条件期望 \(p\mu\)。前端不要把区间当成「含零的总需求区间」。
5. **旧文档**已迁到 `docs/archive/历史算法/`（旧版需求预测链路、旧版安全库存计算）。当前生产 SS 以本文蒙特卡洛为准。

---

## 10. 和旧动态法的差别

旧文档（`docs/archive/历史算法/旧版安全库存计算.md`）用正态近似：

\[
\sigma_d=\frac{U-L}{2\times 1.645},\qquad
\mathrm{SS}=\lceil k_{\mathrm{svc}}\cdot\sigma_d\cdot\sqrt{L}\rceil,\qquad
\mathrm{ROP}=\lceil \bar d\cdot L+\mathrm{SS}\rceil
\]

当前实现不再走这条解析式，原因：

- 需求是 **Bernoulli × Gamma**，不是正态；
- 提前期会跨月，不能用 \(\sqrt{L}\) 把月波动直接折成天；
- 服务水平 \(\alpha\) 直接定义为经验分位数，和 ABC 的 99/95/90 一一对应。

区间反推 \(\sigma\) 只还用在一件事上：给蒙特卡洛准备一个与 90% 区间一致的 \(k_{\mathrm{MC}}\)。
