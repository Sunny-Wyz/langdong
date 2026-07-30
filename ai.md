# AI智能分析模块算法实现详解

本文基于当前仓库已落地代码，完整解释 AI 智能分析模块从数据输入、算法分型、预测生成、评估量化到库存联动决策的实现过程。文中所有算法均对应真实代码实现，不以未落地方案作为主线。

## 1. 模块总览与执行流水线

AI 分析流程由 `AiForecastService` 串联，核心路径为"特征工程 -> 分型预测 -> 结果入库 -> 阈值联动"。

代码映射：
- `backend/src/main/java/com/langdong/spare/service/ai/AiForecastService.java`

实现过程：
- 每月 1 日凌晨 2 点触发定时任务（`@Scheduled(cron = "0 0 2 1 * ?")`）。
- 生成目标月份 `forecastMonth = now + 1 month`。
- 调用 `AiFeatureService.buildAllContexts()` 批量构造每个备件的预测上下文。
- 按 `algoType` 路由到 `SbaForecastServiceImpl` 或 `RandomForestServiceImpl`，不满足条件时使用 `FALLBACK`。
- 批量写入 `ai_forecast_result`。
- 调用 `StockThresholdService.recalcAndPush()` 做 SS/ROP 与补货建议联动。

业务含义：
- 该设计将预测计算与库存决策放进同一条批处理链路，减少预测结果"悬空"而不落地决策的问题。

局限性：
- 当前流程是离线月度批处理，不是实时在线学习与在线推理。

## 2. 输入数据与特征构造

算法输入载体是 `PredictContextDTO`，其中 `demandHistory` 是近 N 个月需求时间序列（当前实现按 12 个月，缺失月补 0）。

代码映射：
- `backend/src/main/java/com/langdong/spare/dto/PredictContextDTO.java`
- `backend/src/main/java/com/langdong/spare/service/ai/AiFeatureService.java`

数学形式：
- 对每个备件构造离散序列
  \[
  D = \{d_1, d_2, \ldots, d_{12}\}, \quad d_t \in \mathbb{Z}_{\ge 0}
  \]
- 缺失月份按 `d_t = 0` 处理，保证时序长度对齐。

实现过程：
- 批量查询全部备件主数据。
- 一次 SQL 拉取近 12 个月消耗汇总。
- 按 `partCode` 聚合后，对月份索引补全 0 值。
- 写入 `PredictContextDTO`：`partCode`、`leadTime`、`demandHistory`、`adi`、`cv2`、`algoType` 等字段。

业务含义：
- 补零能保留"未发生需求"这一信息，对间断需求判别非常关键。

局限性：
- `deviceFeatures` 字段已预留，但当前实现置空，尚未进入模型特征。

## 3. 分型算法：ADI/CV² 与算法路由

系统用 Syntetos-Boylan 判别思想决定备件走 SBA 还是 RF。

代码映射：
- `backend/src/main/java/com/langdong/spare/service/ai/AiFeatureService.java`

数学形式：
- 平均需求间隔（ADI）：
  \[
  ADI = \frac{T}{N_+}
  \]
  其中，`T` 为总期数，`N_+` 为非零需求期数。
- 需求变异系数平方（CV²）：
  \[
  CV^2 = \left(\frac{\sigma}{\mu}\right)^2
  \]
  其中 `\mu`、`\sigma` 在非零需求子样本上计算。

阈值与路由规则：
- 若 `nonZeroCount < 3`，路由 `FALLBACK`。
- 否则若 `ADI > 1.32` 且 `CV² > 0.49`，路由 `SBA`。
- 否则路由 `RF`。

业务含义：
- `ADI` 刻画"多久才发生一次需求"。
- `CV²` 刻画"非零需求大小波动性"。
- 高 ADI 且高 CV² 常见于间断且不稳定需求，适合 SBA 类方法。

局限性：
- 阈值是固定经验值，未做数据集自适应优化。

## 4. SBA 预测算法实现

SBA（Syntetos-Boylan Approximation）用于间断需求，分别平滑"需求大小"和"需求间隔"。

代码映射：
- `backend/src/main/java/com/langdong/spare/service/ai/SbaForecastServiceImpl.java`

