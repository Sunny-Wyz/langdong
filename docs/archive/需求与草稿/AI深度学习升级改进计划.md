# 备件管理系统 — AI 深度学习升级与 Python 外联服务改进计划

> 编写日期：2026-03-30

---

## 一、现有 AI 服务架构总览

当前系统已具备较完整的 AI 能力矩阵，覆盖以下六大模块：

| 模块 | 当前算法 | 实现语言 | 状态 |
|------|---------|---------|------|
| 需求预测 | SBA（间歇需求）+ Random Forest（Smile 库） | Java | 生产就绪 |
| 需求分类 | Syntetos-Boylan ADI/CV² 分类 → 算法路由 | Java | 生产就绪 |
| ABC/XYZ 分类 | 多维评分 + 变异系数 | Java | 生产就绪 |
| 设备故障预测 | 逻辑回归（固定系数） | Java | 生产就绪 |
| 设备健康评估 | 加权多维评分（运行/故障/工单/更换） | Java | 生产就绪 |
| 维护建议生成 | 基于规则的决策树 | Java | 生产就绪 |
| 预测性维护(RUL) | Bi-LSTM + Monte Carlo Dropout + SHAP | Python | 研究/未集成 |
| 智能补货建议 | LSTM + 供应商评分 | Python | 研究/未集成 |

---

## 二、现有算法的局限性分析

### 2.1 需求预测模块

**当前问题：**
- SBA 使用固定平滑参数（α=0.15, β=0.10），无法自动调优
- Random Forest 特征工程过于简单（仅 lag1, lag2, roll3_mean 三个特征），且训练样本仅 9 条
- 置信区间基于 RMSE 近似，非真正的分位数回归
- 无法捕捉季节性、趋势性等复杂时序模式

### 2.2 故障预测模块

**当前问题：**
- 逻辑回归系数是手动硬编码的（-2.5, 0.003, 1.2, -0.5, 2.0），非数据驱动
- 无法学习特征间的非线性交互
- 缺少时间序列建模能力，无法利用传感器数据的时序依赖性

### 2.3 设备健康评估

**当前问题：**
- 权重固定（25/35/20/20），缺乏数据驱动的自适应能力
- 阈值划分（MTBF 映射）为人工经验值，泛化能力差
- 未利用传感器实时数据流

### 2.4 Python 模块未集成

- `predictive_maintenance.py` 和 `smart_replenishment.py` 已有 LSTM 实现，但仅为独立脚本
- 与 Java 后端无通信机制，无法在生产环境使用

---

## 三、深度学习升级方案

### 改进项 1：Transformer 时序模型替代 SBA + Random Forest

| 维度 | 说明 |
|------|------|
| **替代目标** | `SbaForecastServiceImpl` + `RandomForestServiceImpl` |
| **新算法** | Temporal Fusion Transformer (TFT) |
| **优势** | 自动处理多种时间尺度；内置可解释性注意力机制；天然支持间歇需求和多步预测；概率预测输出（分位数回归） |
| **Python 框架** | PyTorch + `pytorch-forecasting` |

**具体实现思路：**

```
输入特征:
  - 静态协变量: 备件类别(ABC)、供应商、设备类型、提前期
  - 已知未来变量: 月份、是否节假日、计划检修排期
  - 历史观测变量: 12个月消耗量、价格变动、库存水平

模型结构:
  TFT(
    hidden_size=64,
    attention_head_size=4,
    max_prediction_length=3,   # 预测未来3个月
    max_encoder_length=12,     # 使用12个月历史
    quantiles=[0.05, 0.5, 0.95]  # 90%置信区间
  )
```

**相比现有方案的优势：**
- SBA 只能做单步预测 → TFT 支持多步滚动预测
- RF 仅 3 个特征 → TFT 自动学习数十个特征的交互关系
- 固定置信区间 → 学习得到的分位数回归，更准确的不确定性估计

---

### 改进项 2：深度学习故障预测替代逻辑回归

| 维度 | 说明 |
|------|------|
| **替代目标** | `FaultPredictionEngine`（硬编码逻辑回归） |
| **新算法** | 双通道模型：1D-CNN 处理传感器时序 + XGBoost 处理统计特征，Late Fusion |
| **优势** | 数据驱动学习系数；捕捉非线性模式；利用传感器原始波形 |
| **Python 框架** | PyTorch + XGBoost |

