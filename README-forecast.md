# 需求预测与安全库存控制模块使用指南 (README-forecast)

本项目已实现基于两阶段 XGBoost 预测、等渗概率校准与蒙特卡洛提前期需求仿真的智能备件管理决策体系（对应 M1 ~ M8 里程碑）。本文档说明如何触发计算、运行回测自检、以及系统中所有核心算法参数的取值清单，方便与论文进行校对。

---

## 1. 核心算法配置参数清单 (与论文对齐)

所有算法参数均集中在 [ForecastProperties.java](file:///Users/weiyaozhou/Documents/langdong/backend/src/main/java/com/langdong/spare/forecast/config/ForecastProperties.java) 中进行配置，并支持在 `application.yml` 中进行个性化覆盖。当前默认参数取值如下：

### 1.1 ABC×XYZ 分类加权得分参数 (第 3.2.2 节)
- **指标权重 (权重之和为 1.0)**：
  - **年消耗金额权重**：`forecast.classify.weightAnnualCost = 0.40`
  - **备件关键度权重**：`forecast.classify.weightCriticality = 0.25` (对应 `is_critical` 字段)
  - **采购提前期权重**：`forecast.classify.weightLeadTime = 0.20`
  - **供应替代难度权重**：`forecast.classify.weightReplaceDiff = 0.15`
- **ABC 分类帕累托阈值 (累计得分占比)**：
  - **A 类**：前 `70%` (`forecast.classify.abcCutoffA = 0.70`)
  - **B 类**：`70% ~ 90%` (`forecast.classify.abcCutoffB = 0.90`)
  - **C 类**：`90%` 之后 (默认)
- **XYZ 分类 CV² (需求变异系数平方) 阈值**：
  - **X 类**：`CV² < 0.5` (`forecast.classify.xyzCutoffX = 0.5`)
  - **Y 类**：`0.5 <= CV² < 1.0` (`forecast.classify.xyzCutoffY = 1.0`)
  - **Z 类**：`CV² >= 1.0` (默认，间断型/零散型需求)

### 1.2 蒙特卡洛模拟参数 (算法 3-2 & 4.3)
- **月工作天数 W**：`22` 天 (`forecast.monteCarlo.workingDays = 22`)
- **模拟采样次数 M**：`10000` 次 (`forecast.monteCarlo.simulations = 10000`)
- **随机数种子 (RNG Seed)**：固定为 **`20260518`** (`forecast.monteCarlo.seed = 20260518`)，确保全局 100% 可复现。
- **正态分布置信度常数**：`1.645` (`forecast.monteCarlo.intervalZ = 1.645`)，用于由 90% 双侧置信区间反推条件正需求标准差 $\sigma_t$。

### 1.3 服务水平映射参数 ($\alpha$ 分位数)
- **A 类** (高服务高保障)：`99%` 满足率 (`forecast.classify.serviceLevelA = 0.99`)
- **B 类** (中等保障)：`95%` 满足率 (`forecast.classify.serviceLevelB = 0.95`)
- **C 类** (普通保障)：`90%` 满足率 (`forecast.classify.serviceLevelC = 0.90`)

### 1.4 特征工程与重算优化 (第 4.4 节)
- **回看历史月数**：`36` 个月 (`forecast.historyMonths = 36`)
- **增量训练优化开关**：`true` (`forecast.incrementalEnabled = true`)，开启后，每月定时或手动重算时，若备件上月无任何新增领用消耗记录且已存在模型，将直接复用并拷贝上月已有的模型快照，避免重复调用 XGBoost 拟合，使增量计算时长压缩至 8~12 秒。
- **快照保存路径**：`target/models/` (`forecast.modelBaseDir = "target/models/"`)

---

## 2. 如何运行 EvaluationRunner (离线回测评估)

离线评估工具 **[EvaluationRunner.java](file:///Users/weiyaozhou/Documents/langdong/backend/src/test/java/com/langdong/spare/forecast/evaluation/EvaluationRunner.java)** 是一个全脱机独立测试组件。它不依赖外部运行中的 MySQL 进程，使用高保真合成数据进行测试，能够在各种环境下迅速启动并执行：

### 运行指令：
在终端执行以下 Maven 指令以单独运行该评估自检器：
```bash
cd backend
/Users/weiyaozhou/IdeaProjects/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -Dtest=EvaluationRunner
```

### 评估产出报告：
运行完成后，会生成结构化的 JSON 文件：`backend/target/evaluation_report.json`。
报告中包含两个协议的详细指标：
- **Protocol A (滚动回测 3.2.3)**：输出点预测（`wMAPE`、`MASE`）和概率预测（`BrierScore`、`CRPS`、`IntervalCoverageRate90`）五个指标，便于与论文表 3-4/3-5 逐格核对。
- **Protocol B (库存仿真 3.3.3)**：输出模拟 12 个月持仓中的 `StockoutMonths` (缺货月次)、`StockoutQuantity` (缺货量)、`FillRate` (服务水平满足率) 和 `AverageEndOfMonthInventory` (平均期末库存)，便于与论文表 3-12 核对。

---

## 3. 如何手动触发训练与重算

除了每月 1号凌晨自动定时触发外，系统也向前端提供了只读与手动触发重算的 REST 控制层接口：

### 3.1 手动触发全量预测流水线 (POST)
- **接口地址**：`POST /api/v1/forecast/trigger`
- **参数说明**：
  - 可选参数 `month` (格式 yyyy-MM)，默认取当前月份。
- **权限标识**：`ai:forecast:trigger`
- **运行方式**：
  - 调用后，后端将在独立的异步线程池 `forecastExecutor` 中拉起分类、训练、蒙特卡洛和落库任务，并立刻向前端返回 200 (Accepted: true)。

### 3.2 分页获取决策与阈值结果 (GET)
- **接口地址**：`GET /api/v1/forecast/result`
- **参数说明**：
  - `month` (yyyy-MM，可选，默认取最新计算月)
  - `partCode` (备件编码模糊过滤，可选)
  - `page` / `size` (分页参数)
- **权限标识**：`ai:forecast:list`
- **返回响应**：一次性输出**六标准字段**：
  - 发生概率 (`occurrenceProb`)
  - 条件正需求预测均值 (`positiveQty`)
  - 90% 置信区间下界 (`lowerBound`)
  - 90% 置信区间上界 (`upperBound`)
  - 提前期分位数 (`leadTimeQuantile`)
  - 安全库存 SS (`safetyStock`)
  - 补货点 ROP (`reorderPoint`)
- **手动触发历史趋势查询 (GET)**：`GET /api/v1/forecast/result/{partCode}`，权限 `ai:forecast:list`，返回单个备件的历史预测趋势。

---

## 4. XGBoost 超参数清单 (论文表 3-3)

配置集中在 [XGBoostProperties.java](file:///Users/weiyaozhou/Documents/langdong/backend/src/main/java/com/langdong/spare/forecast/config/XGBoostProperties.java)，可通过 `forecast.xgboost.*` 覆盖。共 **4 个 Booster**：1 个分类器（阶段一）+ 1 个点回归器 + 2 个分位数回归器（阶段二）。

| 超参 | 阶段一（`classifier`，binary:logistic） | 阶段二（`regressor`，reg:squarederror / reg:quantileerror） |
|---|---|---|
| num_round | 100 | 150 |
| max_depth | 4 | 5 |
| eta | 0.1 | 0.08 |
| min_child_weight | 3 | 2 |
| subsample | 0.8 | 0.8 |
| colsample_bytree | 0.8 | 0.8 |
| reg_alpha | 0 | 0.01 |
| reg_lambda | 1.0 | 1.0 |

- **随机种子**：`forecast.xgboost.seed = 42`（论文硬约束，保证训练可复现）。
- **分位数区间**：下界 τ `forecast.xgboost.quantileLower = 0.05`、上界 τ `quantileUpper = 0.95`（构造 90% 预测区间）。

---

## 5. 特征清单 (论文表 3-2，顺序固定)

由 [FeatureBuilder.java](file:///Users/weiyaozhou/Documents/langdong/backend/src/main/java/com/langdong/spare/forecast/feature/FeatureBuilder.java) 构造，严格防泄露（统计窗口截止预测月前一月，`FeatureBuilderLeakTest` 断言）。

**阶段一分类器（9 维）**：`lag_1`、`lag_3_mean`、`lag_3_std`、`zero_ratio_6`、`EquipHr`（上月设备运行时长）、`RepairCnt`（上月维修工单数）、`Month`、`ABC_code`、`XYZ_code`。
**阶段二回归器（+2 维，仅正需求子集）**：`pos_lag_1`（最近一次正需求消耗量）、`pos_lag_3_mean`（最近三次正需求消耗均值）。

- 新备件无历史记录时：预测任务**跳过并标注「数据不足」，不抛异常**（对应 TC-FC-04）。

---

## 6. 所有「⚠️待确认」参数当前取值（提示词点名，请逐条与论文核对）

> 以下为提示词标注「⚠️待确认」、当前先用默认值的项，集中列出供核对。

| ⚠️ 待确认项 | 当前取值 | 落点 |
|---|---|---|
| **设备关键度打分口径** | **关键=1、非关键=0**（二值） | `AbcXyzClassifier` 取 `is_critical` |
| **`Month` 特征编码方式** | **整数 1~12**（未启用 sin/cos 周期编码） | `FeatureBuilder` |
| **第一阶段概率校准方法** | 默认 **Isotonic Regression（保序回归）**；正样本极少/保序不稳定时回退 **Platt scaling** | `calibration/ProbabilityCalibrator` |
| ABC 帕累托阈值 | A ≤ 0.70；B ≤ 0.90；其余 C | `ForecastProperties.Classify` |
| XYZ CV² 阈值 | X<0.5；0.5≤Y<1.0；Z≥1.0 | 同上 |
| **ABC_code / XYZ_code**（论文表 3-2 写死） | A=3/B=2/C=1；X=1/Y=2/Z=3 | 无覆盖入口 |

- **Brier Score 对齐**：训练日志打印每次校准前后 Brier；论文报告两阶段平均 ≈ **0.15**，合成数据当前落在 **0.09–0.25**，需在真实 `spare_db` 上复核。

---

## 7. 真实业务库评估（待办）

当前 `EvaluationRunner` 使用合成/可控数据核对指标量级，**不连真实库**。要用真实 `spare_db` 评估：注入 `ForecastFeatureLoader` 从业务表（`biz_requisition_item` 等）读取真实月度消耗序列，替换 `EvaluationRunner#buildSyntheticSeries()`，再核对 wMAPE / MASE / Brier / CRPS / 90% 覆盖率是否落在论文表 3-4/3-5/3-7/3-12 量级。

---

## 8. 测试覆盖（JUnit 5，`forecast` 包 40 tests 全通过）

| 测试类 | 覆盖的提示词测试要求 |
|---|---|
| `FeatureBuilderLeakTest` | ① 防泄露 |
| `LeadTimeDemandSimulatorTest` | ② 蒙特卡洛可复现、④ 跨月/单月分支 |
| `TruncatedNormalSamplerTest` | ③ 截断正态（均值≈μ、min≥0、分位数） |
| `TwoStageNumericTest` | ⑤ D_hat = p×ŷ、L ≤ ŷ ≤ U |
| `StockThresholdServiceTest` / `ReplenishmentServiceTest` | ⑥ 新备件跳过、⑦ 服务水平映射、补货排序 |
| `ProbabilityCalibratorTest` / `IsotonicRegressionTest` | 模块 E 校准 |
| `XgbTrainerSmokeTest` | ⑧ 训练/保存/加载/推理冒烟 |
| `IncrementalSnapshotTest` | 增量快照优化 |
| `EvaluationRunner` | 模块 H 指标产出 |

> 运行全部 forecast 测试（Apple Silicon 需先 `export DYLD_LIBRARY_PATH=/opt/homebrew/opt/libomp/lib:$DYLD_LIBRARY_PATH`）：
> ```bash
> cd backend && /Users/weiyaozhou/IdeaProjects/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn test -Dtest='com.langdong.spare.forecast.**'
> ```
