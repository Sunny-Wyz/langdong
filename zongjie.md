# AI 智能模块交付总结

## 一、交付文件清单

| 文件 | 行数 | 用途 |
|---|---|---|
| `predictive_maintenance.py` | 928 行 | AI 预测性维护模块（RUL 剩余寿命预测） |
| `smart_replenishment.py` | 686 行 | AI 智能补货建议模块（需求预测 + 采购建议） |
| `silu.md` | 437 行 | 两个模块的完整实现思路文档 |
| `zongjie.md` | 本文件 | 交付总结 |

---

## 二、模块一：预测性维护（predictive_maintenance.py）

### 2.1 功能概述

给定一个备件 ID，基于其历史传感器数据（温度、振动、压力、电流、转速等），预测剩余可用寿命（RUL），并在 RUL 低于安全阈值时触发预警。

### 2.2 核心接口

```python
from predictive_maintenance import predict_rul

result = predict_rul(spare_part_id=42)
```

返回结构：

```json
{
  "spare_part_id": 42,
  "predicted_rul": 387.5,
  "confidence_interval": {"lower_95": 312.1, "upper_95": 462.9},
  "alert": "CRITICAL",
  "alert_message": "【紧急预警】备件 ID=42 预测剩余寿命 387.5h，已低于安全阈值 500.0h……",
  "top_features": {
    "rank_1": {"feature": "vibration", "importance": 0.0234, "direction": "正向影响（缩短寿命）"},
    "rank_2": {"feature": "temperature", "importance": 0.0189, "direction": "正向影响（缩短寿命）"},
    "rank_3": {"feature": "operating_hours", "importance": 0.0156, "direction": "正向影响（缩短寿命）"}
  },
  "data_quality": "sufficient",
  "timestamp": "2026-02-24T..."
}
```

### 2.3 代码结构

| Section | 内容 |
|---|---|
| Section 1 | 导入库（TensorFlow, scikit-learn, SHAP, SQLAlchemy） |
| Section 2 | 全局配置（`DB_CONFIG`, `MODEL_CONFIG`） |
| Section 3 | 日志配置 |
| Section 4 | 数据库函数：`get_db_engine()`, `ensure_sensor_table_exists()`, `load_sensor_data()` |
| Section 5 | 数据预处理：`generate_synthetic_data()`, `preprocess_data()`, `create_sequences()` |
| Section 6 | 模型管理：`build_lstm_model()`, `train_model()`, `save_model()`, `load_model_and_scaler()` |
| Section 7 | 预测与解释：`monte_carlo_predict()`, `get_shap_explanation()`, `predict_rul()` |
| Section 8 | `main()` 演示函数 |

### 2.4 技术方案

- **模型**：双层 LSTM（128→64），输入 30 个时间步 × 7 特征，输出标量 RUL
- **损失函数**：Huber Loss（对异常值鲁棒）
- **置信区间**：Monte Carlo Dropout（100 次采样，95% CI）
- **可解释性**：SHAP KernelExplainer，返回 Top-3 关键特征
- **数据库扩展**：自动创建 `spare_part_sensor_log` 表

### 2.5 数据不足降级策略

```
≥ 100 条传感器记录  →  正常训练 LSTM
10~99 条            →  合成数据补充至 200 条再训练
< 10 条             →  加载预训练模型（rul_model.h5）
0 条                →  返回保守默认估计 + 警告
```

### 2.6 预警机制

```
RUL < 500h          →  CRITICAL（紧急，立即维修）
500h ≤ RUL < 1000h  →  WARNING（计划维修窗口）
RUL ≥ 1000h         →  OK（运行正常）
```

### 2.7 模型持久化

```
rul_model.h5     ← Keras HDF5 格式（架构 + 权重）
rul_scaler.pkl   ← MinMaxScaler（必须与模型配套）
```

---

## 三、模块二：智能补货建议（smart_replenishment.py）

### 3.1 功能概述

批量分析备件的历史消耗数据，预测未来 3 个月需求量，综合当前库存、安全库存和供应商绩效，生成完整的采购建议（买多少、何时买、找谁买、为什么）。

### 3.2 核心接口

```python
from smart_replenishment import suggest_replenishment

results = suggest_replenishment(spare_part_ids=[1, 2, 3])
```