**具体实现思路：**

```
通道1 - 时序特征 (1D-CNN + GRU):
  输入: spare_part_sensor_log 中的传感器序列
        [temperature, vibration, pressure, current_load, rpm] × T时间步
  结构: Conv1D(5→32, k=3) → Conv1D(32→64, k=3) → GRU(64→32) → 隐向量

通道2 - 统计特征 (XGBoost):
  输入: 运行小时数、故障频率、MTBF、劣化率、设备年龄
  输出: 故障概率估计

融合层:
  Concat(CNN隐向量, XGBoost概率) → Dense(64→1) → Sigmoid
  输出: 未来30/60/90天故障概率
```

**相比现有方案的优势：**
- 硬编码系数 → 数据驱动训练
- 无法处理传感器时序 → CNN 自动提取波形模式（异常振动频率等）
- 单一概率输出 → 多时间窗口（30/60/90天）概率预测

---

### 改进项 3：AutoEncoder 异常检测增强健康评估

| 维度 | 说明 |
|------|------|
| **替代目标** | `DeviceHealthCalculator`（固定加权评分） |
| **新算法** | Variational AutoEncoder (VAE) 异常检测 + 注意力加权 |
| **优势** | 无监督学习正常运行模式；重构误差自然表达健康偏离程度；无需人工设定权重 |
| **Python 框架** | PyTorch |

**具体实现思路：**

```
训练阶段（仅使用健康状态数据）:
  输入: [run_hours, fault_count, work_order_count, part_replace_qty,
         temperature_avg, vibration_avg, ...] 归一化
  VAE Encoder: Dense(N→64→32) → μ, σ
  VAE Decoder: Dense(32→64→N) → 重构

推理阶段:
  health_score = 100 × exp(-λ × reconstruction_error)

  其中 reconstruction_error = MSE(input, reconstructed)
  λ 根据训练集误差分布的99分位数校准
```

**相比现有方案的优势：**
- 固定权重 → 自动学习各维度的重要性
- 离散阈值 → 连续平滑的健康分数
- 无法检测新型异常 → VAE 对未见过的异常模式天然敏感

---

### 改进项 4：Graph Neural Network 供应链优化

| 维度 | 说明 |
|------|------|
| **新增能力** | 建模备件-设备-供应商三方关系 |
| **算法** | Graph Attention Network (GAT) |
| **优势** | 捕捉供应链拓扑结构；替代件推荐；级联风险传播分析 |
| **Python 框架** | PyTorch Geometric |

**具体实现思路：**

```
图结构:
  节点: 备件(属性:库存/消耗/价格) + 设备(属性:健康分/运行时间) + 供应商(属性:评分/交期)
  边: 备件-设备(安装关系) + 备件-供应商(供应关系) + 备件-备件(替代关系)

GAT层:
  3层 GATConv(hidden=64, heads=4) + 残差连接

应用:
  1. 替代件推荐: 相似备件节点的embedding距离排序
  2. 缺货风险传播: 供应商延迟 → 影响哪些设备
  3. 采购优先级: 综合设备关键度+库存水位+供应商可靠性
```

---

### 改进项 5：集成现有 LSTM 模块并升级

| 维度 | 说明 |
|------|------|
| **目标** | 集成 `predictive_maintenance.py` 和 `smart_replenishment.py` |
| **升级方向** | Bi-LSTM → Transformer Encoder；增加在线学习能力 |
| **Python 框架** | 保持 PyTorch，增加 ONNX 导出 |

---

## 四、Python 外联服务架构设计

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────┐
│                   Vue 2 Frontend                     │
└─────────────────┬───────────────────────────────────┘
                  │ HTTP
┌─────────────────▼───────────────────────────────────┐
│              Spring Boot Backend                     │
│  ┌─────────────────────────────────────────────┐    │
│  │  AiForecastService (调度器/路由)              │    │
│  │  ├── SBA / RF (Java, 轻量快速)               │    │
│  │  └── PythonModelClient (HTTP调用Python)      │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────┬───────────────────────────────────┘
                  │ HTTP (REST / gRPC)
