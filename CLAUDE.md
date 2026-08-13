# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# 备件管理系统 (Spare Parts Management System)

## Project Overview

A full-scale enterprise spare parts lifecycle management platform with integrated AI/ML for predictive maintenance and demand forecasting.

- **Backend**: Spring Boot 3.2.0 + MyBatis 3.0.3 + Spring Security + JWT | Java 21 | MySQL 5.7+
- **Frontend**: Vue 3.4 + TypeScript + Vite + Element Plus + Pinia + Vue Router 4 | Node/npm
- **AI Service**: Python FastAPI + XGBoost（两阶段 Hurdle-Gamma）+ PyTorch + MLflow

## Repository Structure

```
backend/              # Spring Boot REST API（约 198 个 Java 文件）
frontend/             # Vue 3 SPA（约 44 个视图）
python-ai-service/    # FastAPI AI 预测微服务
sql/                  # 建表、迁移、种子 SQL
docs/                 # 现行文档见 docs/文档导航.md；草稿与旧原型在 docs/archive/
jmeter/               # 压测计划与报告
scripts/              # start_all.sh 等；论文脚本在 scripts/paper/
```

## Development Setup

### Database
```bash
# Start local MySQL 5.7 Server (Anaconda version)
/opt/anaconda3/bin/mysql.server start

# Initialize core schema
mysql -u root -p < sql/init.sql

# Apply module-specific schemas
mysql -u root -p spare_db < sql/classify_module.sql
mysql -u root -p spare_db < sql/ai_module.sql
mysql -u root -p spare_db < sql/phm_module.sql
mysql -u root -p spare_db < sql/requisition_module.sql
mysql -u root -p spare_db < sql/work_order_module.sql
mysql -u root -p spare_db < sql/purchase_module.sql

# Optional: load mock data
mysql -u root -p spare_db < sql/mock_data.sql
```

### Backend
```bash
# Edit backend/src/main/resources/application.yml — set DB credentials
cd backend
mvn spring-boot:run
# Runs on http://localhost:8080
```

### Frontend
```bash
cd frontend
npm install
npm run dev
# Runs on http://localhost:3000 (proxies /api to localhost:8080)
```

