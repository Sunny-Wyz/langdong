# Claude Code 施工提示词：备件需求两阶段概率预测 + 蒙特卡洛安全库存

> 用法：把「角色与总目标」到「第 0 步」先发给 Claude Code 让它读代码、对齐约定；确认无误后再按「实现顺序」逐模块推进。不要一次性让它写完全部，按里程碑走、每步跑测试。

---

## 角色与总目标

你是一名资深 Java + 机器学习工程师。我有一个**已经搭好骨架**的「酒企配套厂备件智能管理系统」（SpringBoot 3.2 + MyBatis + MySQL 8.0 后端，Vue 2 前端，前后端分离）。业务 CRUD 模块基本可用，但**核心算法完全未实现**。你的任务是把论文第三章的两套算法从零实现出来，并接入现有后端，形成「数据汇聚 → 两阶段预测 → 安全库存计算 → 补货触发」的按月自动闭环。

两套算法：
1. **两阶段概率预测模型**：第一阶段 XGBoost 分类器预测「需求是否发生」，第二阶段 XGBoost 回归器（点估计 + 两个分位数）预测「发生多少」，输出发生概率、点估计与 90% 预测区间。
2. **基于服务水平约束的安全库存计算模型**：以第一步的输出为参数，用蒙特卡洛模拟构造提前期累计需求分布，按服务水平分位数确定补货点 ROP 与安全库存 SS。

**严格约束：算法逻辑必须与本提示词给出的规格逐条一致（特征、超参、伪代码、随机种子、字段口径）。凡本提示词写死的数值/流程，不得自行更改；凡本提示词标注"⚠️待确认"的，先用给出的默认值并在代码注释与最终报告中显式列出，供我核对。**

---

## 技术栈与硬约束

- **JDK 17**，SpringBoot 3.2，Maven 依赖管理。
- **XGBoost4J 2.1.0**（Maven 坐标 `ml.dmlc:xgboost4j_2.12:2.1.0` 或对应无 Spark 版本；不要引入 xgboost4j-spark）。模型训练与推理**必须运行在 SpringBoot 同一 JVM 进程内**，不得起独立 Python 服务。
- **Apache Commons Math 3.6.1**（`org.apache.commons:commons-math3:3.6.1`），用于正态分位数、统计量。
- MyBatis + MySQL 8.0，字符集 UTF8MB4。
- 异步与调度：`@Async` + `ThreadPoolTaskExecutor`（与在线请求线程池物理隔离）+ `@Scheduled`（每月月初触发重算）。
- **随机种子必须固定且可复现**：XGBoost 训练种子 `42`，蒙特卡洛种子 `20260518`。所有随机源都要能从配置注入种子。
- 代码、注释、日志用中文业务术语时保持与论文一致（如"发生概率""补货点""提前期"）。

---

## 第 0 步：先读现有代码，再动手（不要跳过）

在写任何算法代码前，先探查并向我汇总以下内容，我确认后你再继续：

1. 现有包结构、命名规范（Controller/Service/Mapper 分层方式、DTO 命名习惯）。
2. 现有数据库表：重点看 `备件档案表 / 备件分类结果表 / 领用记录 / 维修工单 / 设备档案 / 采购记录` 对应的实体类与建表 SQL。论文里的字段参考：
   - 备件档案表：`code(varchar, 8位统一编码)`、`price`、`is_critical`、`replace_diff(供应替代难度1~5)`、`lead_time(采购提前期,天)`、`category_id` 等。
   - 备件分类结果表：`part_code`、`classify_month(yyyy-MM)`、`abc_class(A/B/C)`、`xyz_class(X/Y/Z)`、`composite_score`、`annual_cost`、`cv2`、`safety_stock`、`reorder_point`、`service_level`、`strategy_code`。
3. 现有是否已有：预测结果表、补货建议表、model_version 表？没有就由你补建（给出建表 SQL）。
4. 现有线程池 / 定时任务配置。

**汇总完这些后，先不要写算法，等我回复"继续"。**

---

## 目标包结构（在现有工程下新增，按现有命名微调）

