# 删除备件功能实现思路

## 一、功能分析

在动手之前，先理清楚"删除一条备件记录"这个操作在整个系统中涉及哪些层次。

该项目采用典型的前后端分离架构：
- **前端**（Vue 2）负责展示界面和发起 HTTP 请求
- **后端**（Spring Boot）负责接收请求、执行业务逻辑
- **数据库**（MySQL + MyBatis）负责实际的数据操作

因此，实现删除功能需要从下到上贯穿三层：**数据库访问层 → 接口层 → 前端交互层**。

---

## 二、逐层实现拆解

### 第一层：数据库访问层（MyBatis Mapper）

MyBatis 的工作方式是：Java 接口定义方法签名，XML 文件提供对应的 SQL 语句，框架负责自动绑定。

**需要改动两个文件：**

#### 1. `SparePartMapper.java`（接口定义）

```java
int deleteById(Long id);
```

- 方法名 `deleteById` 清晰表达意图
- 参数 `Long id` 是备件的主键，足以唯一定位一条记录
- 返回值 `int` 是 MyBatis 的惯例，表示受影响的行数（删除成功为 1，记录不存在为 0），后续可用来判断是否真的删到了数据

#### 2. `SparePartMapper.xml`（SQL 语句）

```xml
<delete id="deleteById">
    DELETE FROM spare_part WHERE id = #{id}
</delete>
```

- `id="deleteById"` 必须与接口方法名一致，MyBatis 靠这个做绑定
- `#{id}` 是 MyBatis 的预编译占位符，等价于 JDBC 的 `?`，可防止 SQL 注入
- `WHERE id = #{id}` 确保只删除指定的一行，不会误删其他数据

---

### 第二层：后端接口层（Spring Boot Controller）

Controller 的职责是：接收 HTTP 请求 → 调用 Mapper → 返回 HTTP 响应。

**在 `SparePartController.java` 中新增：**

```java
@DeleteMapping("/{id}")
public ResponseEntity<?> delete(@PathVariable Long id) {
    int rows = sparePartMapper.deleteById(id);
    if (rows == 0) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok().build();
}
```

设计要点逐行解释：

| 代码 | 说明 |
|---|---|
| `@DeleteMapping("/{id}")` | 声明此方法处理 `DELETE /api/spare-parts/{id}` 请求，DELETE 是 HTTP 标准动词，语义上表示"删除资源" |
| `@PathVariable Long id` | 从 URL 路径中提取 `{id}` 并自动转换为 `Long` 类型 |
| `int rows = sparePartMapper.deleteById(id)` | 执行删除并获取受影响行数 |
| `if (rows == 0)` | 如果影响行数为 0，说明该 ID 对应的记录不存在，返回 HTTP 404（Not Found） |
| `ResponseEntity.ok().build()` | 删除成功返回 HTTP 200，body 为空（删除操作无需返回数据体） |

**为什么用 DELETE 而不是 POST？**

这是 RESTful API 设计规范的要求：
- `GET` → 查询
- `POST` → 新增
- `PUT/PATCH` → 修改
- `DELETE` → 删除

遵守规范使接口语义清晰，也方便前端调用。

---

### 第三层：前端交互层（Vue 组件）

前端需要做两件事：**展示删除入口** 和 **处理用户交互**。

#### 1. 在表格中添加"操作"列

```html
<el-table-column label="操作" width="100" align="center">
  <template slot-scope="{ row }">
    <el-button type="danger" size="mini" icon="el-icon-delete" @click="handleDelete(row)">删除</el-button>
  </template>
</el-table-column>
```

- `slot-scope="{ row }"` 是 Element UI 表格的插槽语法，`row` 是当前行的完整数据对象（包含 `id`、`name` 等字段）
- 点击按钮时把整个 `row` 传给 `handleDelete`，这样方法里既能拿到 `id` 来调接口，也能拿到 `name` 来展示在确认框里
- 用红色（`type="danger"`）区分于新增按钮，符合用户对"危险操作"的视觉预期

#### 2. 实现 `handleDelete` 方法

```javascript
handleDelete(row) {
  this.$confirm(`确定要删除备件"${row.name}"吗？此操作不可恢复。`, '提示', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/spare-parts/${row.id}`)
      this.$message.success('删除成功')
      this.fetchList()
    } catch (e) {
      this.$message.error('删除失败，请重试')
    }
  }).catch(() => {})
}
```

流程拆解：

```
用户点击删除
    ↓
弹出二次确认框（$confirm）
    ↓
    ├─ 用户点"取消" → .catch(() => {}) 静默关闭，什么都不做
    └─ 用户点"确定删除" → .then() 执行
            ↓
        发送 DELETE 请求到后端
            ↓
            ├─ 请求成功 → 提示"删除成功" → 重新拉取列表刷新页面
            └─ 请求失败 → 提示"删除失败，请重试"
```

**为什么要加二次确认？**

删除是不可恢复的操作。用户可能误触按钮，二次确认框是防止误操作的标准做法。确认框文案中展示了备件名称（`row.name`），让用户清楚自己要删的是哪条记录。

**为什么删除成功后要调 `fetchList()`？**

前端的 `list` 数组是从后端拉取的快照数据。删除后如果不重新请求，列表仍会显示已删除的那条记录，造成数据不一致。调用 `fetchList()` 重新请求是最简单可靠的同步方式。

---

## 三、数据流全链路

用户点击删除按钮到数据库记录被删除的完整链路：

```
[用户点击"删除"]
    → [前端弹出确认框]
    → [用户确认]
    → [前端发送 HTTP DELETE /api/spare-parts/5]
    → [Spring Security 验证 JWT Token]
    → [请求到达 SparePartController.delete(id=5)]
    → [调用 sparePartMapper.deleteById(5)]
    → [MyBatis 执行 DELETE FROM spare_part WHERE id = 5]
    → [MySQL 删除记录，返回受影响行数 1]
    → [Controller 返回 HTTP 200]
    → [前端收到成功响应，弹出"删除成功"提示]
    → [前端调用 fetchList() 重新拉取列表]
    → [列表刷新，已删除记录消失]
```

---

## 四、改动文件汇总

| 文件路径 | 改动内容 |
|---|---|
| `backend/.../mapper/SparePartMapper.java` | 新增 `deleteById(Long id)` 方法声明 |
| `backend/.../resources/mapper/SparePartMapper.xml` | 新增 `<delete>` SQL 语句 |
| `backend/.../controller/SparePartController.java` | 新增 `DELETE /{id}` HTTP 接口 |
| `frontend/src/views/SparePartList.vue` | 新增"操作"表格列、删除按钮及 `handleDelete` 方法 |
