# 预测性维护模块 — 实现思路详解

## 一、问题建模

### 1.1 RUL 预测本质
剩余寿命（RUL, Remaining Useful Life）预测是一个**时间序列回归问题**。
给定备件的历史传感器序列 `{x_{t-L}, ..., x_t}`，预测未来还能安全运行的小时数 `y = RUL`。

数学表达：
```
RUL_t = f(x_{t-L:t}) + ε
```
其中 `L` 为观测窗口长度（本实现取 30），`ε` 为预测误差。

### 1.2 数据库扩展设计
原始 `spare_db` 只有 `spare_part` 库存表，缺乏时序传感器数据。
需新增 `spare_part_sensor_log` 表：

| 字段 | 类型 | 含义 |
|---|---|---|
| spare_part_id | BIGINT | 关联备件 ID |
| recorded_at | DATETIME | 采集时间戳 |
| operating_hours | FLOAT | 累计运行小时（核心特征） |
| temperature | FLOAT | 温度 (°C) |
| vibration | FLOAT | 振动幅度 (mm/s) |
| pressure | FLOAT | 压力 (MPa) |
| current_load | FLOAT | 电流负载 (A) |
| rpm | FLOAT | 转速 (r/min) |
| error_code | INT | 错误码（0=正常，>0=异常） |
| is_failed | TINYINT | 是否发生故障事件 |

Python 模块启动时会自动检测并建表（`ensure_sensor_table_exists()`），无需手动迁移。

---

## 二、为什么选 LSTM？

### 2.1 候选方案对比

| 方法 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| ARIMA | 简单可解释 | 只处理单变量线性退化 | 不适合多传感器 |
| XGBoost | 鲁棒、特征重要性好 | 忽略时序顺序 | 可作为基线对比 |
| **LSTM** | 捕捉长期非线性时序依赖，多变量 | 需要较多数据 | **本方案选择** |
| Transformer | 并行化、注意力机制 | 数据需求更大，实现复杂 | 适合大数据场景 |

LSTM 的门控机制（遗忘门、输入门、输出门）能有效建模设备退化的**非线性、多步骤**过程，
且工业 RUL 预测领域（如 NASA CMAPSS 数据集）已有大量成功案例。

### 2.2 模型架构决策

```
输入层: (batch, 30, 7)       # 30 个时间步 × 7 个特征
   ↓
LSTM(128, return_sequences=True)   ← 保留全序列，捕捉短期模式
   ↓
Dropout(0.2)                       ← 正则化 + Monte Carlo 推断的关键
   ↓
LSTM(64, return_sequences=False)   ← 聚合序列为固定维度向量
   ↓
Dropout(0.2)
   ↓
Dense(32, activation='relu')       ← 非线性特征变换
   ↓
Dense(1)                           ← 输出：预测 RUL（小时）
```

关键超参数选择理由：
- **窗口大小 L=30**：工程经验表明 20~50 个时间步能平衡信息量与计算量
- **Huber Loss**：比 MSE 对异常值鲁棒，比 MAE 在 0 附近梯度更稳定，适合 RUL 回归
- **Early Stopping (patience=10)**：防止过拟合，自动选择最佳 epoch

---

## 三、置信区间 — Monte Carlo Dropout

### 3.1 理论依据
Gal & Ghahramani (2016) 证明：
> 训练时使用 Dropout 等价于对贝叶斯神经网络权重后验分布的变分推断近似。
> 推断时保持 Dropout 激活并多次采样，即可得到预测的不确定性估计。

### 3.2 实现步骤
```python
# 关键：推断时传入 training=True，强制保持 Dropout 激活
predictions = [model(X, training=True).numpy().flatten()[0]
               for _ in range(100)]          # 100 次采样
mean    = np.mean(predictions)               # 点预测
std     = np.std(predictions)                # 不确定度
ci_low  = max(0, mean - 1.96 * std)         # 95% 置信下界
ci_high = mean + 1.96 * std                 # 95% 置信上界
```

100 次采样通常足够稳定估计分布（方差收敛），计算开销可接受（通常 < 1s）。

---

## 四、可解释性 — SHAP

### 4.1 选择 SHAP 的理由

| 属性 | SHAP | LIME |
|---|---|---|
| 理论基础 | Shapley 值（博弈论最优解） | 局部线性近似 |
| 一致性保证 | 严格满足（4 大公理） | 不保证 |
| 对神经网络支持 | KernelExplainer（模型无关） | 同样支持 |