```
com.<现有根包>.forecast
├── model/          # DTO：FeatureVector, TrainingSample, ForecastResult, SafetyStockResult
├── feature/        # FeatureBuilder：从业务表汇聚特征
├── xgboost/        # XGBoost4J 封装：分类器、点回归器、分位数回归器
├── stage/          # DemandOccurrenceStage(阶段一), DemandQuantityStage(阶段二)
├── calibration/    # ProbabilityCalibrator：第一阶段概率校准
├── montecarlo/     # LeadTimeDemandSimulator, TruncatedNormalSampler
├── classify/       # AbcXyzClassifier（ABC×XYZ 分类，产出分类特征）
├── service/        # PredictionService, StockThresholdService, ReplenishmentService
├── scheduler/      # MonthlyForecastScheduler
├── mapper/         # MyBatis Mapper + XML
├── config/         # ForecastThreadPoolConfig, XGBoostProperties
└── controller/     # 供前端查询预测结果/补货建议的只读接口
```

---

## 模块 A：ABC×XYZ 分类（特征前置，先做）

论文 4.2.2 / 4.1.3.2 规定：

**ABC 维度**——加权综合评分法，四个维度加权求和后分档：
- 年消耗金额 权重 **0.40**
- 设备关键度 权重 **0.25**（用 `is_critical` 或设备关键度打分，⚠️待确认打分口径，默认关键=1、非关键=0，或按关联设备关键度归一化）
- 采购提前期 权重 **0.20**（`lead_time`，越长分越高）
- 供应替代难度 权重 **0.15**（`replace_diff` 1~5）
- 各维度先**归一化到 [0,1]**（min-max）再加权，得 `composite_score`。

**XYZ 维度**——按需求变异系数 **CV²** 分档（CV² = 方差 / 均值²，基于历史月度消耗；仅统计到分类月份前一个月）。

**⚠️待确认（先用默认阈值，代码里做成可配置常量并注释醒目）**：
- ABC 分档：`composite_score` 降序，累计占比前 70% 为 A、70%~90% 为 B、其余 C（帕累托法）。
- XYZ 分档：`CV² < 0.5` → X（稳定）；`0.5 ≤ CV² < 1.0` → Y（中等波动）；`CV² ≥ 1.0` → Z（高波动）。
- 编码：`ABC_code`：A=3, B=2, C=1；`XYZ_code`：X=1, Y=2, Z=3（此编码论文表 3-2 已写死，**不得改**）。

产出：写入「备件分类结果表」，按 `classify_month` 存档；同时对外提供 `getAbcXyzCode(partCode, month)` 供特征构造调用。

---

## 模块 B：特征构造 FeatureBuilder（严格防泄露）

**铁律：所有特征只能用「预测时点之前已发生或可提前确定」的数据，统计窗口严格截止于预测月份的前一个月。任何未来信息泄露都视为严重 bug。**

对给定 `(partCode, targetMonth)` 构造特征向量。

**阶段一分类器特征（9 维，表 3-2，顺序固定）：**

| # | 特征名 | 含义 | 计算 |
|---|--------|------|------|
| 1 | `lag_1` | 近1个月消耗量 | targetMonth 前1月的总消耗 |
| 2 | `lag_3_mean` | 近3个月消耗均值 | 前3个月消耗均值 |
| 3 | `lag_3_std` | 近3个月消耗标准差 | 前3个月消耗样本标准差 |
| 4 | `zero_ratio_6` | 近6个月零需求月份占比 | 前6个月中消耗为0的月份数/6 |
| 5 | `EquipHr` | 上月设备运行时长 | 关联设备上月运行小时数汇总 |
| 6 | `RepairCnt` | 上月维修工单数 | 该备件关联设备上月维修工单数 |
| 7 | `Month` | 月份季节性编码 | targetMonth 的月份(1~12)，⚠️默认用整数；可选 sin/cos 编码，先用整数 |
| 8 | `ABC_code` | ABC 等级编码 | A=3,B=2,C=1（取 targetMonth 前一月的分类结果） |
| 9 | `XYZ_code` | XYZ 等级编码 | X=1,Y=2,Z=3 |

**阶段二回归器特征（11 维 = 上述 9 维 + 2 维，仅在正需求子集上训练时使用）：**

| # | 特征名 | 含义 | 计算 |
|---|--------|------|------|
| 10 | `pos_lag_1` | 最近一次正需求的消耗量 | 往前找到最近一个消耗>0的月份的消耗量 |
| 11 | `pos_lag_3_mean` | 最近三次正需求的消耗均值 | 最近3次正需求月份的消耗均值 |