### Python AI Service
```bash
cd python-ai-service
pip install -r requirements.txt
# 端口必须与 Java ai.python.base-url 一致；一键脚本打 8000
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

推荐本地：配置根目录 `.env.local` 后执行 `./scripts/start_all.sh`。

Default credentials: `admin` / `123456`

---

## Backend Architecture

**Root package**: `com.langdong.spare`

### Layer Map

| Layer | Location | Count |
|---|---|---|
| Controllers | `controller/` | 29 classes |
| Services | `service/` + `service/ai/` + `forecast/` | 业务编排 + 月度预测管线 |
| Entities | `entity/` | 36 classes |
| DTOs | `dto/` | 31 classes |
| Mappers | `mapper/` | 39 interfaces + 39 XML |
| Config | `config/` | SecurityConfig, AsyncScheduleConfig, PythonClientConfig |
| Utils | `util/` | JwtUtil, DeviceHealthCalculator, FaultPredictionEngine, etc. |

### Controllers（29 个，前缀以代码为准）

| Controller | Endpoints | Purpose |
|---|---|---|
| `AuthController` | `/api/auth/**` | 登录 |
| `HealthController` | `/api/phm/health/**` | PHM 健康 |
| `PredictionController` | `/api/phm/prediction/**` | 故障预测 |
| `SuggestionController` | `/api/phm/suggestion/**` | 维护建议 |
| `AiForecastController` | `/api/ai/forecast/**` | 月度预测触发 / 查询 |
| `AiForecastJobController` | `/api/ai/forecast/jobs/**` | 任务中心 |
| `RealExperimentController` | `/api/ai/experiment/**` | 真实滚动回测 |
| `ClassifyController` | `/api/classify/**` | ABC/XYZ |
| `EquipmentController` | `/api/equipments/**` | 设备档案 |
| `SparePartController` | `/api/spare-parts/**` | 备件档案 |
| `LocationController` | `/api/locations/**` | 货位 |
| `StockInController` | `/api/stock-in/**` | 入库 |
| `StockLedgerController` | `/api/stock-ledger/**` | 台账 |
| `ShelvingController` | `/api/shelving/**` | 上架 |
| `RequisitionController` | `/api/requisitions/**` | 领用 |
| `WorkOrderController` | `/api/work-orders/**` | 工单 |
| `PurchaseOrderController` | `/api/purchase-orders/**` | 采购 |
| `ReorderSuggestController` | `/api/reorder-suggests/**` | 补货建议 |
| `MenuController` | `/api/menus/**` | 菜单 |
| `ReportController` | `/api/report/**` | 报表 |
| `WarningController` | `/api/warnings/**` | 预警 |
| `PythonCallbackController` | `/api/ai/forecast/callback/python/**` | Python 回调 |
| `InternalAiDataController` | `/internal/ai/**` | 给 Python 拉数 |
| `OutboundBatchTraceController` | `/api/outbound-trace/**` | FIFO 追溯 |

### 预测主链路（`forecast/`，不是旧 RF/SBA）

| 类 | 职责 |
|---|---|
| `MonthlyForecastScheduler` | 月初重算下月 |
| `HurdleGammaJobService` | 任务中心异步任务 |
| `StockThresholdService` | 调 Python 训练/推理 + 落库 SS/ROP |
| `LeadTimeDemandSimulator` | 调 `/api/algorithm/inventory-calc` |
| `AiForecastService` | 仅查询结果 + 回调落库 |
| `PythonModelClient` | Java → Python HTTP |

**`algo_type`**：
- `TWO_STAGE` — 两阶段 Hurdle-Gamma（生产主算法）
- `FALLBACK` — 数据不足回退

旧 `AiFeatureService` / `RandomForestServiceImpl` / `SbaForecastServiceImpl` 已删除。算法说明见 `docs/AI_ALGORITHMS/两阶段Hurdle-Gamma与蒙特卡洛.md`。

### Key Config (`application.yml`)

```yaml
server.port: 8080
spring.datasource: jdbc:mysql://localhost:3306/spare_db
  username: ${DB_USERNAME:admin}
  password: ${DB_PASSWORD:123456}
mybatis.mapper-locations: classpath:mapper/*.xml
jwt.secret: ${JWT_SECRET:spare-management-system-secret-key-2024-langdong}
jwt.expiration: 86400000  # 24 hours
ai.python.base-url: http://localhost:8000
ai.python.callback-token: ${PYTHON_CALLBACK_TOKEN}
```

### Backend Patterns & Conventions

- **API response format**: `{ code: 200, message: "...", data: {...}, total: N, page: N }`
- **Pagination**: `page` + `pageSize` query parameters
- **Security**: JWT filter (`OncePerRequestFilter`) + `@PreAuthorize("hasAuthority('...')")` on methods
- **Scheduled tasks**: `@Scheduled(cron = "...")` for nightly evaluations
- **Async processing**: `@Async` for batch/long-running operations
- **Entities**: Use Lombok `@Data`, `LocalDate`/`LocalDateTime`, `BigDecimal` for scores
- **Mappers**: Parameterized with `@Param`, batch inserts via `insertBatch`, complex JOINs in XML
- **Do not commit** real DB passwords or JWT secrets — use env vars or placeholders

---

## Python AI Service Architecture

**Root**: `python-ai-service/app/`

| Directory | Purpose |
|---|---|
| `api/v1/` | FastAPI routers (replenishment, forecast, etc.) |
| `models/` | ML models: demand forecasting, feature engineering, RUL prediction |
| `services/` | Async tasks (Celery), Java data client, task registry |
| `schemas.py` | Pydantic request/response models |

- **ML Stack**: PyTorch (TFT/deep learning), scikit-learn (classical ML), XGBoost (gradient boosting)
- **MLflow**: Experiment tracking at `mlruns/`, logs metrics/params/artifacts
- **Async**: Celery + Redis for long-running training jobs, results stored via `task_registry`
- **Data flow**: Java `StockThresholdService` 批量调用 `/api/algorithm/train` 与 `/predict`，库存走 `/api/algorithm/inventory-calc`；异步回调走 `/api/ai/forecast/callback/python`

---

## Frontend Architecture

**Stack**: Vue 3.4 + TypeScript + Vite + Element Plus + Pinia + Vue Router 4 + Axios + ECharts 5.4.3

### Key Files

| File | Purpose |
|---|---|
| `src/main.ts` | Vue 3 + Pinia + Element Plus |
| `src/App.vue` | Root component |
| `src/router/index.ts` | Hash 路由 + 登录守卫 |
| `src/store/auth.ts` | Pinia：token、菜单、权限 |
| `src/utils/request.ts` | Axios，注入 Bearer，401 刷新 |
| `src/styles/reference-theme.css` | Element Plus 主题覆盖 |
| `vite.config.ts` | 开发端口 3000，代理 `/api` → `8080` |

### Views Directory

```
src/views/
├── Login.vue                    # Auth
├── Home.vue                     # Main layout
├── SparePartList.vue            # Spare part catalog
├── LocationProfile.vue          # Warehouse locations
├── EquipmentProfile.vue         # Device profiles
├── SupplierProfile.vue          # Suppliers
├── SupplyCategory.vue           # Supply categories
│
├── warehouse/                   # Inventory management
│   ├── StockInManage.vue
│   ├── StockLedger.vue
│   └── LocationShelving.vue
│
├── requisition/                 # 4-stage workflow
│   ├── RequisitionApply.vue     # DRAFT → PENDING
│   ├── RequisitionApproval.vue  # PENDING → APPROVED
│   ├── RequisitionOutbound.vue  # APPROVED → OUTBOUND
│   ├── RequisitionInstall.vue   # OUTBOUND → INSTALLED
│   └── RequisitionQuery.vue
│
├── workorder/                   # 4-stage workflow
│   ├── WorkOrderReport.vue      # REPORTED
│   ├── WorkOrderAssign.vue      # ASSIGNED
│   ├── WorkOrderProcess.vue     # IN_PROGRESS
│   ├── WorkOrderComplete.vue    # COMPLETED
│   └── WorkOrderQuery.vue
│
├── purchase/                    # Purchase pipeline
│   ├── PurchaseSuggestions.vue  # AI recommendations
│   ├── PurchaseApply.vue
│   ├── PurchaseQuote.vue
│   ├── PurchaseOrders.vue
│   └── PurchaseAcceptance.vue
│
├── report/                      # Analytics
│   ├── Dashboard.vue
│   ├── InventoryReport.vue
│   ├── ConsumptionReport.vue
│   ├── SupplierReport.vue
│   ├── MaintenanceReport.vue
│   └── WarningCenter.vue
│
├── classify/
│   └── ClassifyResult.vue       # ABC/XYZ results
│
├── phm/                         # Predictive maintenance
│   ├── HealthMonitor.vue        # ECharts health dashboard
│   ├── FaultPrediction.vue      # Fault forecasts
│   └── MaintenanceSuggestion.vue # Adopt/reject suggestions
│
├── ai/                          # AI analysis
│   ├── AiForecastResult.vue
│   ├── AiJobCenter.vue
│   ├── PaperExperimentReport.vue
│   └── RealExperimentReport.vue
│
└── sys/                         # System management
    ├── UserManage.vue
    └── RoleManage.vue
```

### Frontend Patterns & Conventions

- **Auth**: Token stored in `localStorage` / Pinia，请求自动带 Bearer
- **Permissions**: 动态菜单来自 `authStore.permissions[]`
- **Route guards**: `router/index.ts` 检查登录；任务中心额外校验权限
- **Charts**: ECharts 5
- **Forms**: Element Plus
- **Actions**: `el-dialog` 弹窗

---

## Database Schema

### Core Tables (init.sql)

| Table | Purpose |
|---|---|
| `user`, `role`, `menu`, `user_role`, `role_menu` | RBAC authorization |
| `spare_part_category`, `spare_part` | Parts catalog |
| `location` | Warehouse locations |
| `equipment`, `equipment_spare_part` | Device-parts relationship |
| `supplier`, `supplier_category_relation`, `supplier_quote` | Supplier management |

### Module Tables

| Module | Tables | SQL File |
|---|---|---|
| Smart Classification | `part_classify` | `classify_module.sql` |
| AI Forecasting | `ai_device_feature`, `ai_forecast_result`, `ai_train_data_record` | `ai_module.sql` |
| PHM | `ai_device_health`, `ai_fault_prediction`, `biz_maintenance_suggestion`, `sys_device_health_config` | `phm_module.sql` |
| Requisitions | `requisition`, `requisition_item` | `requisition_module.sql` |
| Work Orders | `work_order`, `work_order_item` | `work_order_module.sql` |
| Purchase | `purchase_order`, `purchase_order_item`, `purchase_requisition` | `purchase_module.sql` |
| FIFO Tracing | `outbound_batch_trace` | `fifo_migration_v1.sql` |

### Database Conventions

- `snake_case` column names
- Table prefixes: `ai_*` (AI/ML), `biz_*` (business), `sys_*` (system)
- Spare part codes: 8-digit format; category codes: `X.XXX` format
- `DECIMAL(5,2)` for scores/percentages; `JSON` columns for flexible structures
- All tables have `created_at`, `updated_at` audit columns

---

## Common Commands

```bash
# Backend
cd backend && mvn spring-boot:run        # Start dev server
cd backend && mvn clean package          # Build JAR
cd backend && mvn test                   # Run all tests
cd backend && mvn test -Dtest=ClassifyCalculatorTest  # Run single test class
cd backend && mvn test -Dtest=ClassifyCalculatorTest#testMethod  # Run single method

# Frontend
cd frontend && npm run dev               # Vite 开发服务器（端口 3000）
cd frontend && npm run build             # vue-tsc + vite build

# Python AI service
cd python-ai-service && uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

## Key Notes

- JWT tokens stored client-side, sent as `Authorization: Bearer <token>`
- All API routes (except `/api/auth/**`) require authentication
- Frontend dev server proxies `/api` → `http://localhost:8080`
- Python AI service 本地默认 `http://localhost:8000`（与 `AI_PYTHON_BASE_URL` 对齐）
- Do not commit real DB passwords, JWT secrets, or callback tokens
- PHM scheduled evaluations run nightly via `@Scheduled` in `PhmOrchestrationService`
- FIFO outbound tracing handled by `FifoOutboundService` + `OutboundBatchTraceController`

---

## Project Skills（`.grok/skills/`）

| Skill | 何时加载 |
|---|---|
| `langdong-conventions` | 任何本仓库改动 |
| `langdong-forecast` | 预测 / SS / ROP / 蒙特卡洛 |
| `langdong-docs` | 写或改 markdown 正式文档 |

---

## AI Assistant Guidelines (Memory)

- **Language Preference**: 以后的所有工作计划 (工作计划, implementation plans, tasks) 都必须用中文写。
- **记录问答与解决方案**: 遇到报错、Bug 排查及问题解决时，自动使用 `Q - 问题 - 解决方案` 的格式将记录写入到 `QA.md` 中。
- **记录新增功能点**: 开发、修改或增强新的业务功能时，自动使用 `F - 功能描述 - 落实情况` 的格式将记录补充写入到 `function.md` 中。
