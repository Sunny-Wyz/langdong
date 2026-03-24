# 备件管理系统 (Spare Parts Management System)

## Project Overview

A full-stack industrial spare parts management system with integrated AI/ML capabilities for predictive maintenance and smart inventory optimization.

- **Backend**: Spring Boot 3.2 + MyBatis + Spring Security + JWT | Java 17 | MySQL 8
- **Frontend**: Vue 2 + Element UI + Vuex + Vue Router + ECharts | Node/npm
- **ML**: Random Forest (Smile 3.1) + SBA forecasting algorithms

## Repository Structure

```
backend/                    # Spring Boot application (Maven)
frontend/                   # Vue 2 SPA (Vue CLI)
sql/                        # Database initialization and migration scripts (20+ files)
docs/                       # Documentation, ER diagrams, research papers
scripts/                    # Utility shell scripts
predictive_maintenance.py   # Python prototype for predictive maintenance
smart_replenishment.py      # Python prototype for replenishment optimization
test_plan.jmx               # JMeter performance test plans
jmeter_report*/             # Performance test reports
QA.md                       # Q&A and bug resolution log
function.md                 # Feature development log
```

## Development Setup

### Database
```bash
# Initialize schema
mysql -u root -p < sql/init.sql
# Load test data (optional)
mysql -u root -p spare_db < sql/mock_data.sql
```