- 月度消耗量来源：领用记录 + 维修工单换件明细，按月按备件汇总（先确认现有表怎么记消耗，以领用/换件明细为准）。
- 缺数据处理：历史不足以算某特征时（如新备件），用 0 或均值填充并打标记；**新备件无历史记录时，预测任务应跳过并标注"数据不足"，不得报错**（对应测试用例 TC-FC-04）。
- FeatureBuilder 要能一次性构造「训练矩阵（历史所有月）」和「单个目标月推理向量」两种输出。

---

## 模块 C：XGBoost4J 封装

封装三类 Booster（注意：**共 4 个 Booster** —— 1 个分类器 + 1 个点回归器 + 2 个分位数回归器）：

1. **分类器** `objective = "binary:logistic"`，输出发生概率 `pt ∈ [0,1]`。
2. **点回归器** `objective = "reg:squarederror"`，输出正需求量点估计 `ŷ`。
3. **分位数回归器 ×2**：τ=0.05（下界）与 τ=0.95（上界），构造 90% 预测区间。
   - XGBoost 2.x 原生支持分位数：`objective = "reg:quantileerror"` + 参数 `quantile_alpha`（0.05 / 0.95）。**优先用原生 objective**；若 XGBoost4J 2.1.0 的 Java 绑定不能干净传入 `quantile_alpha`，则退化为自定义 pinball loss 的 obj/eval 回调实现，并在注释中说明所选方案。

**超参数（表 3-3，写死为默认配置，可通过 config 覆盖）：**

| 超参 | 阶段一(分类) | 阶段二(回归/分位数) |
|------|-------------|-------------------|
| n_estimators (num_round) | 100 | 150 |
| max_depth | 4 | 5 |
| learning_rate (eta) | 0.1 | 0.08 |
| min_child_weight | 3 | 2 |
| subsample | 0.8 | 0.8 |
| colsample_bytree | 0.8 | 0.8 |
| reg_alpha | 0 | 0.01 |
| reg_lambda | 1.0 | 1.0 |
| seed | 42 | 42 |

- 提供 `train(DMatrix)` / `predict(features)` / `saveModel(path)` / `loadModel(path)`。
- 用 `DMatrix` 承载 `float[][]` 特征与 `float[]` 标签。
- 每个 Booster 训练完保存快照（模型文件 + 特征列顺序 + 训练截止月），供推理和回滚复用。

---

## 模块 D：两阶段模型编排（算法 3-1）

`DemandOccurrenceStage`（阶段一）：
1. 由历史需求序列构造二分类标签：`I_t = 1 if D_t > 0 else 0`。
2. 用全量历史 (X, I) 训练分类器。

`DemandQuantityStage`（阶段二）：
3. 筛选正需求子集 `S_pos = { t | D_t > 0 }`。
4. 在 `S_pos` 上扩充特征（加 `pos_lag_1`、`pos_lag_3_mean`），用 (X[S_pos], D[S_pos]) 训练点回归器 + 两个分位数回归器。

`PredictionService.forecast(partCode, targetMonth)`（推理，行 8-16）：
5. 构造目标月特征向量 `x*`。
6. `p_t* = 分类器.predictProba(x*)`（经校准，见模块 E）。
7. `ŷ = 点回归器.predict(x*)`。
8. `L_t* = 分位数0.05.predict(x*)`，`U_t* = 分位数0.95.predict(x*)`。
9. **总需求点估计 `D_hat = p_t* × ŷ`**。
10. 返回六个标准字段：`发生概率 p、正需求量点估计 ŷ、区间下界 L、区间上界 U、总需求点估计 D_hat、（后续填充）提前期分位数/ROP/SS`。

**滚动训练纪律**：训练样本严格按时间顺序组织；做滚动/回测时，每完成一个月才把该月实际值并入训练集再预测下一月，杜绝未来信息泄露。

---

## 模块 E：第一阶段概率校准 ProbabilityCalibrator

论文 3.2.2(4) 要求对第一阶段输出概率做校准（缓解树模型在小样本下概率偏向极端）。
- ⚠️待确认：论文未指定方法。**默认用 Isotonic Regression（保序回归）**，在时间序列嵌套 CV 的验证折上拟合校准映射；若正样本极少导致保序不稳定，回退到 Platt scaling（逻辑回归）。
- 校准器要能保存/加载，随模型版本一起管理。
- 记录校准前后的 Brier Score 便于我核对（论文报告两阶段平均 Brier Score = 0.15）。

