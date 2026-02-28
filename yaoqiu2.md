# AI 智能分析模块（M7）— Claude Code 提示词

你是一位 Java/Vue3 全栈工程师及熟悉机器学习算法的开发者。请基于以下需求规格，为备件管理系统实现「AI 智能分析模块（M7）」。

## 技术栈

- 后端核心：Spring Boot 3.x + MyBatis-Plus + MySQL 5.7
- 算法引擎：可使用 Java 本地的 Weka、Smile 库，或通过 Python 脚本（scikit-learn 等）被 Java 调用
- 前端：Vue 3 + Element Plus + ECharts 5
- 定时任务：Spring Task 或 Quartz (用于每月自动运行预测)

---

## 数据库表结构（MySQL 5.7 兼容）

### 1. ai_forecast_result（需求预测结果表）

| 字段名 | 类型 | 说明 |
|---|---|---|
| 🔑 id | bigint(19) | 预测记录ID，主键 |
| 🔗 part_code | varchar(20) | 备件编码，关联 biz_part |
| forecast_month | varchar(7) | 预测目标月份（yyyy-MM） |
| predict_qty | decimal(8,2) | 预测消耗量（件） |
| lower_bound | decimal(8,2) | 90%置信区间下界 |
| upper_bound | decimal(8,2) | 90%置信区间上界 |
| algo_type | varchar(20) | 预测算法：RF (随机森林) / SBA |
| mase | decimal(6,4) | MASE评估指标 |
| model_version | varchar(20) | 模型版本号 |
| create_time | datetime | 预测计算时间 |

### 2. ai_device_feature（设备特征记录表）

| 字段名 | 类型 | 说明 |
|---|---|---|
| 🔑 id | bigint(19) | 特征记录ID，主键 |
| 🔗 device_id | bigint(19) | 设备ID，关联 biz_device |
| stat_month | varchar(7) | 统计月份（yyyy-MM） |
| run_hours | decimal(8,1) | 月运行时长（小时） |
| fault_count | int(5) | 当月故障次数 |
| work_order_count | int(5) | 当月工单数 |
| part_replace_qty | int(5) | 当月换件总数量 |

---

## 业务功能要求（5个关键子模块）

### 子模块1：需求类型判断（算法分型分类器）
- **触发条件**：每月定时任务执行前，为系统内所有备件分配预测模型。
- **核心逻辑**：基于 Syntetos-Boylan 分类矩阵计算：
  - ADI（平均需求间隔）
  - CV²（需求量变异系数的平方）
- **分类规则**：若 ADI > 1.32 且 CV² > 0.49，判定为**间断型需求 (SBA 模型)**；其他均判定为**规律型需求 (随机森林 模型)**。

### 子模块2：需求预测引擎（核心算法）
- **功能目标**：对下一个月备件消耗量进行预测，并输出 90% 置信区间的上下界。
- **模型一：随机森林 (RF)**：
  - 适用：规律型需求备件
  - 参数建议：`n_estimators=100`, `max_depth=5`（可通过 GridSearchCV 调优）
  - 输入特征：滞后1期销量 (`lag_1`)、3期滚动均值 (`roll_mean_3`)、关联设备的运行时长 (`run_hours`)、当月工单数 (`work_order_count`)、季节编码等
- **模型二：改进版 Croston / SBA**：
  - 适用：间断型需求备件（发生频次低、单次数量不固定）
- **性能指标计算**：系统需自动计算 MASE（平均绝对比例误差），并在低于某阈值（如精度低于基线 0.98 时）发送警告提示。预测结果需写入 `ai_forecast_result`。

### 子模块3：安全库存计算
- **触发条件**：每月获取新预测值后自动计算。
- **计算逻辑**（需从 M2 智能分类模块获取当前 ABC 分类）：
  - `SS (安全库存) = k × σ_d × √L`
  - `ROP (补货触发点) = d̄ × L + SS` 
  - *说明*：`L`为采购提前期(lead_time)，`d̄`为日均预测消耗量，`σ_d`为标准差。
   - 分类参数 `k`：A类业务 k=2.33，B类 k=1.65，C类 k=1.28。
- **输出**：更新对应备件的库存预警阈值，并推送给 **M3仓储管理** 模块。