┌─────────────────▼───────────────────────────────────┐
│          Python AI Microservice (FastAPI)             │
│  ┌─────────┐ ┌──────────┐ ┌───────────┐            │
│  │ TFT需求  │ │ CNN+XGB  │ │ VAE健康   │            │
│  │ 预测服务  │ │ 故障预测  │ │ 评估服务  │            │
│  ├─────────┤ ├──────────┤ ├───────────┤            │
│  │ GAT供应链│ │ LSTM-RUL │ │ 智能补货  │            │
│  │ 优化服务  │ │ 预测服务  │ │ 建议服务  │            │
│  └─────────┘ └──────────┘ └───────────┘            │
│  ┌─────────────────────────────────────────────┐    │
│  │ 公共层: 模型注册/版本管理/特征存储/监控       │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────┬───────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────┐
│  基础设施: MySQL + Redis(缓存) + MinIO(模型存储)     │
└─────────────────────────────────────────────────────┘
```

### 4.2 Python 服务技术栈

```
核心框架:    FastAPI (异步高性能 REST API)
深度学习:    PyTorch 2.x
时序预测:    pytorch-forecasting (TFT实现)
传统ML:      XGBoost, scikit-learn
图网络:      PyTorch Geometric
可解释性:    SHAP, Captum (PyTorch原生)
模型管理:    MLflow (版本追踪/实验对比/模型注册)
任务队列:    Celery + Redis (异步训练任务)
API文档:     FastAPI 自带 OpenAPI/Swagger
容器化:      Docker + docker-compose
```

### 4.3 Java ↔ Python 通信方案

采用 **REST API + 异步回调** 的混合模式：

```
实时推理（<500ms）:
  Java → HTTP POST /predict → Python FastAPI → 返回结果
  适用: 单设备故障概率查询、单备件需求预测

批量任务（分钟级）:
  Java → HTTP POST /batch/forecast → Python 返回 task_id
  Python 执行完毕 → HTTP POST /callback → Java 接收结果
  适用: 月度批量预测、全量模型再训练

模型预热:
  服务启动时加载所有模型到GPU/CPU内存
  使用 ONNX Runtime 加速推理
