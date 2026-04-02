# 备件管理系统 (Spare Parts Management System)

## Project Overview

A full-scale enterprise spare parts lifecycle management platform with integrated AI/ML for predictive maintenance and demand forecasting.

- **Backend**: Spring Boot 3.2.0 + MyBatis 3.0.3 + Spring Security + JWT | Java 17 | MySQL 5.7+
- **Frontend**: Vue 2.7.14 + Element UI 2.15.14 + Vuex 3.6.2 + Vue Router 3.6.5 | Node/npm
- **AI Service**: Python FastAPI + PyTorch + scikit-learn + XGBoost + MLflow

## Repository Structure

```
backend/              # Spring Boot REST API (163 Java files, ~10,492 LOC)
frontend/             # Vue 2 SPA (19 main views)
python-ai-service/    # FastAPI AI prediction microservice
sql/                  # Database schemas & migrations (27 SQL files)
docs/                 # Technical documentation (13 subdirectories)
jmeter/               # Performance test reports
scripts/              # Utility scripts
plan/                 # Project planning documents
```

## Development Setup

### Database
```bash
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
npm run serve
# Runs on http://localhost:3000 (proxies /api to localhost:8080)
```

### Python AI Service
```bash
cd python-ai-service
pip install -r requirements.txt
cp .env.example .env  # configure DB + API tokens
uvicorn main:app --port 8001
# Runs on http://localhost:8001
```

Default credentials: `admin` / `123456`

---

## Backend Architecture

**Root package**: `com.langdong.spare`

### Layer Map

| Layer | Location | Count |
|---|---|---|
| Controllers | `controller/` | 23 classes |
| Services | `service/` + `service/ai/` | 14 + 8 classes |
| Entities | `entity/` | 28 classes |
| DTOs | `dto/` | 35+ classes |
| Mappers | `mapper/` | 31 interfaces + 38 XML files |
| Config | `config/` | SecurityConfig, AsyncScheduleConfig, PythonClientConfig |
| Utils | `util/` | JwtUtil, DeviceHealthCalculator, FaultPredictionEngine, etc. |

### Controllers (23 total)

| Controller | Endpoints | Purpose |
|---|---|---|
| `AuthController` | `/api/auth/**` | Login / logout |
| `HealthController` | `/api/phm/health/**` | PHM health monitoring (6 endpoints) |
| `PredictionController` | `/api/phm/prediction/**` | Device fault prediction (6 endpoints) |
| `SuggestionController` | `/api/phm/suggestion/**` | Maintenance suggestions (7 endpoints) |
| `AiForecastController` | `/api/ai/forecast/**` | AI forecast results |
| `AiForecastJobController` | `/api/ai/job/**` | AI job scheduling |
| `AiTrainDataController` | `/api/ai/train-data/**` | Training data management |
| `ClassifyController` | `/api/classify/**` | ABC/XYZ classification |
| `EquipmentController` | `/api/equipment/**` | Equipment profiles |
| `SparePartController` | `/api/spare-part/**` | Spare part catalog |
| `LocationController` | `/api/location/**` | Warehouse locations |
| `StockInController` | `/api/stock-in/**` | Receiving / inventory |
| `StockLedgerController` | `/api/stock-ledger/**` | Stock ledger |
| `ShelvingController` | `/api/shelving/**` | Location shelving |
| `RequisitionController` | `/api/requisition/**` | Requisition workflow |
| `WorkOrderController` | `/api/work-order/**` | Maintenance work orders |
| `PurchaseOrderController` | `/api/purchase/**` | Purchase orders |
| `ReorderSuggestController` | `/api/reorder/**` | Reorder suggestions |
| `MenuController` | `/api/menu/**` | Menu & permissions |
| `ReportController` | `/api/report/**` | Reports & dashboards |
| `WarningController` | `/api/warning/**` | Alerts & warnings |
| `PythonCallbackController` | `/api/python/callback/**` | AI service callbacks |
| `OutboundBatchTraceController` | `/api/outbound-trace/**` | FIFO tracing |