返回结构（每个备件一条）：

```json
{
  "spare_part_id": 1,
  "spare_part_name": "轴承-6205",
  "current_stock": 20,
  "predicted_demand": {
    "method": "lstm",
    "monthly_detail": [18.3, 19.7, 21.0],
    "total": 59.0,
    "confidence_interval": {"lower": [12.1, 13.5, 14.8], "upper": [24.5, 25.9, 27.2]}
  },
  "suggestion": {
    "suggested_qty": 48,
    "safety_stock": 9.1,
    "suggested_date": "2026-03-05",
    "lead_time_days": 14
  },
  "supplier": {
    "name": "华东精密轴承",
    "score": 0.88,
    "quality_score": 0.95,
    "on_time_rate": 0.9,
    "price_score": 0.75,
    "lead_time_days": 14,
    "reason": "综合评分最高：质量优秀（95%）、交付及时（90%）"
  },
  "priority": "HIGH",
  "alert_message": "【高优先级预警】备件「轴承-6205」预测未来 3 个月需求 59 件，远超当前库存 20 件……",
  "explanation": {
    "rank_1": {"feature": "outbound_qty", "description": "月出库数量", "importance": 0.034, "direction": "推高需求"},
    "rank_2": {"feature": "repair_count", "description": "月维修次数", "importance": 0.021, "direction": "推高需求"},
    "rank_3": {"feature": "month_sin", "description": "季节因素（周期项）", "importance": 0.012, "direction": "推高需求"}
  },
  "data_quality": "sufficient",
  "timestamp": "2026-02-24T..."
}
```

### 3.3 代码结构

| Section | 内容 |
|---|---|
| Section 1-2 | 导入库 + **从 `predictive_maintenance.py` 复用** `DB_CONFIG` / `get_db_engine()` |
| Section 3-4 | `DEMAND_CONFIG` 全局配置 + 日志 |
| Section 5 | 数据库建表 + 数据加载（`load_spare_part_info`, `load_consumption_data`, `load_supplier_performance`） |
| Section 6 | `aggregate_monthly_data()` 预处理 + 月份 sin/cos 编码 + 合成数据生成 + 滑动窗口 |
| Section 7 | `build_demand_model()` / `train_demand_model()` / `save_demand_model()` / `load_demand_model()` |
| Section 8 | `predict_demand()` — LSTM + MC Dropout / 统计降级 / 默认估计 |
| Section 9 | `get_demand_shap_explanation()` — SHAP 特征解释（适配需求场景） |
| Section 10 | `select_best_supplier()` — 供应商绩效评分选择 |
| Section 11 | **`suggest_replenishment(spare_part_ids)`** — 批量补货建议核心接口 |
| Section 12 | `main()` 演示（24月 / 4月 / 2月 三种数据量场景） |

### 3.4 技术方案

- **模型**：双层 LSTM（64→32），输入 12 个月 × 6 特征，输出 3 个月需求量
- **特征工程**：月份正弦/余弦循环编码（解决 12→1 月份跳变）
- **置信区间**：Monte Carlo Dropout（50 次采样）
- **可解释性**：SHAP KernelExplainer（对预测总需求解释 Top-3 特征）
- **数据库扩展**：自动创建 `spare_part_consumption_log` + `supplier_performance` 两张表

### 3.5 补货公式

```
建议采购量 = max(0, 预测总需求 - 当前库存 + 安全库存)
安全库存   = 日均消耗 × 交货期天数 × 1.5
建议日期   = 今天 + (当前库存 / 日均消耗) - 交货期天数
供应商评分 = 0.4 × 质量合格率 + 0.3 × 按时交付率 + 0.3 × 价格竞争力
```

### 3.6 数据不足降级策略

```
≥ 6 个月消耗记录   →  正常训练 LSTM
3~5 个月           →  合成数据补充至 24 个月再训练
< 3 个月           →  统计平均法（月均消耗 × M）
0 个月             →  返回保守默认估计（每月 5 件）+ 警告
```

### 3.7 预警机制

```
预测总需求 > 当前库存 × 1.5  →  HIGH（高优先级，立即采购）
预测总需求 > 当前库存        →  MEDIUM（正常周期补货）
预测总需求 ≤ 当前库存        →  LOW（库存充足）
```

