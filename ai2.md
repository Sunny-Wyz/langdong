# 备件管理系统 — AI 技术应用清单

> 本文档整理项目中所有用到 AI / 智能分析技术的功能模块，包含算法原理、核心公式和关键文件路径。

---

## 一、备件智能分类模块（F9）

**技术类型**：统计学习 + 多维度加权评分

### 1.1 ABC 分类

基于多维度加权综合评分对备件进行 ABC 分类，满分100分：

| 维度 | 权重 | 计算方式 |
|------|------|---------|
| 年消耗金额 | 40% | Min-Max 归一化：(annualCost / maxAnnualCost) × 100 |
| 设备关键度 | 30% | is_critical=1 → 100分，=0 → 0分 |
| 采购提前期 | 20% | >30天→100，15~30天→60，<15天→20 |
| 供应替代难度 | 10% | (replaceDiff - 1) / 4.0 × 100（replaceDiff范围1~5） |

按综合得分排序后，以分位数划分 A/B/C 类。

**关键文件**：
- `backend/src/main/java/com/langdong/spare/util/ClassifyCalculator.java`
- `backend/src/main/java/com/langdong/spare/service/ClassifyService.java`

---

### 1.2 XYZ 分类

基于需求变异系数 CV²（Coefficient of Variation Squared）衡量需求稳定性：

```
CV² = (σ / μ)²      （仅对非零需求期计算）

X类：CV² < 0.5       → 稳定需求
Y类：0.5 ≤ CV² < 1.0 → 波动需求
Z类：CV² ≥ 1.0       → 随机需求
特殊：有效消耗月数 < 3 → 强制归 Z 类
```

**关键文件**：`ClassifyCalculator.java`

---

### 1.3 安全库存（SS）与补货触发点（ROP）

根据 ABC 分类对应的服务水平系数 k，结合预测需求标准差和采购提前期计算：

```
SS  = k × σ_month × √L
ROP = d̄_month × L + SS

k 系数：A类 → 2.33，B类 → 1.65，C类 → 1.28
L：采购提前期（月）
σ_month：月度需求标准差
d̄_month：月均需求量
```

**关键文件**：`ClassifyCalculator.java`、`ClassifyService.java`

---

### 1.4 定时自动重算

- 触发方式：`@Scheduled(cron="0 0 1 1 * ?")` 每月1日凌晨1点自动执行
- 异步执行：`@Async` 防止阻塞
- 手动触发：`POST /api/classify/trigger`（ADMIN 权限）

---

### 1.5 ABC×XYZ 热力矩阵可视化

- 3×3 九宫格矩阵，颜色深浅表示备件数量
- ECharts 热力图渲染，支持点击格子联动过滤列表
- **前端文件**：`frontend/src/views/classify/ClassifyResult.vue`

---

## 二、AI 需求预测模块（F10）

**技术类型**：时间序列分析 + Syntetos-Boylan 分型 + 随机森林 + 指数平滑

### 2.1 特征工程与算法分型器

读取近12个月历史消耗数据，计算两个关键统计量：

**ADI（平均需求间隔）**：
```
ADI = T / N₊      T：总期数，N₊：非零需求期数
ADI → MAX_VALUE（当从未发生需求时）
```

**CV²（需求变异系数平方）**：
```
CV² = (σ / μ)²    仅对非零需求期计算
CV² = 0（非零期数 < 2 时）
```

**Syntetos-Boylan 分型矩阵**：

| 条件 | 算法路由 | 需求类型 |
|------|---------|---------|
| 非零样本 < 3 | FALLBACK | 数据不足 |
| ADI > 1.32 且 CV² > 0.49 | SBA | 间断型需求 |
| 其他 | RF（随机森林） | 规律型需求 |

**关键文件**：`backend/src/main/java/com/langdong/spare/service/ai/AiFeatureService.java`

---

### 2.2 SBA 算法（间断型需求预测）

**算法**：Syntetos-Boylan Approximation，分别对需求大小和需求间隔做指数平滑：

```
平滑参数：α = 0.15（需求大小），β = 0.10（需求间隔）

更新公式（当 d_t > 0 时）：
  z_t = α × d_t + (1-α) × z_(t-1)   [需求大小平滑]
  p_t = β × q_t + (1-β) × p_(t-1)   [需求间隔平滑]

预测公式：
  ŷ_(t+1) = (1 - β/2) × (z_t / p_t)

90% 置信区间（泊松近似）：
  σ ≈ √ŷ
  区间：[max(0, ŷ - 1.645×σ), ŷ + 1.645×σ]
```

**关键文件**：`backend/src/main/java/com/langdong/spare/service/ai/SbaForecastServiceImpl.java`

---

### 2.3 随机森林算法（规律型需求预测）

**算法**：Random Forest（依赖 `smile-core 3.1.0` Java ML 库）

**特征构造**（滑动窗口 = 3）：
```
x_t = [lag1, lag2, roll3_mean]
    = [d_(t-1), d_(t-2), mean(d_(t-1), d_(t-2), d_(t-3))]
目标：y_t = d_t
训练样本数：12个历史月 → 9个样本
```

**模型配置**：
```
ntrees    = 50    （树的数量）
max_depth = 5     （最大深度）
```

**90% 置信区间（RMSE 近似）**：
```
RMSE = sqrt((1/n) × Σ(y_t - ŷ_t)²)
区间：[max(0, ŷ - 1.645×RMSE), ŷ + 1.645×RMSE]
```