SHAP 的 Shapley 值满足**效率性、对称性、哑元性、可加性**，解释更可靠稳定。

### 4.2 LSTM 的 SHAP 实现策略

LSTM 是动态图模型，无法直接用 TreeExplainer。采用 `KernelExplainer`（模型无关）：

```
步骤 1: 将 3D 输入 (1, 30, 7) 展平为 (1, 210)
步骤 2: 用 K-means 聚类背景数据生成 50 个代表点（降低计算量）
步骤 3: KernelExplainer 估算各维度的 Shapley 值
步骤 4: 对每个特征，跨 30 个时间步求绝对值均值
步骤 5: 取 Top-3 特征，附带影响方向（+/-）
```

---

## 五、数据不足处理策略（三层降级）

当历史数据 < 100 条时，LSTM 训练不可靠，采用分层降级：

```
数据量 ≥ 100 条  →  正常训练 LSTM 模型（主路径）
                          ↓
数据量 10~99 条  →  合成数据补充至 200 条，再训练
                          ↓
数据量 < 10 条   →  加载预训练通用模型（rul_model.h5）
                          ↓
文件不存在       →  返回保守默认估计 + 明确警告提示
```

### 5.1 合成数据生成原理

基于物理退化模型生成仿真传感器序列：

```python
# 退化模型：基线 + 线性趋势 + 周期负载波动 + 随机噪声
degradation(t) = baseline
               + slope * t                    # 线性老化趋势
               + A * sin(2π * f * t)         # 周期负载波动
               + N(0, σ²)                    # 传感器随机噪声
```

`slope` 从现有数据的统计特性估算，确保合成数据与真实退化规律一致。

---

## 六、预警机制设计

```
RUL < 500h          →  CRITICAL 紧急预警，立即安排维护
500h ≤ RUL < 1000h  →  WARNING  计划维修窗口
RUL ≥ 1000h         →  OK       运行正常
```

阈值在 `MODEL_CONFIG['rul_threshold']` 中统一配置，可按备件类别调整。

---

## 七、生产部署考量

### 7.1 模型持久化

**关键原则：模型与标准化器必须配套保存/加载**

```
rul_model.h5       ← Keras HDF5（架构 + 权重）
rul_scaler.pkl     ← MinMaxScaler（fit 的 fit_params）
```

若只加载模型而丢失 Scaler，推断时特征尺度会错误，导致预测完全失准。

### 7.2 与 Spring Boot 集成方式

`predict_rul(spare_part_id)` 返回标准 JSON 结构，Spring Boot 可通过以下方式调用：

```java
// 方式一：ProcessBuilder 调用 Python 子进程
ProcessBuilder pb = new ProcessBuilder("python3", "predictive_maintenance.py", "--id", sparePartId);

// 方式二：将 Python 模块包装为 FastAPI 微服务（推荐生产环境）
// GET http://localhost:8001/predict?spare_part_id=42
```

---

## 八、关键技术引用

1. **LSTM 原理**：Hochreiter & Schmidhuber (1997). Long Short-Term Memory. *Neural Computation*, 9(8).
2. **Monte Carlo Dropout**：Gal & Ghahramani (2016). Dropout as a Bayesian Approximation. *ICML 2016*.
3. **SHAP**：Lundberg & Lee (2017). A Unified Approach to Interpreting Model Predictions. *NeurIPS 2017*.
4. **RUL 预测综述**：Lei et al. (2018). Machinery health prognostics: A systematic review. *Mechanical Systems and Signal Processing*, 104.

---
---

# 智能补货建议模块 — 实现思路详解

## 一、需求分析与问题建模

### 1.1 补货预测 vs RUL 预测：核心差异

| 维度 | 预测性维护 (RUL) | 智能补货 (Demand) |
|---|---|---|
| 预测目标 | 单个备件剩余寿命（标量） | 未来 M 个月的需求量（向量） |
| 输入粒度 | 每小时/每分钟传感器采样 | **每月**聚合消耗数据 |
| 数据密度 | 高（数千条/备件） | 低（通常 12~36 条/备件） |
| 时间尺度 | 小时级 | 月级 |
| 输出维度 | 1（RUL 小时数） | **M**（未来 M 个月各月需求） |
| 业务决策 | 何时维修 | **买什么、买多少、何时买、找谁买** |

