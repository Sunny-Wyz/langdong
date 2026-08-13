# 贡献指南 (Contributing Guide)

## 目录

- [开发环境搭建](#开发环境搭建)
- [可用命令参考](#可用命令参考)
- [测试指南](#测试指南)
- [代码规范](#代码规范)
- [提交规范](#提交规范)
- [PR 提交清单](#pr-提交清单)

---

## 开发环境搭建

### 前置依赖

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17 | 后端运行环境 |
| Maven | 3.8+ | 后端构建工具 |
| Node.js | 16+ | 前端运行环境 |
| npm | 8+ | 前端包管理器 |
| MySQL | 5.7+ | 数据库 |

### 初始化步骤

```bash
# 1. 克隆仓库
git clone <repository-url>
cd langdong

# 2. 初始化数据库（执行顺序很重要）
mysql -u root -p < sql/init.sql
mysql -u root -p spare_db < sql/classify_module.sql
mysql -u root -p spare_db < sql/ai_module.sql
mysql -u root -p spare_db < sql/requisition_module.sql
mysql -u root -p spare_db < sql/fix_menu.sql
mysql -u root -p spare_db < sql/work_order_module.sql
mysql -u root -p spare_db < sql/purchase_module.sql
mysql -u root -p spare_db < sql/report_module.sql
mysql -u root -p spare_db < sql/phm_module.sql
mysql -u root -p spare_db < sql/fifo_migration_v1.sql

# （可选）导入测试数据
mysql -u root -p spare_db < sql/mock_data.sql

# 3. 配置后端数据库连接
# 编辑 backend/src/main/resources/application.yml

# 4. 启动后端
cd backend && mvn spring-boot:run

# 5. 安装前端依赖并启动
cd frontend && npm install && npm run serve
```

---

## 可用命令参考

<!-- AUTO-GENERATED: 从 frontend/package.json 和 backend/pom.xml 生成 -->

### 前端命令（`cd frontend`）

| 命令 | 说明 |
|------|------|
| `npm run serve` | 启动开发服务器（含热重载），地址：http://localhost:3000 |
| `npm run build` | 生产构建，输出至 `dist/` |
| `npm install` | 安装所有依赖 |

### 后端命令（`cd backend`）

| 命令 | 说明 |
|------|------|
| `mvn spring-boot:run` | 启动后端开发服务器，端口：8080 |
| `mvn clean package` | 打包生产 JAR（输出至 `target/`） |
| `mvn test` | 运行所有单元测试 |
| `mvn clean install` | 清理 + 编译 + 测试 + 打包 |
| `mvn spring-boot:run -DskipTests` | 跳过测试启动（仅用于调试） |

<!-- END AUTO-GENERATED -->

---

## 测试指南

### 后端测试

```bash
cd backend
# 运行全部测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassifyCalculatorTest

# 查看测试报告
open target/surefire-reports/*.html
```

测试文件位置：`backend/src/test/java/com/langdong/spare/`

### 前端测试

当前前端暂无自动化测试框架，建议手工验证以下核心流程：
- 登录 / 登出
- 备件档案增删改查
- 领用申请 → 审批 → 出库 → 安装全流程
- 工单报修 → 派工 → 完工全流程

---

## 代码规范

### 后端（Java）

- 使用 Lombok `@Data`，不要手写 getter/setter
- 时间字段统一使用 `LocalDateTime`
- 数据库查询使用 MyBatis XML + resultMap 显式映射
- 权限控制使用 `@PreAuthorize("hasAuthority('xxx:xxx:xxx')")`，权限字符串参照 `sql/init.sql` 中的 `menu.permission` 字段
- 不要在 Controller 中硬编码用户 ID，从 `SecurityContextHolder` 获取：
  ```java
  String username = SecurityContextHolder.getContext().getAuthentication().getName();
  User user = userMapper.findByUsername(username);
  ```
- 错误响应不要直接返回 `e.getMessage()`，使用通用提示语

### 前端（Vue 2）

- 使用 Element UI 组件，不要引入额外 UI 库
- 路由守卫已在 `src/router/index.js` 实现，新增路由需要匹配后端菜单权限
- API 请求统一通过 `src/utils/request.js` 的 axios 实例发出（已配置 token 拦截器）

---

## 提交规范

使用 Conventional Commits 格式：

```
<type>: <简短描述>

<可选详细描述>
```

| type | 使用场景 |
|------|----------|
| `feat` | 新功能 |
| `fix` | 修复 Bug |
| `refactor` | 重构（不改变行为） |
| `docs` | 文档更新 |
| `test` | 测试相关 |
| `chore` | 构建、依赖、工具配置 |
| `perf` | 性能优化 |

---

## PR 提交清单

提交 Pull Request 前，请确认以下各项：

- [ ] 后端可编译（`mvn clean package`）
- [ ] 核心接口经过手工验证
- [ ] 所有相关接口已添加 `@PreAuthorize` 权限注解
- [ ] 没有硬编码用户 ID（`return 1L`）
- [ ] 没有硬编码密钥或密码
- [ ] 错误响应不泄露系统内部信息（`e.getMessage()`）
- [ ] 涉及数据库变更时，已新增对应 SQL 脚本至 `sql/` 目录
- [ ] 前端可构建（`npm run build`）
- [ ] 主要页面可正常访问
- [ ] `function.md` 已更新功能记录
- [ ] PR 描述中说明变更背景、影响范围、验证方式