- 预测结果取 `max(0, prediction)`，避免负需求
- 执行异常时自动降级为 FALLBACK

**关键文件**：`backend/src/main/java/com/langdong/spare/service/ai/RandomForestServiceImpl.java`

---

### 2.4 FALLBACK 降级策略

**触发条件**：
- 非零历史样本 < 3
- RF 或 SBA 执行时抛出异常

**策略**：
```
ŷ = mean({d_t | d_t > 0})     非零历史均值
置信区间：[0.5 × ŷ, 1.5 × ŷ]   固定比例 ±50%
```

**关键文件**：`backend/src/main/java/com/langdong/spare/service/ai/AbstractForecastAlgorithm.java`

---

### 2.5 MASE 模型评估指标

**MASE（平均绝对比例误差）**——衡量预测精度，与 lag-1 朴素基线比较：

```
MAE_model = (1/n) × Σ|y_t - ŷ_t|
MAE_naive = (1/(n-1)) × Σ|y_t - y_(t-1)|   [lag-1 基线]
MASE = MAE_model / MAE_naive

MASE < 1.0：优于朴素基线
MASE = null：历史数据过短或基线 MAE 为 0
```

**关键文件**：`AbstractForecastAlgorithm.java`

---

### 2.6 预测驱动库存联动（SS/ROP）

将 AI 预测结果转化为库存管理策略：

```
日均需求：d̄ = 预测月总量 / 30
需求标准差反推：σ_d ≈ (上界 - 下界) / (2 × 1.645)

SS = k × σ_d × √L
ROP = d̄ × L + SS

urgency = "紧急" if 当前库存 < SS else "正常"
```

**关键文件**：`backend/src/main/java/com/langdong/spare/service/ai/StockThresholdService.java`

---

### 2.7 调度与触发机制

| 方式 | 实现 |
|------|------|
| 定时任务 | `@Scheduled(cron="0 0 2 1 * ?")` 每月1日凌晨2点 |
| 异步执行 | `@Async` 防止前端阻塞 |
| 手动触发 | `POST /api/ai/forecast/trigger`（ADMIN权限） |

**关键文件**：`backend/src/main/java/com/langdong/spare/service/ai/AiForecastService.java`

---

### 2.8 前端可视化

- ECharts 折线图展示指定备件的历史预测趋势
- MASE 颜色预警：`> 1.0` 红色警告，`≤ 1.0` 绿色正常
- 算法来源标签彩色区分（RF / SBA / FALLBACK）
- 手动触发按钮仅 ADMIN 可见（基于权限 `ai:forecast:trigger`）

**关键文件**：`frontend/src/views/ai/AiForecastResult.vue`

---

## 三、Python 扩展模块（研究性）

> 以下模块为研究探索性实现，未集成至主系统 Spring Boot API。

### 3.1 预测性维护（predictive_maintenance.py）

**技术**：LSTM 时间序列模型 + SHAP 可解释性

| 功能 | 技术 |
|------|------|
| 备件剩余寿命预测（RUL） | LSTM 深度学习，输入特征：运行时长、温度、振动、压力 |
| 不确定性量化 | Monte Carlo Dropout，生成 95% 置信区间 |
| 模型可解释性 | SHAP KernelExplainer 解释关键特征贡献度 |
| 降级策略 | 数据不足时自动使用合成数据 / 预训练模型 / 默认估计 |
| 预警触发 | RUL < 安全阈值时触发维修预警 |

**依赖**：`tensorflow, scikit-learn, pandas, numpy, pymysql, sqlalchemy, shap, scipy`

---

### 3.2 智能补货建议（smart_replenishment.py）

**技术**：LSTM 需求预测 + 供应商绩效评分

| 功能 | 技术 |
|------|------|
| 未来需求预测 | LSTM 预测未来 M 个月备件消耗量 |
| 采购量建议 | 综合预测需求 + 当前库存 + 安全库存 |
| 供应商优选 | 绩效评分模型（质量评分 + 价格竞争力 + 交货及时率） |
| 模型可解释性 | SHAP 解释预测依据 |
| 不确定性量化 | Monte Carlo Dropout 量化置信区间 |
| 降级策略 | 历史数据 < 6 个月时降级为统计平均法 |
| 高优补货预警 | 预测需求 > 当前库存 × 1.5 时触发高优先级预警 |

**依赖**：`tensorflow, scikit-learn, pandas, numpy, pymysql, sqlalchemy, shap`

---

## 四、数据库支撑

| 表名 | 用途 |
|------|------|
| `ai_forecast_result` | 存储每次预测结果（预测量、置信区间、算法类型、MASE） |
| `ai_device_feature` | 设备特征表（预留：运行时长、故障次数、工单数、换件量） |
| `biz_part_classify` | 存储 ABC/XYZ 分类结果（SS、ROP、综合得分等） |

**SQL 文件**：
- `sql/ai_module.sql`
- `sql/classify_module.sql`

---

## 五、AI 技术总览

| 模块 | 核心 AI 技术 |
|------|------------|
| F9 备件分类 | 多维加权评分、需求变异系数(CV²)、安全库存模型(SS/ROP) |
| F10 需求预测 | Syntetos-Boylan 分型、SBA 指数平滑、随机森林(RF)、MASE评估 |
| F10 库存联动 | 预测驱动的 SS/ROP 动态计算 |
| Python-RUL | LSTM + Monte Carlo Dropout + SHAP |
| Python-补货 | LSTM + 供应商绩效评分 + SHAP |