```

### 4.4 FastAPI 服务目录结构

```
python-ai-service/
├── app/
│   ├── main.py                    # FastAPI 入口
│   ├── config.py                  # 配置管理
│   ├── api/
│   │   ├── v1/
│   │   │   ├── forecast.py        # 需求预测接口
│   │   │   ├── fault.py           # 故障预测接口
│   │   │   ├── health.py          # 健康评估接口
│   │   │   ├── supply_chain.py    # 供应链优化接口
│   │   │   └── maintenance.py     # RUL + 补货接口
│   │   └── callback.py            # 异步回调接口
│   ├── models/
│   │   ├── tft_forecaster.py      # Temporal Fusion Transformer
│   │   ├── fault_predictor.py     # CNN+XGBoost 融合模型
│   │   ├── vae_health.py          # VAE 健康评估
│   │   ├── gat_supply.py          # GAT 供应链
│   │   └── lstm_rul.py            # LSTM RUL (升级版)
│   ├── training/
│   │   ├── trainer.py             # 统一训练框架
│   │   ├── data_pipeline.py       # 数据加载/预处理
│   │   └── evaluation.py          # 模型评估指标
│   ├── services/
│   │   ├── model_registry.py      # MLflow 模型注册
│   │   ├── feature_store.py       # 特征缓存管理
│   │   └── monitoring.py          # 模型漂移检测
│   └── utils/
│       ├── db.py                  # 数据库连接
│       └── explainer.py           # SHAP/Captum 解释器
├── tests/
├── Dockerfile
├── docker-compose.yml
└── requirements.txt
```

---

## 五、分阶段实施计划

### 第一阶段：基础设施搭建（预计 2 周）

- [ ] 搭建 FastAPI 项目骨架，定义 API 接口规范
- [ ] 配置 Docker 环境（Python 3.11 + CUDA 可选）
- [ ] 实现 Java `PythonModelClient` 与 Python 服务的 HTTP 通信
- [ ] 部署 MLflow 用于模型版本管理
- [ ] 集成 Redis 作为特征缓存和任务队列
- [ ] 编写健康检查、日志、监控基础组件

### 第二阶段：需求预测升级（预计 3 周）

- [ ] 实现 TFT 模型的数据管道（从 MySQL 读取历史消耗 + 特征工程）
- [ ] 训练 TFT 模型，与现有 SBA/RF 做 A/B 对比（MASE 指标）
- [ ] 实现分位数回归输出（替代 Poisson/RMSE 近似置信区间）
- [ ] 注册模型到 MLflow，Java 端通过 `/predict` 调用
- [ ] 保留 SBA/RF 作为回退方案（Python 服务不可用时）
- [ ] 前端 `AiForecastResult.vue` 增加算法类型 "TFT" 显示

### 第三阶段：故障预测升级（预计 3 周）

- [ ] 实现 1D-CNN + GRU 传感器时序特征提取器
- [ ] 实现 XGBoost 统计特征模型
- [ ] Late Fusion 融合两个通道，训练端到端模型
- [ ] 对比现有逻辑回归的 AUC-ROC，验证提升
- [ ] 实现多时间窗口（30/60/90天）概率预测
- [ ] 前端 `FaultPrediction.vue` 增加多窗口概率展示

### 第四阶段：健康评估 + RUL 升级（预计 2 周）

- [ ] 实现 VAE 健康评估模型，使用历史正常数据训练
- [ ] 集成已有 `predictive_maintenance.py` 的 LSTM-RUL，升级为 Transformer Encoder
- [ ] VAE 重构误差 → 健康分数的映射校准
- [ ] 前端 `HealthMonitor.vue` 增加 AI 健康分数对比展示

### 第五阶段：供应链优化（预计 3 周）

- [ ] 构建备件-设备-供应商知识图谱（从现有数据库关系抽取）
- [ ] 实现 GAT 模型训练
- [ ] 集成 `smart_replenishment.py` 并升级
- [ ] 实现替代件推荐 API
- [ ] 实现缺货风险传播分析 API
- [ ] 前端新增供应链可视化页面

### 第六阶段：模型运维与持续优化（持续）

- [ ] 实现模型漂移检测（PSI/KL散度监控特征分布）
- [ ] 自动再训练触发机制（漂移超阈值时）
- [ ] A/B 测试框架（新旧模型并行对比）
- [ ] 模型推理性能优化（ONNX 导出、批处理）
- [ ] 完善 Swagger API 文档与集成测试

---

## 六、各改进项预期效果对比

| 模块 | 现有方案 | 升级方案 | 预期提升 |
|------|---------|---------|---------|
| 需求预测 | SBA/RF, MASE ≈ 0.8-1.2 | TFT | MASE 降低 20-35%，间歇需求表现显著提升 |
| 故障预测 | 逻辑回归(硬编码) | CNN+XGBoost | AUC-ROC 从约 0.65 提升至 0.85+ |
| 健康评估 | 加权评分(固定) | VAE | 异常检测 F1 提升 30%+，消除人工阈值依赖 |
| RUL预测 | Bi-LSTM(未集成) | Transformer + 集成到生产 | MAE 降低 15-25%，正式投入使用 |
| 供应链 | 无 | GAT | 新增能力：替代件推荐、级联风险分析 |

---

## 七、风险与缓解策略

| 风险 | 缓解措施 |
|------|---------|
| 训练数据不足 | 保留现有 SBA/RF 作为回退；使用迁移学习和数据增强 |
| Python 服务稳定性 | 熔断器模式 + Java 端回退到本地算法 |
| GPU 资源需求 | TFT/VAE 可在 CPU 推理；训练可使用云 GPU 按需租用 |
| 模型过拟合 | MLflow 实验追踪 + 交叉验证 + Early Stopping |
| 接口延迟 | ONNX 加速推理；批量预测使用异步队列 |

---

## 八、总结

当前系统的 AI 架构**已具备良好的工程基础**（算法路由、回退机制、异步调度），但算法层面存在明显的升级空间。核心升级路径是：

1. **用 Transformer 家族替代传统统计模型**（TFT 替代 SBA/RF）
2. **用数据驱动的深度学习替代硬编码规则**（CNN+XGBoost 替代逻辑回归）
3. **用无监督学习替代人工阈值**（VAE 替代加权评分）
4. **新增图神经网络建模供应链关系**（GAT）
5. **通过 Python FastAPI 微服务统一承载所有深度学习模型**

Python 外联服务的引入不仅解决了当前两个 Python 脚本未集成的问题，更建立了一个可扩展的 AI 模型服务平台，为后续持续引入更先进的算法奠定了基础。