数学形式：
- 设 `z_t` 为需求大小平滑量，`p_t` 为需求间隔平滑量，`q_t` 为当前间隔计数。
- 预测式：
  \[
  \hat{y}_{t+1} = \left(1 - \frac{\beta}{2}\right)\frac{z_t}{p_t}
  \]
- 更新式（若当期需求 `d_t > 0`）：
  \[
  z_t = \alpha d_t + (1-\alpha)z_{t-1}
  \]
  \[
  p_t = \beta q_t + (1-\beta)p_{t-1}
  \]
- 当前实现参数：`\alpha = 0.15`，`\beta = 0.10`。

实现过程：
- 用首个非零需求初始化 `zMean`，`pMean` 初值为 1。
- 遍历历史序列逐期预测并更新状态。
- 取遍历完成后的状态做下一期预测。
- 为避免 SBA 前期状态不稳定，MASE 评估跳过前 2 期热身样本。

业务含义：
- SBA 不把零需求当作"低需求数值"，而是通过间隔建模解释零值，适合备件低频消耗场景。

局限性：
- 平滑参数固定，未做自动调参。

## 5. 随机森林预测算法实现

随机森林用于相对规律型需求，当前采用短阶滞后特征与滚动均值特征。

代码映射：
- `backend/src/main/java/com/langdong/spare/service/ai/RandomForestServiceImpl.java`
- 依赖：`smile-core 3.1.0`

数学形式：
- 特征构造：
  \[
  x_t = [d_{t-1}, d_{t-2}, \frac{d_{t-1}+d_{t-2}+d_{t-3}}{3}]
  \]
- 目标：
  \[
  y_t = d_t
  \]
- 模型输出：
  \[
  \hat{y}_{t+1} = f_{RF}(x_{t+1})
  \]

实现过程：
- 用窗口 `window=3` 构造监督样本，样本数为 `n-3`。
- 将 `lag1`、`lag2`、`roll3`、`y` 组装为 Smile `DataFrame`。
- 调用 `RandomForest.fit(formula, trainData)` 训练。
- 用最新三期值构造下一期特征，执行 `predict`。
- 结果执行 `max(0, prediction)`，避免出现负需求。

业务含义：
- 在有限历史长度下，滞后特征可快速捕捉短期依赖与局部趋势。

局限性：
- 当前特征维度较少，未引入节假日、设备工况、季节性等外生变量。

## 6. Fallback 回退策略

当数据不足或模型异常时，系统退化到保守的统计启发式预测。

代码映射：
- `backend/src/main/java/com/langdong/spare/service/ai/AbstractForecastAlgorithm.java`
- 触发来源：`AiFeatureService`、`RandomForestServiceImpl`、`SbaForecastServiceImpl`

数学形式：
- 预测值取非零历史需求均值：
  \[
  \hat{y} = mean(\{d_t \mid d_t > 0\})
  \]
- 预测区间：
  \[
  [0.5\hat{y},\ 1.5\hat{y}]
  \]

触发条件：
- 非零样本不足 `MIN_DATA_POINTS=3`。
- RF/SBA 执行异常。

业务含义：
- 通过回退机制保证批处理"可产出"，避免单个备件失败拖垮全量任务。

局限性：
- 回退区间为固定比例，不随历史波动自适应。

## 7. 评估指标：MASE

系统统一使用 MASE（Mean Absolute Scaled Error）评估预测质量。

代码映射：
- `backend/src/main/java/com/langdong/spare/service/ai/AbstractForecastAlgorithm.java`

数学形式：
- 模型 MAE：
  \[
  MAE_{model} = \frac{1}{n}\sum_{t=1}^{n}|y_t - \hat{y}_t|
  \]
- 朴素基线 MAE（lag-1）：
  \[
  MAE_{naive} = \frac{1}{n-1}\sum_{t=2}^{n}|y_t - y_{t-1}|
  \]
- MASE：
  \[
  MASE = \frac{MAE_{model}}{MAE_{naive}}
  \]

实现细节：
- 若历史过短或 `MAE_naive = 0`，返回 `null`（避免除零和误导性指标）。
- 保留 4 位小数。

业务含义：
- MASE 通过与朴素预测对比，能跨不同量纲序列进行可比评估。

局限性：
- 当序列极度平稳时，朴素基线误差趋近 0，MASE 不稳定。