---

## 模块 F：蒙特卡洛安全库存（算法 3-2）

**输入**：`p_t`（发生概率）、`μ_t = ŷ`（正需求量均值）、`L_t*`（下界）、`U_t*`（上界）、`L`（采购提前期，天，取自备件档案 lead_time）、`W`（月工作天数，默认 22）、`M`（模拟次数，默认 10000）、`α`（服务水平：A类0.99 / B类0.95 / C类0.90，按 ABC 分类取）。

**输出**：`ROP`（补货点）、`SS`（安全库存）。

**流程（严格按伪代码，注意可复现）：**
```
σ_t = (U_t* − L_t*) / (2 × 1.645)   # 由90%区间反推条件标准差；放在循环外只算一次
rng = 固定种子 20260518 的随机源
samples = []
for m in 1..M:
    s = UniformInt(1, W)             # 触发日
    e = s + L − 1                    # 结束日
    if e ≤ W:                        # 提前期落在单月内
        I1 = Bernoulli(p_t)
        Y1 = TruncatedNormal(mean=μ_t, sd=σ_t, lower=0)
        Dmonth1 = I1 × Y1
        DL = (L / W) × Dmonth1
    else:                            # 跨两月，逐月独立采样
        I1 = Bernoulli(p_t); Y1 = TruncatedNormal(μ_t, σ_t, 0)
        I2 = Bernoulli(p_t); Y2 = TruncatedNormal(μ_t, σ_t, 0)
        Dmonth1 = I1 × Y1;  Dmonth2 = I2 × Y2
        d1 = W − s + 1;  d2 = L − d1
        DL = (d1 / W) × Dmonth1 + (d2 / W) × Dmonth2
    samples.append(DL)
ROP = ceil( Quantile(samples, α) )
SS  = ROP − ceil( Mean(samples) )
return (ROP, SS)
```

**截断正态采样 `TruncatedNormalSampler`（用 Commons Math 3.6.1）：**
- 用逆变换法：设标准化下界 `a = (0 − μ)/σ`，`Φ` 为标准正态 CDF（`NormalDistribution.cumulativeProbability`），`Φ⁻¹` 为其反函数（`inverseCumulativeProbability`）。
  采样 `u ~ Uniform(0,1)`，令 `p = Φ(a) + u·(1 − Φ(a))`，则 `x = μ + σ·Φ⁻¹(p)`，`x ≥ 0`。
- σ 为 0 或极小时（区间退化）做保护：直接返回 `max(0, μ)`。
- Bernoulli、Uniform、逆变换共用同一个种子化 RNG，保证整体可复现。

**边界/健壮性**：`L ≤ 0`、`W ≤ 0`、`p_t` 越界都要校验；`L > W` 也应能处理（当前论文场景 L=14、W=22，跨月概率约 59%，但代码不要假设一定不超过一个月）。

---

## 模块 G：服务编排、调度与落库

`PredictionService`：统一编排 4 个 Booster 的训练与推理（论文 4.3.6 的 orchestration 角色）。

`StockThresholdService`：对每个备件调用蒙特卡洛，批量算 SS 与 ROP，写回「备件分类结果表 / 预测结果表」的 `safety_stock`、`reorder_point`、`service_level` 字段，整个批处理放在**事务**里（读特征 + 写预测结果 + 写 SS/ROP 一致提交）。

`ReplenishmentService`：比对「可用库存 vs ROP」，对低于 ROP 的备件写入「补货建议表」，含建议采购量，按紧急程度排序（对应测试 TC-PO-01）。

`MonthlyForecastScheduler`：
- `@Scheduled` 每月月初业务空闲时段触发全量重算（先跑 ABC×XYZ 分类 → 再两阶段预测 → 再安全库存 → 再补货建议）。
- 训练走 `@Async` + 独立线程池，与在线请求隔离。
- **model_version 表**：每次重训记录版本、训练截止月、指标快照、模型文件路径，支持回滚。
- **增量快照优化（论文 4.4.3 提到）**：月度触发时仅对「本月有新增消耗记录」的备件重训，存量无变化的复用上月快照，把增量月度训练耗时压到约 8~12 秒。先实现全量版本，再把增量优化做成开关。

