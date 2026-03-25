# 朗动备件管理系统（LangDong Spare Management System）

![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.0-6DB33F?logo=springboot&logoColor=white)
![MyBatis](https://img.shields.io/badge/MyBatis-3.0.3-000000?logo=mybatis&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-5.7%2B-4479A1?logo=mysql&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-2.7.14-4FC08D?logo=vue.js&logoColor=white)
![Element UI](https://img.shields.io/badge/Element_UI-2.15.14-409EFF)
![ECharts](https://img.shields.io/badge/ECharts-5.4.3-AA344D)
![License](https://img.shields.io/badge/License-未声明-lightgrey)

一个面向工业场景的备件全生命周期管理系统，覆盖基础档案、仓储、领用、工单、采购、智能分类、AI 预测与报表看板。  
后端采用 Spring Boot + MyBatis，前端采用 Vue 2 + Element UI，支持基于权限的菜单与接口控制。

---

## 目录

- [项目亮点](#项目亮点)
- [技术栈](#技术栈)
- [核心功能](#核心功能)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [关键模块说明](#关键模块说明)
- [开发与调试建议](#开发与调试建议)
- [贡献指南](#贡献指南)
- [常见问题](#常见问题)

---

## 项目亮点

- 支持备件业务闭环：采购申请 -> 采购下单 -> 收货入库 -> 货位上架 -> 领用审批 -> 出库安装 -> 维修工单归档。
- 内置智能分类（ABC/XYZ）与 AI 预测（RF/SBA/Fallback）联动库存阈值策略。
- 支持安全库存（SS）与补货点（ROP）自动计算，并与补货建议联动。
- 提供报表与看板能力，前端集成 ECharts 可视化。
- Spring Security + JWT 鉴权，按菜单/按钮细粒度授权。
- 具备定时任务与异步执行能力（分类与 AI 预测按月调度）。

---

## 技术栈

### 后端

- Java 17
- Spring Boot 3.2.0
- Spring Security
- MyBatis Spring Boot Starter 3.0.3
- MySQL Connector/J
- JJWT 0.11.5
- Smile ML 3.1.0（随机森林）
- Maven

### 前端

- Vue 2.7.14
- Vue Router 3.6.5
- Vuex 3.6.2
- Element UI 2.15.14
- Axios 0.27.2
- ECharts 5.4.3
- xlsx + file-saver（导出能力）
- Vue CLI 5

### 数据库

- MySQL（建议 5.7+）

---

## 核心功能

- 基础数据管理：备件、货位、设备、供应商、供货品类。
- 仓储管理：收货入库、货位上架、库存总台账/货位明细台账。
- 领用管理：申请、审批、出库确认、安装登记、记录查询。
- 维修工单：报修、派工、维修过程、完工确认、工单查询。
- 采购管理：采购申请、采购订单、询报价、验收、智能采购建议。
- 智能分类模块（M6）：
  - ABC/XYZ 分类
  - CV² 计算
  - 安全库存与补货点计算
  - 支持定时重算与手动触发
- AI 智能分析模块（M7）：
  - ADI/CV² 分型
  - SBA（间断需求）
  - Random Forest（规律型需求）
  - Fallback 回退预测
  - MASE 评估与预测区间
  - 库存阈值联动补货建议
- 设备健康预测模块（PHM）：
  - 设备健康评分与风险分级（CRITICAL/HIGH/MEDIUM/LOW）
  - 故障预测（概率、预测窗口、置信区间）
  - 维护建议自动生成，支持采纳/拒绝并联动工单与领用
  - 定时批量评估与手动触发
- 库存出库 FIFO 追溯：出库批次先进先出追溯。
- 报表与看板：库存、领用、维修、供应商、预警中心、Dashboard。

---

## 快速开始

### 1. 环境准备

- JDK 17
- Maven 3.8+
- Node.js 16+
- npm 8+
- MySQL 5.7+

### 2. 初始化数据库

在 MySQL 中执行：

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

可选：导入分类示例数据

```bash
mysql -u root -p < sql/classify_data.sql
```

### 3. 配置后端连接

修改 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/spare_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: <your-db-user>
    password: <your-db-password>

jwt:
  secret: <至少64字符的随机密钥>
  expiration: 86400000

# 允许跨域的前端地址（生产环境请修改为实际域名）
app:
  cors:
    allowed-origin: http://localhost:3000
```

默认后端端口：`8080`

### 4. 启动后端

```bash
cd backend
mvn spring-boot:run
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run serve
```

默认前端地址：`http://localhost:3000`  
开发代理：`/api -> http://localhost:8080`

### 6. 默认账号

- 用户名：`admin`
- 密码：`123456`

---

## 项目结构

```text
langdong/
├── backend/                         # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/langdong/spare/
│       │   ├── config/              # 安全、异步、定时配置
│       │   ├── controller/          # REST 接口
│       │   ├── service/             # 业务服务
│       │   │   └── ai/              # AI 预测与库存阈值联动
│       │   ├── mapper/              # MyBatis Mapper 接口
│       │   ├── entity/              # 实体模型
│       │   ├── dto/                 # 数据传输对象
│       │   └── util/                # 计算与工具类
│       └── resources/
│           ├── application.yml
│           └── mapper/*.xml         # MyBatis SQL
├── frontend/                        # Vue 2 前端
│   ├── package.json
│   ├── vue.config.js
│   └── src/
│       ├── router/                  # 路由
│       ├── store/                   # Vuex
│       ├── utils/                   # 请求封装
│       └── views/                   # 页面（分类、AI、仓储、领用、工单、采购、报表等）
├── sql/                             # 初始化与模块迁移 SQL
├── docs/                            # 文档与论文材料
├── function.md                      # 功能迭代记录
└── README.md
```

---

## 关键模块说明

### AI 智能分析（`backend/src/main/java/com/langdong/spare/service/ai`）

- `AiFeatureService`：构建近 12 个月需求序列，计算 ADI/CV²，并做算法分型。
- `SbaForecastServiceImpl`：实现 SBA 间断需求预测。
- `RandomForestServiceImpl`：基于 Smile 的随机森林回归预测。
- `AbstractForecastAlgorithm`：统一封装 MASE 计算与 Fallback 回退逻辑。
- `StockThresholdService`：根据预测区间与提前期重算 SS/ROP 并触发补货建议。

### 智能分类（`ClassifyService` + `ClassifyCalculator`）

- 计算 ABC/XYZ 分类
- 计算 CV²、安全库存、补货点
- 支持月度定时重算与手动触发

---

## 开发与调试建议

### 后端常用命令

```bash
cd backend
mvn clean package
mvn spring-boot:run
mvn test
```

### 前端常用命令

```bash
cd frontend
npm install
npm run serve
npm run build
```

### 接口联调

- 前端请求统一走 `/api`
- `vue.config.js` 已配置开发代理到 `http://localhost:8080`
- 若出现 401，请检查 JWT 与权限菜单配置是否一致

---

## 贡献指南

1. Fork 本仓库并创建功能分支：
   - `feature/xxx`
   - `fix/xxx`
2. 保持提交粒度清晰，提交信息建议使用：
   - `feat: ...`
   - `fix: ...`
   - `refactor: ...`
   - `docs: ...`
3. 提交前请至少完成：
   - 后端可编译、核心接口可用
   - 前端可构建、主要页面可访问
   - 相关 SQL 迁移脚本与文档同步更新
4. 发起 Pull Request，说明：
   - 变更背景
   - 影响范围
   - 验证方式
   - 回滚策略（如涉及结构变更）

---

## 常见问题

### 1. 前端启动后无法访问后端接口

- 检查后端是否已启动在 `8080`
- 检查 `frontend/vue.config.js` 中代理目标地址
- 检查浏览器网络面板是否出现跨域或 401
- 检查 `application.yml` 中 `app.cors.allowed-origin` 是否与前端地址一致

### 2. 接口返回 403 Forbidden

- 说明当前用户缺少该接口所需的权限（`@PreAuthorize` 校验失败）
- 登录管理员账号，进入"角色与权限分配"，确认该角色已勾选对应菜单/按钮权限
- 重新登录后前端会刷新权限缓存

### 3. 登录失败

- 确认已执行 `sql/init.sql`
- 确认 `user` 表存在 `admin` 账号
- 确认数据库连接配置正确

### 4. 菜单不显示或权限按钮缺失

- 检查 `menu`、`role_menu`、`user_role` 数据是否完整
- 重新登录以刷新前端动态路由与权限缓存

### 5. AI/分类任务未出结果

- 检查定时任务是否启用（`@EnableScheduling`）
- 检查历史业务数据是否足够（尤其是近 12 个月消耗）
- 可使用手动触发接口进行排查

--- 

> 如果你是首次接手该项目，建议先从 `sql/init.sql` + `backend/application.yml` + `frontend/vue.config.js` 三个文件完成环境闭环，再逐模块验证业务流程。