## 8. 预测区间与不确定性处理

当前系统统一输出 90% 置信区间（`lowerBound` / `upperBound`），但两类算法的区间估计方式不同。

代码映射：
- `SbaForecastServiceImpl`（泊松近似）
- `RandomForestServiceImpl`（RMSE 近似）
- `sql/ai_module.sql`（区间字段落库）

SBA 区间：
- 近似假设：泊松分布满足 `Var(Y) \approx E(Y)`。
- 标准差估计：
  \[
  \sigma \approx \sqrt{\hat{y}}
  \]
- 90% 区间（`z=1.645`）：
  \[
  [\max(0,\hat{y}-1.645\sigma),\ \hat{y}+1.645\sigma]
  \]

RF 区间：
- 用训练残差估计不确定性：
  \[
  RMSE = \sqrt{\frac{1}{n}\sum (y_t-\hat{y}_t)^2}
  \]
- 90% 区间：
  \[
  [\max(0,\hat{y}-1.645\cdot RMSE),\ \hat{y}+1.645\cdot RMSE]
  \]

业务含义：
- 区间可直接作为库存安全策略的波动输入，而非只依赖单点预测。

局限性：
- RF 区间未使用树间分布或分位数回归，属于工程近似。

## 9. 库存联动决策：SS 与 ROP

预测结果进入库存策略层后，转换为安全库存与补货触发点。

代码映射：
- `backend/src/main/java/com/langdong/spare/service/ai/StockThresholdService.java`

数学形式：
- 日均需求估计：
  \[
  \bar{d} = \frac{\hat{Q}_{month}}{30}
  \]
- 由区间反推日需求标准差：
  \[
  \sigma_d \approx \frac{upper-lower}{2\times1.645}
  \]
- 安全库存：
  \[
  SS = k\cdot\sigma_d\cdot\sqrt{L}
  \]
- 补货触发点：
  \[
  ROP = \bar{d}\cdot L + SS
  \]

服务水平系数 `k`：
- A 类：2.33
- B 类：1.65
- C 类：1.28

实现过程：
- 从 `PredictContextDTO` 读取 `abcClass` 与 `leadTime`。
- 计算 `SS` 与 `ROP` 并向上取整。
- 若 `currentStock <= ROP`，生成补货建议对象。

业务含义：
- 将"预测不确定性"显式映射为库存缓冲，从经验备货转向量化备货。

局限性：
- 当前 `currentStock` 为示例常量（10），真实库存查询与建议持久化仍待完善。

## 10. 工程边界、已知限制与改进方向

已知边界：
- 设备特征已在 DTO 与表结构预留，但当前未进入预测模型。
- RF 特征为短阶时序统计量，未引入外生因素。
- 区间估计为简化近似，不是严格概率校准。
- 库存联动中当前库存读取与建议写库逻辑未完整打通。

改进方向：
- 引入设备运行时长、故障频次等外生变量，扩展 `deviceFeatures` 建模。
- 对 SBA 参数 `\alpha, \beta` 做自动寻优。
- 对 RF 使用分位数回归森林或 Bootstrap 估计更稳健区间。
- 打通实时库存读取与补货建议入库，实现闭环自动化。

说明：
- 论文中提及但当前后端未落地的算法（如 Transformer、Isolation Forest）不纳入本实现主线。

## 11. 关键源码索引

核心调度：
- `backend/src/main/java/com/langdong/spare/service/ai/AiForecastService.java`

特征与分型：
- `backend/src/main/java/com/langdong/spare/service/ai/AiFeatureService.java`
- `backend/src/main/java/com/langdong/spare/dto/PredictContextDTO.java`

预测算法：
- `backend/src/main/java/com/langdong/spare/service/ai/SbaForecastServiceImpl.java`
- `backend/src/main/java/com/langdong/spare/service/ai/RandomForestServiceImpl.java`
- `backend/src/main/java/com/langdong/spare/service/ai/AbstractForecastAlgorithm.java`

库存联动：
- `backend/src/main/java/com/langdong/spare/service/ai/StockThresholdService.java`

结果存储：
- `backend/src/main/java/com/langdong/spare/entity/AiForecastResult.java`
- `sql/ai_module.sql`

---

本文档版本：`v1.0`（与当前 `MODEL_VERSION` 对齐）