### AI Services (`service/ai/`)

| Service | Algorithm |
|---|---|
| `AiForecastService` | Orchestrates demand forecasting pipeline |
| `AiFeatureService` | Feature engineering (12-month rolling demand) |
| `SbaForecastServiceImpl` | SBA algorithm for intermittent demand |
| `RandomForestServiceImpl` | Random Forest regression (Smile ML 3.1.0) |
| `AbstractForecastAlgorithm` | MASE scoring + fallback strategy |
| `StockThresholdService` | Safety stock / reorder point calculation |
| `PythonModelClient` | HTTP client calling Python AI service |
| `PythonCallbackStoreService` | Stores async Python predictions |

### Key Config (`application.yml`)

```yaml
server.port: 8080
spring.datasource: jdbc:mysql://localhost:3306/spare_db
  username: ${DB_USERNAME:admin}
  password: ${DB_PASSWORD:123456}
mybatis.mapper-locations: classpath:mapper/*.xml
jwt.secret: ${JWT_SECRET:spare-management-system-secret-key-2024-langdong}
jwt.expiration: 86400000  # 24 hours
ai.python.base-url: http://localhost:8001
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

## Frontend Architecture

**Stack**: Vue 2.7.14, Element UI, Vuex, Vue Router, Axios, ECharts 5.4.3

### Key Files

| File | Purpose |
|---|---|
| `src/main.js` | Entry point — Vue + ElementUI + axios init |
| `src/App.vue` | Root component |
| `src/router/index.js` | Route definitions with auth guard + permission checks |
| `src/store/index.js` | Vuex: token, username, menus[], permissions[] |
| `src/utils/request.js` | Axios wrapper — auto-injects `Authorization: Bearer <token>` |
| `src/styles/reference-theme.css` | Custom Element UI theme overrides |
| `vue.config.js` | Dev server (port 3000), proxy `/api` → `http://localhost:8080` |

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
│   └── AiTrainDataDashboard.vue
│
└── sys/                         # System management
    ├── UserManage.vue
    └── RoleManage.vue
```

### Frontend Patterns & Conventions

- **Auth**: Token stored in `localStorage`, auto-injected into every request
- **Permissions**: Dynamic menu routes generated from Vuex `permissions[]`
- **Route guards**: Check auth + permission on every navigation
- **Charts**: ECharts 5 with graceful degradation if no data
- **Forms**: Element UI form components with validation rules
- **Actions**: Modal dialogs (`el-dialog`) for create/edit/delete

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
cd backend && mvn test                   # Run tests

# Frontend
cd frontend && npm run serve             # Start dev server (port 3000)
cd frontend && npm run build             # Production build

# Python AI service
cd python-ai-service && uvicorn main:app --port 8001 --reload
```

---

## Key Notes

- JWT tokens stored client-side, sent as `Authorization: Bearer <token>`
- All API routes (except `/api/auth/**`) require authentication
- Frontend dev server proxies `/api` → `http://localhost:8080`
- Python AI service at `http://localhost:8001`, called via `PythonModelClient`
- Do not commit real DB passwords, JWT secrets, or callback tokens
- PHM scheduled evaluations run nightly via `@Scheduled` in `PhmOrchestrationService`
- FIFO outbound tracing handled by `FifoOutboundService` + `OutboundBatchTraceController`

---

## AI Assistant Guidelines (Memory)

- **Language Preference**: 以后的所有工作计划 (工作计划, implementation plans, tasks) 都必须用中文写。
- **记录问答与解决方案**: 遇到报错、Bug 排查及问题解决时，自动使用 `Q - 问题 - 解决方案` 的格式将记录写入到 `QA.md` 中。
- **记录新增功能点**: 开发、修改或增强新的业务功能时，自动使用 `F - 功能描述 - 落实情况` 的格式将记录补充写入到 `function.md` 中。
