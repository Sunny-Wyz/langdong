---
name: troubleshoot-menu-404
description: 排查新增模块后菜单路由 404 错误和菜单重复显示的问题。主要解决因 `INSERT IGNORE` 导致的主键 ID 冲突被忽略、以及后端进程未重启等常见陷阱。
---

# 菜单 404 与重复显示排查指南 (Troubleshoot Menu 404)

此 Skill 记录了从远端拉取新模块、整合后发生“部分新模块可用，部分模块菜单跳转 404，或者重复显示”时的标准排查和解决流程。

---

## 问题 1：路由跳转 404 (Not Found)

### 现象表现
- 能够正常登录并看到侧边栏。
- 点击新增的子菜单（如：故障报修、派工管理），右侧区域无法渲染组件或直接显示 404。
- Network 面板或控制台发现调用后端 `/api/...` 接口时返回 404 报错。

### 根因分析

1. **后端旧进程尚未彻底关闭**
   - **现象**：虽然执行了 `mvn spring-boot:run` 或重新编译了代码，但**旧的后端进程依然占用 8080 端口**。新启动的 Spring Boot 实例由于端口冲突报错退出（或者直接挂起），导致前端请求的其实还是 merge 之前的旧代码。旧代码中没有包含新的 Controller，自然接口报 404。
   - **解决**：彻底清理 8080 端口进程并重新启动。
     ```powershell
     # 查找 8080 端口被哪个 PID 占用
     netstat -aon | findstr :8080 | findstr LISTENING
     
     # 假设 PID 为 12345，强制终止
     taskkill /PID 12345 /F
     
     # 重新进入 backend，重新编译并启动
     cd backend
     mvn clean package -DskipTests
     java -jar target\spare-1.0.0.jar
     ```

2. **数据库菜单项 `INSERT IGNORE` 导致插入丢失**
   - **现象**：如果在追加 SQL 脚本（如 `work_order_module.sql`）时使用了 `INSERT IGNORE INTO menu (id, ...)`，且该 `id` 已经被其他模块（如仓储模块）提前占用了，则该条菜单和对应的权限不会报错，而是被**静默跳过**。此时数据库里根本没有这条菜单记录。
   - **解决**：检查 `menu` 表是否有缺少的数据，如果是 id 冲突引起的跳过，修改 SQL 使用空闲的大 ID 区间（如 id=60~99）重新插入，并确保给对应的 Role 分配 `role_menu` 权限。

---

## 问题 2：侧边栏菜单重复显示

### 现象表现
- 修复 404 后发现，部分子功能菜单（如“完工确认”、“维修过程记录”）在侧边栏出现了两个或多个完全一样的项目。

### 根因分析

- **现象**：在解决上述“INSERT IGNORE 被跳过”导致菜单缺失的问题时，直接为整个模块新建了全新的 ID 块进行批量插入（如：全部设为 60~64）。但实际上，原版可能在之前仅仅发生了部分冲突（例如只差 id=23 和 24），而其余的 id=25、26、27 在旧版本中其实早已被成功插入过。
- 这样，新插入的同样名称、相同路径的新ID数据，与数据库中幸存的旧ID数据，共同挂在同一个 `parent_id` 下，造成菜单出现复数。

### 解决

1. **查询重复项**：
   使用 Python + subprocess 的方式列出指定模块下所有的路径和名称：
   ```python
   import subprocess
   r = subprocess.run(
       ["mysql", "-u", "root", "-p123456", "--default-character-set=utf8mb4", "spare_db",
        "-e", "SELECT id, parent_id, name, path FROM menu WHERE parent_id=14 ORDER BY path, id;"],
       capture_output=True,
   )
   print(r.stdout.decode("utf-8", "replace"))
   ```

2. **清理多余项**：
   比对并选择性地删除**错误新增**或**被废弃**的那部分菜单 `id` 以及它的对应权限。
   ```sql
   -- 示例：发现 id=62, 63, 64 是多余的新增的，真正的原数据是 25, 26, 27
   DELETE FROM menu WHERE id IN (62, 63, 64);
   DELETE FROM role_menu WHERE menu_id IN (62, 63, 64);
   
   -- 最后别忘了确保保留下来的 ID 同样拥有访问权限
   INSERT IGNORE INTO role_menu (role_id, menu_id) VALUES (1, 25), (1, 26), (1, 27);
   ```

---

## 检查清单总结

每次做新增模块合并时，务必核对以下三件事：
1. **网络端口**：`netstat` 确保 8080 是在彻底关闭旧应用后再重启的！
2. **路由守卫**：确保 `router/index.js` 合并时，没把 master 或本地的旧路由互相给冲掉（需手动仔细比对待合并的代码段）。
3. **ID无碰撞**：在执行建表 SQL 文件前，若发现存在显式写死 `id` 的 `INSERT IGNORE` 语句，先通过 `SELECT id FROM menu` 确认没有占座碰撞，或者干脆删掉写死的 ID 让其走 `AUTO_INCREMENT`（但这种需要留意后续父子权限表的绑定）。
