# 备件管理模块 — 功能说明文档

> **版本**：v1.0  
> **提交**：`8b14e4a` — 增加功能：新增备件  
> **日期**：2026-02-21  
> **作者**：xiaoxin

---

## 一、功能概述

本次更新为**朗东备件管理系统**新增了"备件管理"模块，支持以下核心能力：

| 功能 | 说明 |
|---|---|
| 备件列表查看 | 以表格形式展示所有备件信息，按创建时间倒序排列 |
| 新增备件 | 通过弹窗表单录入备件信息，提交后实时刷新列表 |

同时将首页从欢迎占位页改造为带**侧边栏导航**的完整应用 Shell，为后续模块扩展奠定布局基础。

---

## 二、技术架构

```
前端（Vue 2 + Element UI）
    └─ /home/spare-parts → SparePartList.vue
         ↕ HTTP REST API
后端（Spring Boot + MyBatis）
    └─ GET  /api/spare-parts  → 查询备件列表
    └─ POST /api/spare-parts  → 新增备件
         ↕
数据库（MySQL）
    └─ 表：spare_part
```

---

## 三、数据库设计

**表名**：`spare_part`

| 字段 | 类型 | 是否必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | BIGINT | ✅ | 自增 | 主键 |
| `name` | VARCHAR(100) | ✅ | — | 备件名称 |
| `model` | VARCHAR(100) | ❌ | NULL | 型号规格 |
| `quantity` | INT | ✅ | 0 | 库存数量（不能为负） |
| `unit` | VARCHAR(20) | ❌ | 个 | 单位 |
| `price` | DECIMAL(10,2) | ❌ | NULL | 单价（元） |
| `category` | VARCHAR(50) | ❌ | NULL | 类别 |
| `supplier` | VARCHAR(100) | ❌ | NULL | 供应商 |
| `remark` | TEXT | ❌ | NULL | 备注 |
| `created_at` | DATETIME | ✅ | CURRENT_TIMESTAMP | 创建时间（自动写入） |
| `updated_at` | DATETIME | ✅ | CURRENT_TIMESTAMP | 更新时间（自动刷新） |

---

## 四、后端接口说明

### 4.1 查询备件列表

```
GET /api/spare-parts
```

**响应示例**：

```json
[
  {
    "id": 1,
    "name": "轴承",
    "model": "6205-2RS",
    "quantity": 50,
    "unit": "个",
    "price": 12.50,
    "category": "机械件",
    "supplier": "SKF",
    "remark": null,
    "createdAt": "2026-02-21T01:15:35",
    "updatedAt": "2026-02-21T01:15:35"
  }
]
```

---

### 4.2 新增备件

```
POST /api/spare-parts
Content-Type: application/json
```

**请求体（SparePartDTO）**：

```json
{
  "name": "轴承",
  "model": "6205-2RS",
  "quantity": 50,
  "unit": "个",
  "price": 12.50,
  "category": "机械件",
  "supplier": "SKF",
  "remark": "常用型号"
}
```

**参数校验规则**：

| 字段 | 规则 |
|---|---|
| `name` | 必填，不能为空字符串 |
| `quantity` | 必填，不能为负数 |
| 其他字段 | 可选 |

**响应**：

- `200 OK`：返回已插入的备件对象（含自增 id）
- `400 Bad Request`：参数不合法时返回错误提示文字

---

## 五、前端页面说明

### 5.1 页面路由

| 路径 | 组件 | 说明 |
|---|---|---|
| `/login` | `Login.vue` | 登录页（已有） |
| `/home` | `Home.vue` | 应用主框架（本次改造） |
| `/home/spare-parts` | `SparePartList.vue` | 备件列表页（本次新增） |

> 访问 `/home` 时自动重定向至 `/home/spare-parts`。

---

### 5.2 Home.vue — 主框架布局

- **左侧侧边栏**：深色（`#304156`）系统导航，显示系统名称与菜单项（当前仅"备件管理"）
- **顶部 Header**：显示当前登录用户名 + 退出登录按钮
- **主内容区**：`<router-view />` 动态加载子页面

---

### 5.3 SparePartList.vue — 备件列表页

**列表字段**：

| 列名 | 字段 | 说明 |
|---|---|---|
| ID | `id` | 自增主键 |
| 备件名称 | `name` | — |
| 型号规格 | `model` | — |
| 类别 | `category` | — |
| 库存数量 | `quantity` | — |
| 单位 | `unit` | — |
| 单价（元） | `price` | 自动格式化为两位小数，无值显示 `—` |
| 供应商 | `supplier` | — |
| 备注 | `remark` | 超长文字悬浮提示 |
| 创建时间 | `createdAt` | 格式化为本地时间 |

**新增备件弹窗表单**：

- 备件名称（必填）
- 型号规格
- 库存数量（数字输入，最小值 0，必填）
- 单位（默认"个"）
- 单价（数字输入，保留两位小数）
- 类别
- 供应商
- 备注（文本域）

提交成功后弹出成功提示，自动刷新列表并关闭弹窗；取消或关闭弹窗后表单自动重置。

---

## 六、新增文件清单

| 文件路径 | 说明 |
|---|---|
| `backend/src/main/java/com/langdong/spare/model/SparePart.java` | 备件实体类 |
| `backend/src/main/java/com/langdong/spare/dto/SparePartDTO.java` | 备件数据传输对象 |
| `backend/src/main/java/com/langdong/spare/mapper/SparePartMapper.java` | MyBatis Mapper 接口 |
| `backend/src/main/resources/mapper/SparePartMapper.xml` | MyBatis SQL 映射 |
| `backend/src/main/java/com/langdong/spare/controller/SparePartController.java` | REST 控制器 |
| `frontend/src/views/SparePartList.vue` | 备件列表前端页面 |

## 七、修改文件清单

| 文件路径 | 说明 |
|---|---|
| `sql/init.sql` | 新增 `spare_part` 建表语句 |
| `frontend/src/router/index.js` | 新增备件路由，Home 改为嵌套路由 |
| `frontend/src/views/Home.vue` | 改造为侧边栏 + Header + router-view 框架 |