**关键洞察**：RUL 预测回答「备件还能用多久」，补货预测回答「未来会消耗多少」。
两者互补：RUL 预警触发 → 触发补货建议 → 确保库存充足。

### 1.2 补货问题的四要素

```
                    ┌──────────────────────────────┐
                    │    suggest_replenishment()     │
                    ├──────────────────────────────┤
                    │  1. 买多少？                  │
                    │     采购量 = 预测需求          │
                    │           - 当前库存           │
                    │           + 安全库存           │
                    ├──────────────────────────────┤
                    │  2. 何时买？                  │
                    │     建议日期 = 今天            │
                    │       + (库存 / 日均消耗)      │
                    │       - 供应商交货期            │
                    ├──────────────────────────────┤
                    │  3. 找谁买？                  │
                    │     综合绩效 = 0.4×质量        │
                    │              + 0.3×及时率       │
                    │              + 0.3×价格竞争力    │
                    ├──────────────────────────────┤
                    │  4. 为什么？                  │
                    │     SHAP 解释预测依据          │
                    └──────────────────────────────┘
```

---

## 二、数据库扩展设计

### 2.1 新增两张表

现有 `spare_part` 表（id, name, quantity, price, supplier, …）**只记录当前库存快照**，
缺少两类关键数据：

**表 1：`spare_part_consumption_log`（月度消耗日志）**

| 字段 | 类型 | 含义 |
|---|---|---|
| spare_part_id | BIGINT | 关联备件 ID |
| record_month | DATE | 记录月份（格式 YYYY-MM-01） |
| outbound_qty | INT | 当月出库数量（核心特征） |
| repair_count | INT | 当月维修工单数 |
| avg_unit_price | DECIMAL(10,2) | 当月采购均价 |
| working_days | INT | 当月工作日数 |

- 联合唯一键 `(spare_part_id, record_month)` 防止重复录入
- 这是 LSTM 的**主训练数据源**

**表 2：`supplier_performance`（供应商绩效）**

| 字段 | 类型 | 含义 |
|---|---|---|
| supplier_name | VARCHAR(200) | 供应商名称 |
| spare_part_id | BIGINT | 关联备件（NULL=通用评分） |
| quality_score | FLOAT | 质量合格率 (0~1) |
| price_score | FLOAT | 价格竞争力 (0~1) |
| on_time_rate | FLOAT | 按时交付率 (0~1) |
| lead_time_days | INT | 平均交货期（天） |

- 供应商选择公式：`score = 0.4 × quality + 0.3 × on_time + 0.3 × price`

### 2.2 与 predictive_maintenance.py 的数据库共享

两个模块共用 `spare_db` 数据库和 `DB_CONFIG` 配置。
`smart_replenishment.py` 通过 `from predictive_maintenance import DB_CONFIG, get_db_engine`
复用数据库连接逻辑，避免重复代码。若 `predictive_maintenance` 模块不存在，则本地定义。

---

## 三、LSTM 架构适配（从 RUL 到需求预测）

### 3.1 RUL 模型 vs 需求模型的架构差异

```
[RUL 模型]                           [需求模型]
输入: (batch, 30, 7)                  输入: (batch, 12, 6)
  ↓                                     ↓
LSTM(128, seq=True)                   LSTM(64, seq=True)       ← 更小：月度数据更稀疏
  ↓                                     ↓
Dropout(0.2)                          Dropout(0.2)
  ↓                                     ↓
LSTM(64)                              LSTM(32)
  ↓                                     ↓
Dropout(0.2)                          Dropout(0.2)
  ↓                                     ↓
Dense(32, relu)                       Dense(16, relu)
  ↓                                     ↓
Dense(1)  →  RUL                      Dense(3)  →  未来3个月需求   ← 多输出
```

### 3.2 关键设计决策

**为什么窗口改为 12 个月？**
- 消耗数据按月聚合，12 个月窗口覆盖完整年度周期（捕捉季节性）
- 工厂备件消耗通常有年度周期模式（年初检修高峰、夏季减产等）

**为什么网络更小（64/32 vs 128/64）？**
- 月度数据每备件通常只有 12~36 条，过大的网络极易过拟合
- 奥卡姆剃刀：用更少参数捕捉月度趋势足矣