`ForecastController`（只读）：供前端查询预测结果、区间、补货建议。返回六标准字段：`需求发生概率、正需求量预测均值、区间上下界、提前期分位数、安全库存、补货点`。

---

## 模块 H：评估与自检（帮助我和论文数据对齐）

实现一个可离线运行的 `EvaluationRunner`（可做成测试或 CLI），在给定历史数据上产出论文中的核心指标，便于我核对数值是否落在论文量级：

- **点预测**：`wMAPE = Σ|ŷ_t − y_t| / Σ|y_t|`（标准定义，不要加"伯努利加权"变体）；`MASE`（以朴素季节性预测为参照）。
- **概率预测**：`Brier Score`（第一阶段二分类校准精度）；`CRPS`（对最终总需求分布，用蒙特卡洛样本估计）；`90% 预测区间经验覆盖率`（仅在正需求月份统计）。
- 支持两种切分协议：
  - 3.2.3 的「前 30 月训练 / 后 6 月滚动」（评估预测精度）；
  - 3.3.3 的「12 月训练 / 12 月 holdout」（评估库存绩效：缺货月次、缺货量、需求满足率 fill rate、平均月末库存）。
- 输出为结构化 JSON/CSV，字段命名与论文表 3-4/3-5/3-7/3-12 对齐，方便我逐格核对。

---

## 测试要求

用 JUnit 5 覆盖：
1. **防泄露单测**：构造已知序列，断言 FeatureBuilder 在预测第 t 月时绝不读取 ≥ t 月的数据。
2. **蒙特卡洛可复现单测**：固定种子 20260518 跑两次，`samples`、`ROP`、`SS` 完全一致。
3. **截断正态单测**：大样本下均值≈μ（截断修正后）、最小值 ≥ 0、经验分位数与理论接近。
4. **跨月/单月分支单测**：L=14、W=22 时跨月比例落在 ~59% 附近；L<W-s 时走单月分支。
5. **两阶段数值单测**：给定 p 与 ŷ，断言 `D_hat = p × ŷ`；区间满足 `L ≤ ŷ ≤ U`（正常情况下）。
6. **新备件跳过单测**：无历史记录时预测任务跳过并标注"数据不足"，不抛异常（TC-FC-04）。
7. **服务水平映射单测**：A→0.99、B→0.95、C→0.90。
8. XGBoost 封装的训练/保存/加载/推理冒烟测试（可用小规模合成数据，避免测试依赖真实业务库）。

---

## 实现顺序（里程碑，逐步交付、每步跑测试后停下等我确认）

1. **M0**：读现有代码，输出结构与表结构汇总（第 0 步）。等我确认。
2. **M1**：pom 依赖（XGBoost4J 2.1.0 + commons-math3 3.6.1）、config、model DTO、线程池。
3. **M2**：模块 A（ABC×XYZ 分类）+ 模块 B（FeatureBuilder）+ 防泄露单测。
4. **M3**：模块 C（XGBoost 封装）+ 冒烟测试；确认分位数 objective 走通。
5. **M4**：模块 D（两阶段编排）+ 模块 E（校准）+ 两阶段数值单测。
6. **M5**：模块 F（蒙特卡洛 + 截断正态）+ 可复现单测。
7. **M6**：模块 G（StockThreshold/Replenishment/Scheduler/model_version + 落库事务）。
8. **M7**：模块 H（EvaluationRunner）+ ForecastController + 端到端跑一遍。
9. **M8**：增量快照优化 + 补建/校对所有相关建表 SQL。

---

## 交付物

- 全部源码（按上面包结构）。
- 新增/变更的建表 SQL（预测结果表、补货建议表、model_version 表等）。
- 一份 `README-forecast.md`：说明如何触发训练、如何跑 EvaluationRunner、所有 ⚠️待确认参数的当前取值清单（ABC/XYZ 阈值、Month 编码方式、校准方法、设备关键度打分口径等），方便我逐条核对是否与论文一致。

## 你现在要做的第一件事

只做 **M0**：探查现有工程与数据库，向我汇总结构，并列出你打算新建/修改的表。**不要开始写算法代码，等我回复"继续"。**