### 子模块4：智能补货建议生成
- **核心逻辑**：当实时库存(或可用库存) ≤ 刚刚计算好的 ROP 时，自动生成补货建议记录 (`biz_reorder_suggest`)。
- **建议量设计**：建议采购量 = 本月度 AI 预测值。
- **流转动作**：生成的补货建议附带 90% 置信区间数据，推送到 **M6采购管理** 模块供采购员参考/执行。

### 子模块5：预测性维护预警
- **数据来源**：关联设备当前运行特征及维修历史。
- **核心逻辑**：基于备件历史更换周期和设备平均故障间隔时间（MTBF），识别潜在失效风险。
- **预警推送**：需在备件失效发生前 `L` 天（采购提前期）触发预警，并写入预警消息池（推送至 **预警任务中心**）。

---

## 模块联动与相互依赖说明

1. **上游输入依赖**：
   - 依赖 **M3仓储管理** 的出入库流水，生成备件消耗历史的时间序列。
   - 依赖 **M5维修工单** 完工数据，抽取设备失效特征（更新 `ai_device_feature` 表）。
2. **下游数据供给**：
   - 为 **M2智能分类** 提供各备件预测消耗量用于 ABC 分类重算。
   - 为 **M6采购模块** 自动生成含建议采购量的补货建议列表。
   - 为 **M8报表看板** 提供模型预测精度(MASE)和需求趋势预测数据供大屏展示。

---

## 权限与数据隔离

- 预测算法触发：通常由后台定时器触发。也可提供一个供管理员手动触发“模型重训与重算”的接口。
- **限制要求**：API 需要通过 `@PreAuthorize("hasRole('ROLE_ADMIN')")` 控制权限层。

---

## 后端代码结构要求 (建议)

```
com.yourproject.ai
  ├── controller/
  │   └── AiForecastController.java (提供手动触发、查询预测结果接口)
  ├── service/
  │   ├── AbstractForecastAlgorithm.java (预测算法抽象)
  │   ├── RandomForestServiceImpl.java (RF算法实现或Python调用适配)
  │   ├── SbaForecastServiceImpl.java (SBA算法实现)
  │   ├── AiFeatureService.java (特征工程与数据清洗)
  │   └── StockThresholdService.java (SS和ROP计算)
  ├── mapper/
  │   ├── AiForecastResultMapper.java
  │   └── AiDeviceFeatureMapper.java
  ├── jobs/
  │   └── MonthlyForecastJob.java (每月 1 日凌晨运行的主流程任务)
  └── dto/
      └── PredictContextDTO.java
```

---

## 注意事项与约束

1. **Python 与 Java 的集成方式**：由于核心推荐了随机森林和SBA算法，你可以选择使用 Java 的机器学习库 (如 Tribuo/Weka/Smile)，但更推荐在后端提供一个封装的服务调用 Python 脚本（利用 `Runtime.getRuntime().exec` 或 HTTP 本地微服务），请你确定一种可实现且最简洁的方案并编写代码。说明你所采用的方式及依赖配置。
2. **批量处理**：由于系统备件可能有上万个，模型特征提取和预测必须支持**批量处理**，禁止使用在 for 循环中执行单条 SQL 查询历史数据的行为。
3. **计算回绝机制**：若某些新备件的历史数据点小于 3 个月，无法进行有效的时间序列预测时，应当具备 Fallback 机制（如直接使用简单的最近一月均值）。

---

## 💡 开发分步建议给 Claude Code：

建议按以下顺序把需求发给 Claude 以保证代码质量：

| 步骤 | 给 Claude Code 发出的独立指令 |
|---|---|
| 1️⃣ | 「生成 AI 智能分析模块的基础实体类、Mapper、SQL建表语句（MySQL 5.7兼容）及 DTO。」 |
| 2️⃣ | 「设计算法的 Java 接口及具体实现：实现需求分型逻辑（ADI与CV²计算）、及特征工程数据提取服务。」 |
| 3️⃣ | 「实现核心预测逻辑（含 Python 脚本与 Java 调用集成，或基于 Java 原生 ML 库实现 RF 和 SBA）及 MASE 指标评估。」 |
| 4️⃣ | 「实现安全库存 SS 和 ROP 的阈值重算、并串联智能补货建议推送到 M6采购模块 的完整 Java 业务服务。」 |
| 5️⃣ | 「创建每月定时任务 `MonthlyForecastJob` 串联这 5 个子流程，并提供前台调用的手动触发及结果查询 API Controller。」 |