**为什么输出 3 个月（而非 1 个月）？**
- 采购有交货期（通常 7~30 天），需要提前 1~3 个月预判需求
- 多步输出避免了自回归累积误差（direct multi-step 策略）

**月份的循环编码：**
月份 1~12 不应直接作为数值输入（12月到1月不是"12→1的跳变"），使用正弦/余弦编码：
```python
month_sin = sin(2π × month / 12)
month_cos = cos(2π × month / 12)
```
这样 12月和1月在特征空间中是相邻的。

---

## 四、补货量计算公式

### 4.1 核心公式

```python
# 预测未来 M 个月总需求
total_demand = sum(predicted_demand[0:M])

# 安全库存 = 日均消耗 × 交货期天数 × 安全系数(1.5)
safety_stock = daily_avg_consumption * lead_time_days * 1.5

# 建议采购量（不小于 0）
suggested_qty = max(0, total_demand - current_stock + safety_stock)

# 建议采购日期
days_until_stockout = current_stock / daily_avg_consumption
suggested_date = today + days_until_stockout - lead_time_days
# 若 suggested_date ≤ today → 已紧急，标记为"立即采购"
```

### 4.2 预警触发条件

```
预测总需求 > 当前库存 × 1.5  →  🔴 HIGH：高优先级补货，立即启动采购
预测总需求 > 当前库存        →  🟡 MEDIUM：需要补货，进入正常采购流程
预测总需求 ≤ 当前库存        →  🟢 LOW：库存充足，暂不需补货
```

---

## 五、与预测性维护模块的代码复用

### 5.1 复用清单

| 组件 | 来源 | 复用方式 |
|---|---|---|
| `DB_CONFIG` | `predictive_maintenance.py` | `import` 直接导入 |
| `get_db_engine()` | `predictive_maintenance.py` | `import` 直接导入 |
| MinMaxScaler 预处理模式 | `predictive_maintenance.py` | 结构相同，特征列不同 |
| Monte Carlo Dropout 推断 | `predictive_maintenance.py` | 适配多输出（1D → MD） |
| SHAP KernelExplainer 框架 | `predictive_maintenance.py` | 适配需求特征名 |
| 模型 save/load 模式 | `predictive_maintenance.py` | 不同文件名 |
| 合成数据降级策略 | `predictive_maintenance.py` | 适配月度消耗模式 |

### 5.2 导入策略

```python
# 优先从预测性维护模块复用，模块不存在时本地降级定义
try:
    from predictive_maintenance import DB_CONFIG, get_db_engine
    _PM_AVAILABLE = True
except ImportError:
    _PM_AVAILABLE = False
    # 本地定义 DB_CONFIG 和 get_db_engine() ...
```

---

## 六、数据不足降级策略

需求预测对数据量的要求：
- LSTM 至少需要 `窗口(12) + 输出(3) = 15` 个月数据
- 考虑训练/验证划分，理想 ≥ 24 个月

```
数据量 ≥ 6 个月  →  正常训练 LSTM
数据量 3~5 个月  →  生成合成月度消耗数据补充至 24 个月
数据量 < 3 个月  →  统计平均法（月均消耗 × M 个月）
数据量 = 0       →  返回默认保守估计 + 警告
```

### 6.1 统计平均法降级

当数据极少时，放弃 LSTM，使用简单但可靠的统计方法：
```python
monthly_avg = mean(available_outbound_qty)
predicted_demand = [monthly_avg] * M  # M 个月都用均值
```

附带高不确定性标记，告知用户预测可靠性较低。

---

## 七、SHAP 解释适配

需求模型的 SHAP 解释侧重点不同于 RUL：

| RUL 的 SHAP 解释 | 需求的 SHAP 解释 |
|---|---|
| "温度升高缩短了寿命" | "上季度出库量激增推高了预测" |
| "振动异常是关键因素" | "维修频率上升表明需求增加" |

特征名映射为中文可读描述：
```python
FEATURE_DESCRIPTIONS = {
    "outbound_qty":   "月出库数量",
    "repair_count":   "月维修次数",
    "avg_unit_price": "采购均价",
    "month_sin":      "季节因素（周期项）",
    "month_cos":      "季节因素（相位项）",
    "working_days":   "工作日天数",
}
```
