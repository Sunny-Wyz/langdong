---
name: after-pull
description: 从远端 git pull 或 merge 之后执行此 Skill，自动检查并修复编译错误、缺失数据库表，确保项目可以正常启动。
---

# After Pull — 合并后自动修复工作流

每次从远端 `git pull` 或 `git merge` 之后，按以下步骤执行：

---

## 步骤 1：查看并解决 Git 合并冲突

执行以下命令查看当前合并状态：

```bash
git status --porcelain
```

- 识别 `UU` 前缀（双方修改冲突）的文件
- 对每一个冲突文件，以 **本地分支内容为准** 执行：
  ```bash
  git checkout --ours <冲突文件路径>
  git add <冲突文件路径>
  ```
- 对来自远端的 **新增文件**（`A` 前缀），保留它们，不做处理
- 对已有文件（`M` 前缀），如无冲突则保持原样；如有冲突则同上，以本地为准
- 冲突全部解决后执行：
  ```bash
  git commit -m "Merge: 以本地为准解决冲突，保留远端新增文件"
  ```

---

## 步骤 2：尝试编译后端，修复报错

进入 `backend` 目录尝试编译：

```bash
cd backend
mvn clean compile
```

如果编译失败，查找报错并常见修复方式如下：

### 常见错误 1：类型不匹配（`String` vs `int`）
- 检查 Entity 类（`entity/` 目录）中字段的实际类型（`String`、`Integer` 等）
- 查找 Service 层中直接用 `>` `<` 等比较运算符的地方，确保类型一致
- 修复方式：如 `capacity` 为 `String`，改为 `Integer.parseInt(loc.getCapacity())` 后再比较

### 常见错误 2：返回值 `null` 导致 NullPointerException（拆箱风险）
- Mapper 接口中返回 `Integer` 的方法（如 `sumQuantityByLocationId()`）可能返回 `null`
- 修复方式：
  ```java
  Integer result = mapper.someMethod();
  int value = result == null ? 0 : result;
  ```

反复编译直至 **exit code 0**（`BUILD SUCCESS`）。

---

## 步骤 3：补充缺失的数据库表

### 3.1 检查新增的 Entity 类
查看 `backend/src/main/java/.../entity/` 目录下，有哪些是**本次新增的**实体类（即从远端合并进来的新文件）。

### 3.2 对比已有的数据库建表脚本
查看 `sql/init.sql`，确认哪些表**尚未创建**。

### 3.3 为缺失的表编写建表语句
根据 Entity 类中的字段，编写对应的 `CREATE TABLE IF NOT EXISTS` 建表 SQL，保存到 `sql/add_tables.sql`。

常见字段类型映射：
| Java 类型 | MySQL 类型 |
|---|---|
| `Long` | `bigint(20)` |
| `Integer` | `int(11)` |
| `String` | `varchar(255)` 或 `varchar(50)` |
| `BigDecimal` | `decimal(10,2)` |
| `LocalDate` | `date` |
| `LocalDateTime` | `datetime` |

### 3.4 执行建表脚本
```bash
mysql -u root -p123456 spare_db < sql/add_tables.sql
```

> ⚠️ 若密码不同，请修改 -p 后面的密码。密码见 `backend/src/main/resources/application.yml`。

### 3.5 追加到初始化脚本
确保 `sql/init.sql` 同步更新：
```powershell
Get-Content sql\add_tables.sql | Add-Content sql\init.sql
```

---

## 步骤 4：最终验证

重新编译确认通过：

```bash
cd backend
mvn compile
```

输出 `BUILD SUCCESS` 代表一切就绪。

---

## 项目启动命令

| 服务 | 目录 | 命令 | 访问地址 |
|---|---|---|---|
| 后端 Spring Boot | `backend/` | `mvn spring-boot:run` | http://localhost:8080 |
| 前端 Vue | `frontend/` | `npm run serve` | http://localhost:3000 |

默认账号：`admin` / `123456`
