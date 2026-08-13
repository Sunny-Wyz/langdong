# 朗动备件管理系统（LangDong Spare Management System）

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.0-6DB33F?logo=springboot&logoColor=white)
![MyBatis](https://img.shields.io/badge/MyBatis-3.0.3-000000?logo=mybatis&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-5.7%2B-4479A1?logo=mysql&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vue.js&logoColor=white)
![Element Plus](https://img.shields.io/badge/Element_Plus-2.5-409EFF)
![FastAPI](https://img.shields.io/badge/FastAPI-0.109-009688?logo=fastapi&logoColor=white)
![ECharts](https://img.shields.io/badge/ECharts-5.4.3-AA344D)
![License](https://img.shields.io/badge/License-未声明-lightgrey)

面向工业场景的备件全生命周期管理系统，覆盖基础档案、仓储、领用、工单、采购、ABC/XYZ 分类、AI 需求预测、PHM 预测性维护与报表看板。

仓库是**三端协作的多服务项目**，不是单一应用：

| 端 | 目录 | 技术栈 | 默认端口 |
|---|---|---|---|
| 业务后端 | `backend/` | Spring Boot 3.2 + Java 21 + MyBatis + Spring Security + JWT | `8080` |
| 管理前端 | `frontend/` | Vue 3.4 + TypeScript + Vite + Element Plus + Pinia | `3000` |
| AI 微服务 | `python-ai-service/` | FastAPI + XGBoost / PyTorch + MLflow + Celery | `8000`（与 Java 默认配置对齐） |

默认账号：`admin` / `123456`

---

## 目录

- [系统架构](#系统架构)
- [项目亮点](#项目亮点)
- [技术栈](#技术栈)
- [核心功能](#核心功能)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [关键模块说明](#关键模块说明)
- [开发与调试](#开发与调试)
- [文档与论文](#文档与论文)
- [贡献指南](#贡献指南)
- [常见问题](#常见问题)

---

## 系统架构

```
浏览器 :3000
    │  Vite 将 /api 反代到后端
    ▼
Spring Boot :8080  ──JWT + RBAC──► MySQL spare_db
    │
    │  HTTP 调用 / 异步回调
    ▼
FastAPI :8000  ──Celery + Redis──► 训练 / 推理
    │
    └── MLflow 实验记录（python-ai-service/mlruns/）
```

数据与调用约定：

- 前端只访问 `/api`，不直接调用 Python。
- Java 通过 `PythonModelClient` 调用 AI 服务，结果回写 `/api/ai/forecast/callback/python`。
- Python 可通过 `/internal/ai/**` 向 Java 拉取消耗、传感器、供应商等训练数据。
- 月度预测主链路已统一为**两阶段 Hurdle-Gamma**，旧 RF/SBA 全量重算已从生产入口移除。

业务闭环：

```
智能补货建议
    → 采购申请 → 询价 → 订单 → 验收入库 → 货位上架
                                              ↓
设备故障 / 人工申请 → 领用审批 → FIFO 出库 → 安装登记
                                              ↓
                                         工单归档
                                              ↓
                    消耗历史 → ABC/XYZ + AI 预测 → 安全库存 / 补货点
                                              ↓
                                         下一轮采购建议
```

PHM 平行运行：设备健康评分 → 故障预测 → 维护建议（采纳后可再开工单 / 领用）。

---

## 项目亮点

- 备件业务闭环：采购申请 → 下单 → 收货入库 → 货位上架 → 领用审批 → FIFO 出库安装 → 维修工单归档。
- 智能分类（ABC/XYZ）与两阶段 Hurdle-Gamma 预测联动安全库存（SS）和补货点（ROP）。
- PHM：设备健康评分、故障预测、维护建议采纳 / 拒绝，并可联动工单与领用。
- 报表与看板：库存、消耗、维修、供应商、预警中心，前端使用 ECharts。
- Spring Security + JWT 无状态鉴权，按菜单 / 按钮细粒度授权；前端支持 access token 静默刷新。
- 定时任务与异步执行：月初重算下月需求；PHM 支持批量评估。
- 本地可用 `scripts/start_all.sh` 一键拉起 MySQL 检查、Redis、Python、Java、前端。

---

## 技术栈

### 后端（`backend/`）

- Java 21（`pom.xml` 的 `java.version`，并开启虚拟线程）
- Spring Boot 3.2.0
- Spring Security + JJWT 0.11.5
- MyBatis Spring Boot Starter 3.0.3
- MySQL Connector/J
- Smile ML 3.1.0（历史随机森林能力，当前月度主链路不再走 RF 全量重算）
- Apache Commons Math 3.6.1（蒙特卡洛安全库存）
- EasyExcel 3.3.4
- JUnit 5 + Mockito（`spring-boot-starter-test`）
- Maven

### 前端（`frontend/`）

- Vue 3.4 + TypeScript
- Vue Router 4（Hash 路由）
- Pinia 2
- Element Plus 2.5
- Axios 1.6（`src/utils/request.ts` 自动注入 Bearer，401 时刷新 token）
- ECharts 5.4.3
- xlsx + file-saver
- Vite 5（开发端口 `3000`，代理 `/api` → `http://localhost:8080`）

### AI 微服务（`python-ai-service/`）

- FastAPI 0.109 + Uvicorn + Pydantic 2
- XGBoost 2.0 / scikit-learn / PyTorch
- NeuralForecast / tsai / PyTorch Lightning（深度学习时序）
- MLflow 2.11
- Celery 5.3 + Redis
- SQLAlchemy + PyMySQL
- pytest

### 数据库

- MySQL 5.7+，库名 `spare_db`

---

## 核心功能

- **基础数据**：备件、货位、设备、供应商、供货品类。
- **仓储**：收货入库、货位上架、库存总台账 / 货位明细、FIFO 批次追溯。
- **领用**：申请 → 审批 → 出库确认 → 安装登记 → 记录查询。
- **维修工单**：报修 → 派工 → 维修过程 → 完工确认 → 工单查询。
- **采购**：智能补货建议 → 采购申请 → 询报价 → 订单 → 到货验收。
- **智能分类（M6）**：ABC/XYZ、CV²、安全库存与补货点；支持定时重算与手动触发。
- **AI 智能分析（M7）**：
  - 生产主算法：两阶段 Hurdle-Gamma（`algo_type = TWO_STAGE`）
  - 数据不足时回退展示为「数据不足回退」
  - 任务中心异步提交 / 查询
  - 论文实验页、真实滚动回测实验页
- **PHM**：健康评分与风险分级（CRITICAL / HIGH / MEDIUM / LOW）、故障预测、维护建议采纳 / 拒绝。
- **报表与看板**：KPI、库存 ABC、滞销、周转、消耗趋势、供应商绩效、维修费用、预警中心。
- **系统管理**：用户、角色、菜单权限。

---

## 快速开始

### 1. 环境准备

- JDK 21
- Maven 3.8+
- Node.js 18+
- npm 8+
- MySQL 5.7+
- Python 3.11 + Conda（AI 服务）
- Redis（Celery Worker 需要；一键脚本会尝试拉起）

本地凭据建议写在仓库根目录 `.env.local`（不要提交）：

```bash
DB_USERNAME=admin
DB_PASSWORD=your-password
JWT_SECRET=请换成至少 64 字符的随机密钥
PYTHON_CALLBACK_TOKEN=请换成本地随机 token
AI_PYTHON_BASE_URL=http://localhost:8000
```

### 2. 初始化数据库

```bash
mysql -u root -p < sql/init.sql
```

按需补充功能模块 SQL（建议顺序）：

```bash
mysql -u root -p spare_db < sql/classify_module.sql
mysql -u root -p spare_db < sql/ai_module.sql
mysql -u root -p spare_db < sql/requisition_module.sql
mysql -u root -p spare_db < sql/fix_menu.sql
mysql -u root -p spare_db < sql/work_order_module.sql
mysql -u root -p spare_db < sql/purchase_module.sql
mysql -u root -p spare_db < sql/report_module.sql
mysql -u root -p spare_db < sql/phm_module.sql
mysql -u root -p spare_db < sql/fifo_migration_v1.sql
mysql -u root -p spare_db < sql/mock_data.sql
```

后续菜单 / 演示数据按需执行，例如：

```bash
mysql -u root -p spare_db < sql/add_paper_experiment_menu.sql
mysql -u root -p spare_db < sql/add_real_experiment_menu.sql
mysql -u root -p spare_db < sql/seed_closed_loop_demo.sql
```

### 3. 配置后端

`backend/src/main/resources/application.yml` 已支持环境变量，本地优先用 `.env.local`，不要把真实密码写进仓库。关键项：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/spare_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:123456}

jwt:
  secret: ${JWT_SECRET:spare-management-system-secret-key-2024-langdong}
  expiration: 86400000

app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:3001}

ai:
  python:
    base-url: ${AI_PYTHON_BASE_URL:http://localhost:8000}
    callback-token: ${PYTHON_CALLBACK_TOKEN:langdong-local-dev-20260330}
```

### 4. 一键启动（推荐）

```bash
# 需先配置 .env.local 中的 DB_USERNAME / DB_PASSWORD
./scripts/start_all.sh
./scripts/status_all.sh
./scripts/stop_all.sh
```

`start_all.sh` 会把 Python 打在 **8000**，与 Java 默认 `ai.python.base-url` 一致。

### 5. 分端启动

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

打开 `http://localhost:3000`。开发代理：`/api` → `http://localhost:8080`。

AI 服务（端口必须与 `AI_PYTHON_BASE_URL` 一致）：

```bash
cd python-ai-service
# 建议使用 conda 环境 langdong，或 pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

注意：`python-ai-service/app/main.py` 与 `supervisord.conf` 的进程内默认是 `8001`。单独按默认启动时，请同步改 `AI_PYTHON_BASE_URL`，否则 Java 调不到 Python。

---

## 项目结构

```text
langdong/
├── backend/                              # Spring Boot 业务 API
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/langdong/spare/
│       │   ├── SpareApplication.java
│       │   ├── config/                   # Security / 异步调度 / Python 客户端
│       │   ├── controller/               # 29 个 REST 控制器
│       │   ├── service/                  # 业务编排
│       │   │   └── ai/                   # 预测查询、Python 客户端、回调落库
│       │   ├── forecast/                 # 月度 Hurdle-Gamma 管线（主链路）
│       │   ├── mapper/ + entity/ + dto/
│       │   └── util/                     # JWT、分类、健康评分、故障预测
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── mapper/*.xml
│       └── test/java/                    # 预测校准、分类、PHM 等单测
├── frontend/                             # Vue 3 + Vite 管理后台
│   ├── package.json
│   ├── vite.config.ts                    # 端口 3000，代理 /api
│   └── src/
│       ├── main.ts
│       ├── router/index.ts               # Hash 路由 + 登录守卫
│       ├── store/                        # Pinia：auth、dashboard
│       ├── utils/request.ts
│       └── views/                        # 44 个页面，按业务域分目录
├── python-ai-service/                    # FastAPI AI 微服务
│   ├── app/
│   │   ├── main.py
│   │   ├── api/v1/                       # 补货、周预测、PHM、任务、算法
│   │   ├── models/                       # 特征、Hurdle-Gamma、基线、RUL
│   │   └── services/                     # Celery、任务注册、Java 数据客户端
│   ├── requirements.txt
│   ├── supervisord.conf
│   └── tests/
├── sql/                                  # 建表、迁移、菜单、种子数据
├── scripts/
│   ├── start_all.sh / status_all.sh / stop_all.sh
│   └── paper/                            # 论文复现脚本与结果 JSON
├── docs/                                 # 现行文档 + archive 归档 + 论文
│   ├── 文档导航.md
│   ├── figures/                          # 论文/压测插图
│   └── archive/                          # 草稿、旧原型、历史算法
├── jmeter/                               # 压测计划、结果、报告
├── function.md                           # 功能迭代记录
├── QA.md                                 # 问题与解决方案
└── README.md
```

前端路由分组：

| 前缀 | 页面 |
|---|---|
| `/home/*` | 档案、仓储、领用、工单、采购、报表 |
| `/smart/*` | ABC/XYZ 分类、PHM 健康 / 故障 / 维护建议 |
| `/ai/*` | 预测结果、任务中心、论文实验、真实实验 |
| `/sys/*` | 用户、角色 |

---

## 关键模块说明

### 月度预测主链路（当前生产路径）

入口不在旧的 RF/SBA 实现里，而在 `forecast/`：

| 类 | 职责 |
|---|---|
| `MonthlyForecastScheduler` | 月初 cron 重算**下个月**（唯一月度调度入口） |
| `HurdleGammaJobService` | 任务中心异步提交 / 查询两阶段预测 |
| `StockThresholdService` | 调 Python 做 Hurdle-Gamma，回写 SS / ROP |
| `ReplenishmentService` | 根据阈值生成补货建议 |
| `AiForecastService` | 仅负责预测结果查询与异步回调落库 |
| `PythonModelClient` | Java → Python HTTP 客户端 |

算法类型（`algo_type`）：

- `TWO_STAGE`：两阶段 Hurdle-Gamma（XGBoost 分类 + `reg:gamma` 回归），前端展示「两阶段 Hurdle-Gamma」
- `FALLBACK`：数据不足回退

### 智能分类

- `ClassifyService` + `ClassifyCalculator` / `forecast/classify/AbcXyzClassifier`
- ABC（金额）× XYZ（规律）九宫格
- 输出 CV²、安全库存、补货点
- 支持月度定时重算与 `/api/classify/trigger` 手动触发

### PHM

- `DeviceHealthService` / `FaultPredictionService` / `MaintenanceSuggestionService`
- `PhmOrchestrationService` 负责批量评估编排
- 维护建议采纳后可自动创建工单和领用单

### 主要 REST 前缀

| 模块 | 前缀 |
|---|---|
| 认证 | `/api/auth` |
| 用户 / 角色 / 菜单 | `/api/users`、`/api/roles`、`/api/menus` |
| 基础档案 | `/api/spare-parts`、`/api/locations`、`/api/equipments`、`/api/suppliers` |
| 仓储 | `/api/stock-in`、`/api/shelving`、`/api/stock-ledger`、`/api/outbound-trace` |
| 领用 | `/api/requisitions` |
| 工单 | `/api/work-orders` |
| 采购 | `/api/purchase-orders`、`/api/reorder-suggests` |
| 分类 | `/api/classify` |
| AI 预测 | `/api/ai/forecast`、`/api/ai/forecast/jobs`、`/api/ai/experiment` |
| Python 回调 | `/api/ai/forecast/callback/python` |
| PHM | `/api/phm/health`、`/api/phm/prediction`、`/api/phm/suggestion` |
| 报表 / 预警 | `/api/report`、`/api/warnings` |
| 内部训练数据（给 Python） | `/internal/ai` |

---

## 开发与调试

### 常用命令

```bash
# 后端
cd backend
mvn spring-boot:run
mvn test
mvn clean package

# 前端
cd frontend
npm run dev          # 开发（不是 npm run serve）
npm run build        # vue-tsc + vite build
npm run preview

# Python AI
cd python-ai-service
uvicorn app.main:app --host 0.0.0.0 --port 8000
pytest
```

### 接口联调

- 浏览器只打前端 `3000`，接口走 `/api` 代理。
- 代理配置在 `frontend/vite.config.ts`，不再使用 `vue.config.js`。
- CORS 配置键是 `app.cors.allowed-origins`（复数）。
- 出现 401：检查 JWT secret、token 是否过期、前端是否带上 `Authorization: Bearer <token>`。
- 出现 403：当前角色缺少 `@PreAuthorize` 对应权限，用管理员在角色管理中勾选后重新登录。

### 读代码建议顺序

1. `sql/init.sql` 与 `docs/ARCHITECTURE/系统总体架构.md`
2. `SpareApplication.java` → `SecurityConfig` → 一条完整流程（建议 `RequisitionController` 或 `PurchaseOrderController`）
3. `frontend/src/router/index.ts` → 对应 `views/`
4. `PythonModelClient` ↔ `python-ai-service/app/main.py`
5. `forecast/service/HurdleGammaJobService`（当前月度预测主路径）

---

## 文档与论文

更细的架构、表结构和算法说明见 [docs/文档导航.md](docs/文档导航.md)。

| 文档 | 内容 |
|---|---|
| [系统总体架构](docs/ARCHITECTURE/系统总体架构.md) | 模块、数据流、权限矩阵 |
| [数据模型](docs/DATABASE/数据模型.md) | 表与字段 |
| [API 参考](docs/API/API参考手册.md) | 接口约定 |
| [FIFO 实现](docs/IMPLEMENTATION/FIFO出库指南.md) | 出库批次追溯 |
| [Hurdle-Gamma 与蒙特卡洛](docs/AI_ALGORITHMS/两阶段Hurdle-Gamma与蒙特卡洛.md) | 当前生产预测与安全库存算法 |
| [算法选型-历史对照](docs/AI_ALGORITHMS/算法选型-历史对照.md) | 分类与历史算法对照 |
| [功能迭代](function.md) | 已落地功能记录 |
| [问题记录](QA.md) | 报错与解决方案 |

本仓库同时承担论文 / 毕设实验：

- `docs/paper-aaai2026/`：论文 LaTeX
- `frontend/src/views/ai/PaperExperimentReport.vue`：论文实验静态表
- `frontend/src/views/ai/RealExperimentReport.vue`：真实滚动回测
- `scripts/paper/run_paper_repro_eval.py`、`scripts/paper/run_narrative_offline.py`：复现脚本

`docs/archive/旧原型/` 里的 `smart_replenishment.py`、`predictive_maintenance.py` 是历史原型，**不是**当前生产入口。正式路径是 `python-ai-service/`。PHM 遗留桥接仍从该归档目录加载。

---

## 贡献指南

1. 创建功能分支：`feature/xxx` 或 `fix/xxx`。
2. 提交信息建议：`feat:` / `fix:` / `refactor:` / `docs:` / `test:` / `chore:`。
3. 提交前至少确认：
   - 后端可编译，核心接口可用
   - 前端 `npm run build` 可通过
   - 相关 SQL 与 `function.md` / 文档同步
   - 不要提交真实数据库密码、JWT secret、回调 token
4. PR 请说明变更背景、影响范围、验证方式和回滚策略。

---

## 常见问题

### 1. 前端启动后无法访问后端

- 确认后端已在 `8080` 监听。
- 前端请用 `npm run dev`，代理在 `frontend/vite.config.ts`，不是旧的 `vue.config.js`。
- 检查 `application.yml` 的 `app.cors.allowed-origins` 是否包含 `http://localhost:3000`（端口被占用时脚本会落到 `3001`）。

### 2. 接口返回 403 Forbidden

- 已登录但角色缺少该接口权限。
- 用 `admin` 进入角色管理，勾选对应菜单 / 按钮后重新登录。

### 3. 登录失败

- 已执行 `sql/init.sql`，`user` 表中有 `admin`。
- `DB_USERNAME` / `DB_PASSWORD` 与实际 MySQL 一致。

### 4. 菜单不显示或按钮缺失

- 检查 `menu`、`role_menu`、`user_role`。
- 重新登录以刷新 Pinia / 本地权限缓存。

### 5. AI / 分类任务没有结果

- Python 是否在 **8000** 监听，且与 `AI_PYTHON_BASE_URL` 一致。
- Redis / Celery Worker 是否存活（训练类任务需要）。
- 近 12 个月是否有足够消耗数据。
- 可先调 `/api/ai/forecast/trigger` 或任务中心手动触发排查。

### 6. 单独启动 Python 后 Java 调用失败

- `uvicorn` / supervisord 默认可能是 `8001`，Java 默认打 `8000`。
- 以 `scripts/start_all.sh` 为准，或显式指定 `--port 8000`。

---

首次接手建议按这个闭环验证：`.env.local` → `sql/init.sql` → `scripts/start_all.sh` → 登录 `admin` / `123456` → 走通一条「采购 → 入库 → 上架 → 领用 → 预测结果」路径。更细的模块说明见 [docs/文档导航.md](docs/文档导航.md)。