### Backend
```bash
# Edit backend/src/main/resources/application.yml — set DB credentials if needed
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

Default credentials: `admin` / `123456`

---

## Backend Architecture

**Root Package**: `com.langdong.spare`
**Entry Point**: `SpareApplication.java` (with `@MapperScan("com.langdong.spare.mapper")`)

### Layer Map

| Layer | Location | Count |
|---|---|---|
| Controllers | `controller/` | 26 REST controllers |
| Entities | `entity/` | 33 JPA/MyBatis models |
| DTOs | `dto/` | 30+ request/response objects |
| Services | `service/` + `service/ai/` | 14 services + 6 AI services |
| Mappers | `mapper/` | 35 MyBatis interfaces + XML files |
| Config | `config/` | SecurityConfig, AsyncScheduleConfig |
| Utils | `util/` | JwtUtil, ClassifyCalculator, FaultPredictionEngine, DeviceHealthCalculator, MaintenanceSuggestionGenerator |

### Controllers (26 files)

| Controller | Responsibility |
|---|---|
| `AuthController` | Login/logout, JWT issuance |
| `UserController`, `RoleController`, `MenuController` | RBAC user management |
| `SparePartController`, `SparePartCategoryController` | Spare part master data |
| `EquipmentController`, `LocationController` | Equipment & warehouse locations |
| `SupplierController`, `SupplyCategory Controller` | Supplier management |
| `StockInController`, `StockLedgerController`, `ShelvingController` | Inbound & shelving |
| `RequisitionController`, `WorkOrderController` | Requisition & maintenance workflow |
| `PurchaseOrderController`, `ReorderSuggestController` | Procurement |
| `ClassifyController` | ABC/XYZ smart classification |
| `PredictionController`, `AiForecastController` | ML fault prediction & demand forecasting |
| `HealthController`, `WarningController` | Device health monitoring |
| `OutboundBatchTraceController` | FIFO outbound tracking |
| `ReportController`, `SuggestionController` | Reports & maintenance suggestions |

### Key Entities

| Entity | Key Fields |
|---|---|
| `SparePart` | id, code (8-digit), name, model, quantity, unit, price, category_id, supplier_id, location_id, is_critical, replace_diff |
| `SparePartCategory` | id, code (hierarchical), name, parent_id (2-level hierarchy) |
| `Equipment` | id, code, name |
| `Location` | id, code, name, zone (1–12), capacity |
| `Requisition` / `WorkOrder` | Full lifecycle with status transitions |
| `PurchaseOrder` | Multi-step procurement workflow |
| `DeviceHealth` | Score, health config parameters |
| `FaultPrediction` | Random Forest prediction results |
| `AiForecastResult` | SBA/Random Forest demand forecast |
| `PartClassify` | ABC/XYZ classification output |

### Security & Auth

- **Config**: `SecurityConfig.java` — BCrypt passwords, stateless JWT sessions, CORS `*`
- **JWT**: `JwtUtil` — secret key in `application.yml`, 24h expiry
- **All routes** require `Authorization: Bearer <token>` except `/api/auth/**`
- **Method-level security** enabled (`@PreAuthorize`)

### Key Config (`application.yml`)

```yaml
server.port: 8080
spring.datasource.url: jdbc:mysql://localhost:3306/spare_db
spring.datasource.username: admin
spring.datasource.password: 123456  # DO NOT commit real passwords
mybatis.mapper-locations: classpath:mapper/*.xml
mybatis.type-aliases-package: com.langdong.spare.entity
jwt.secret: spare-management-system-secret-key-2024-langdong
jwt.expiration: 86400000  # 24 hours in ms
```

### AI/ML Services (`service/ai/`)

- `RandomForestServiceImpl` — Uses Smile 3.1 ML library for fault prediction
- `SbaForecastServiceImpl` — Single-period Bayesian forecasting for demand
- `AiFeatureService`, `AiForecastService` — Feature extraction and forecast orchestration
- `StockThresholdService` — Dynamic reorder point calculation
- `AbstractForecastAlgorithm` — Base class for forecast algorithms

---

## Frontend Architecture

**Stack**: Vue 2.7.14, Element UI 2.15.14, Vuex 3.6.2, Vue Router 3.6.5, Axios 0.27.2, ECharts 5.4.3

### Directory Structure

```
src/
├── main.js              # App bootstrap (Vue, Element UI, Router, Store)
├── App.vue              # Root component
├── router/index.js      # 30+ routes with auth guards
├── store/index.js       # Vuex (token, username, menus, permissions)
├── utils/request.js     # Axios instance with JWT + 401 handling
└── views/
    ├── Login.vue
    ├── Home.vue
    ├── SparePartList.vue, LocationProfile.vue, EquipmentProfile.vue
    ├── SupplierProfile.vue, SupplyCategory.vue
    ├── requisition/     # Apply, Approval, Outbound, Install, Query
    ├── warehouse/       # StockIn, Shelving, StockLedger
    ├── workorder/       # Report, Assign, Process, Complete, Query
    ├── purchase/        # Suggestions, Apply, Quote, Orders, Acceptance
    ├── report/          # Dashboard, Inventory, Consumption, Supplier, Maintenance, WarningCenter
    ├── phm/             # HealthMonitor, FaultPrediction, MaintenanceSuggestion
    ├── classify/        # ClassifyResult (ABC/XYZ)
    ├── ai/              # AiForecastResult
    └── sys/             # UserManage, RoleManage
```

### Routing & State

- Root `/` redirects to `/login`
- All non-login routes: `meta: { requiresAuth: true }` — guard checks localStorage token
- **Vuex state**: `token`, `username`, `menus[]`, `permissions[]` (all persisted to localStorage)
- **Mutations**: `SET_TOKEN`, `SET_MENUS_AND_PERMISSIONS`, `LOGOUT`

### HTTP Client (`utils/request.js`)

- Base URL: `/api` (dev-proxied to `http://localhost:8080`)
- Timeout: 5000ms
- Request interceptor injects JWT from localStorage
- Response interceptor: on 401, clears token + redirects to `/login`

---

## Database Schema (`spare_db`)

All tables use UTF-8 MB4, timezone Asia/Shanghai.

### Core Tables

| Table | Purpose |
|---|---|
| `user`, `role`, `menu` | RBAC — users, roles, menu/button permissions |
| `user_role`, `role_menu` | M2M join tables |
| `spare_part`, `spare_part_category` | Spare parts master + 2-level category |
| `spare_part_stock` | Aggregate inventory levels |
| `spare_part_location_stock` | Per-location inventory |
| `location` | Warehouse zones (1–12) |
| `equipment`, `equipment_spare_part` | Equipment & part associations |
| `supplier`, `supplier_quote`, `supply_category` | Supplier & pricing |
| `stock_in_receipt`, `stock_in_item` | Inbound receipts |
| `requisition`, `requisition_item` | Withdrawal requests |
| `work_order` | Maintenance work orders |
| `purchase_order`, `purchase_order_item` | Procurement orders |
| `outbound_batch_trace` | FIFO traceability |
| `reorder_suggest` | Auto-generated reorder suggestions |

### AI/Smart Tables

| Table | Purpose |
|---|---|
| `part_classify` | ABC/XYZ classification results |
| `device_health`, `health_config` | Health scores & config params |
| `fault_prediction` | Random Forest fault predictions |
| `ai_device_feature` | Feature vectors for ML |
| `ai_forecast_result` | Demand forecast outputs |
| `maintenance_suggestion` | Auto-generated maintenance tasks |

### SQL Migration Files (`sql/`)

| File | Purpose |
|---|---|
| `init.sql` | Main schema |
| `mock_data.sql` | Sample data |
| `phm_module.sql` | PHM tables |
| `classify_module.sql` | Classification tables |
| `requisition_module.sql`, `work_order_module.sql`, `purchase_module.sql` | Process modules |
| `ai_module.sql` | AI forecast tables |
| `fifo_migration_v1.sql` | FIFO migration |
| `fix_*.sql` | Bug fix migrations |
| `seed_ai_*.sql` | AI training/seed data |

---

## Common Commands

```bash
# Backend
cd backend && mvn spring-boot:run          # Start dev server
cd backend && mvn clean package            # Build JAR
cd backend && mvn test                     # Run unit tests

# Frontend
cd frontend && npm run serve               # Start dev server (port 3000)
cd frontend && npm run build               # Production build

# Database
mysql -u root -p < sql/init.sql            # Fresh schema init
```

---

## Key Conventions

- **FIFO inventory**: Outbound always uses oldest stock first; traced via `outbound_batch_trace`
- **Spare part codes**: 8-digit numeric codes
- **Category codes**: Hierarchical codes (e.g., `01`, `0101`)
- **Location zones**: Numeric 1–12
- **Status fields**: Numeric enums on most entities (order/requisition/workorder states)
- **All dates**: Stored as `datetime`, displayed in Asia/Shanghai timezone
- **Excel support**: EasyExcel (backend) + xlsx/file-saver (frontend) for import/export
- **ECharts**: Used in Dashboard and report views for visualizations

---

## AI Assistant Guidelines (Memory)

- **Language Preference**: 以后的所有工作计划 (工作计划, implementation plans, tasks) 都必须用中文写。
- **记录问答与解决方案**: 遇到报错、Bug 排查及问题解决时，自动使用 `Q - 问题 - 解决方案` 的格式将记录写入到 `QA.md` 中（项目根目录）。
- **记录新增功能点**: 开发、修改或增强新的业务功能时，自动使用 `F - 功能描述 - 落实情况` 的格式将记录补充写入到 `function.md` 中（项目根目录）。
- **Do not commit DB passwords** — `application.yml` should always use placeholder values.
- **Security**: All protected API routes require JWT Bearer token; never expose auth bypass.