### 3.8 模型持久化

```
demand_model.h5     ← Keras HDF5 格式
demand_scaler.pkl   ← MinMaxScaler
```

---

## 四、两模块代码复用关系

```
predictive_maintenance.py              smart_replenishment.py
─────────────────────────              ──────────────────────
DB_CONFIG ─────────────────────────→  import 直接复用
get_db_engine() ───────────────────→  import 直接复用
MinMaxScaler 预处理模式 ───────────→  preprocess_features() 结构相同
Monte Carlo Dropout 推断 ──────────→  _mc_dropout_predict() 适配多输出
SHAP KernelExplainer 框架 ─────────→  get_demand_shap_explanation() 适配需求特征
模型 save/load 模式 ───────────────→  save_demand_model() / load_demand_model()
合成数据降级策略 ──────────────────→  generate_synthetic_monthly_data() 适配月度模式
```

**导入策略**：`smart_replenishment.py` 通过 `try/except` 导入 `predictive_maintenance` 中的公共组件；若模块不存在则自动降级为本地定义，保证独立可运行。

---

## 五、数据库表总览

两个模块共用 `spare_db` 数据库，在原有表基础上新增 3 张表：

| 表名 | 创建者 | 用途 |
|---|---|---|
| `spare_part`（已有） | 原系统 | 备件库存基本信息 |
| `user`（已有） | 原系统 | 用户认证 |
| `spare_part_sensor_log`（新增） | predictive_maintenance.py | 传感器时序数据（小时级） |
| `spare_part_consumption_log`（新增） | smart_replenishment.py | 月度消耗日志 |
| `supplier_performance`（新增） | smart_replenishment.py | 供应商绩效评分 |

所有新增表在模块首次运行时通过 `CREATE TABLE IF NOT EXISTS` 自动创建，无需手动迁移。

---

## 六、依赖安装

```bash
pip install tensorflow scikit-learn pandas numpy pymysql sqlalchemy shap scipy
```

---

## 七、与 Spring Boot 后端集成方式

两个模块均返回标准 JSON 结构，Spring Boot 后端可通过以下方式调用：

### 方式一：子进程调用（简单部署）

```java
ProcessBuilder pb = new ProcessBuilder(
    "python3", "smart_replenishment.py", "--ids", "1,2,3"
);
Process p = pb.start();
String json = new String(p.getInputStream().readAllBytes());
```

### 方式二：FastAPI 微服务（推荐生产环境）

```python
# 将 Python 模块包装为 REST API
from fastapi import FastAPI
app = FastAPI()

@app.get("/api/predict-rul")
def api_predict_rul(spare_part_id: int):
    return predict_rul(spare_part_id)

@app.post("/api/suggest-replenishment")
def api_suggest(ids: List[int]):
    return suggest_replenishment(ids)
```

Spring Boot 通过 RestTemplate / WebClient 调用：

```
GET  http://localhost:8001/api/predict-rul?spare_part_id=42
POST http://localhost:8001/api/suggest-replenishment  body: [1, 2, 3]
```

---

## 八、两模块的业务协同

```
              传感器数据（小时级）              消耗记录（月度）
                    ↓                              ↓
         ┌─────────────────────┐       ┌─────────────────────────┐
         │ predictive_maintenance│       │  smart_replenishment     │
         │    predict_rul()     │       │ suggest_replenishment()  │
         └─────────┬───────────┘       └────────────┬────────────┘
                   ↓                                ↓
            RUL < 500h 预警                  需求 > 库存×1.5 预警
                   ↓                                ↓
                   └──────────┐    ┌────────────────┘
                              ↓    ↓
                     ┌────────────────┐
                     │  运维决策中心    │
                     │                │
                     │ • 何时维修？    │  ← RUL 模块回答
                     │ • 买什么？多少？│  ← 补货模块回答
                     │ • 何时买？      │  ← 补货模块回答
                     │ • 找谁买？      │  ← 补货模块回答
                     └────────────────┘
```

RUL 预警可以作为补货需求的上游触发信号：当预测到某备件即将故障时，自动触发该备件的补货建议生成，形成从「预测维护」到「智能采购」的完整闭环。
