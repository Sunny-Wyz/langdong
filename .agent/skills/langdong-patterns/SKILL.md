---
name: langdong-patterns
description: 备件管理系统（langdong）项目编码规范与开发模式，从 Git 历史中提取
version: 1.0.0
source: local-git-analysis
analyzed_commits: 50
---

# 备件管理系统 — 项目开发模式

## 提交消息规范

提交信息以中文为主，遵循以下约定：

| 类型 | 中文关键词 | 说明 |
|------|-----------|------|
| feat | 新增/增加/搭建 | 新功能模块 |
| fix  | 修复/bug修复 | Bug 修复 |
| docs | 更新/er图/readme | 文档更新 |
| refactor | 重构 | 代码重构 |
| chore | 初始化 | 项目初始化、配置 |

示例：
- `新增货位上架功能`
- `修复看板`
- `备件智能分类模块搭建`

---

## 项目架构

```
langdong/
├── backend/                  # Spring Boot 3.2 后端
│   └── src/main/java/com/langdong/spare/
│       ├── controller/       # REST 控制器
│       ├── service/          # 业务逻辑（含 ai/ 子包）
│       ├── mapper/           # MyBatis Mapper 接口
│       ├── entity/           # 数据库实体（Lombok @Data）
│       ├── dto/              # 请求/响应 DTO（VO 后缀用于视图对象）
│       ├── config/           # Spring 配置（Security、Async）
│       └── util/             # 工具类（JwtUtil、ClassifyCalculator）
├── backend/src/main/resources/
│   └── mapper/               # MyBatis XML 映射文件（*.xml）
├── frontend/src/
│   ├── views/                # Vue 页面组件（按业务子目录分组）
│   │   ├── requisition/      # 领用管理（5个子视图）
│   │   ├── workorder/        # 维修工单（5个子视图）
│   │   ├── classify/         # 智能分类
│   │   ├── ai/               # AI 预测
│   │   ├── report/           # 报表看板
│   │   └── sys/              # 系统管理
│   ├── router/               # Vue Router 配置
│   ├── store/                # Vuex 状态管理
│   └── utils/                # Axios 拦截器等工具
└── sql/                      # 数据库脚本（init.sql + 各模块 SQL）
```

---

## 新增业务模块工作流

每次新增模块时，必须同步创建以下文件（文件必须同批次提交）：

### 后端（按顺序）
1. `entity/XxxEntity.java` — 实体类，必须用 `@Data`（Lombok），时间字段用 `LocalDateTime`
2. `dto/XxxDTO.java` / `dto/XxxVO.java` — 请求/响应对象
3. `mapper/XxxMapper.java` — MyBatis Mapper 接口（`@Mapper`）
4. `resources/mapper/XxxMapper.xml` — XML 映射，必须有 `<resultMap>` 显式映射所有字段
5. `service/XxxService.java` — 业务层
6. `controller/XxxController.java` — REST 控制器（`@RestController`，`@RequestMapping("/api/xxx")`）

### 前端
7. `frontend/src/views/xxx/XxxView.vue` — Vue 2 页面组件（Element UI）
8. `frontend/src/router/index.js` — 注册路由

### 数据库
9. `sql/xxx_module.sql` — DDL + 菜单权限 INSERT（固定格式）

---

## 关键约定

### 后端

- **ORM**: 原生 MyBatis（非 MyBatis-Plus），所有 SQL 写在 XML 中
- **实体类**: 统一使用 `@Data`，时间字段用 `LocalDateTime`
- **权限控制**: `@PreAuthorize("hasAuthority('模块:操作:动作')")`，权限字符串来自 `sys_menu.permission`
- **JWT**: 所有 `/api/**` 路由需要鉴权（除 `/api/auth/**`）
- **异步/定时**: 使用 `@Async` + `@Scheduled`，需在 Config 类上加 `@EnableAsync` + `@EnableScheduling`
- **工具类**: 静态方法优先，不注入 Spring Bean（参考 `ClassifyCalculator`）

### 前端

- **框架**: Vue 2.7 + Element UI v2（`el-table`、`el-form`、`el-dialog`）
- **HTTP**: Axios，统一封装在 `utils/request.js`，含 JWT 拦截器
- **图表**: ECharts（热力矩阵、折线图、柱状图）
- **导出**: xlsx + file-saver（需提前 `npm install`）
- **路由**: 按业务模块分组，基路径 `/smart`（id=11 菜单）

### 数据库

- 新模块的菜单记录需插入 `sys_menu` 并关联权限
- SQL 文件命名：`sql/<module_name>_module.sql`
- AI/种子数据单独放 `sql/seed_*.sql`

---

## 测试规范

- 测试目录：`backend/src/test/java/com/langdong/spare/`
- 命名：`XxxTest.java`（JUnit 5）
- 工具类测试优先（如 `ClassifyCalculatorTest.java` 含 27 个用例）
- 目标覆盖率：80%+

---

## QA & 功能记录（自动维护）

- Bug 排查记录 → `QA.md`（格式：`Q - 问题 - 解决方案`）
- 新功能记录 → `function.md`（格式：`F - 功能描述 - 落实情况`）
