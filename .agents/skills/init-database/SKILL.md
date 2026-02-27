---
name: init-database
description: 在 Windows 环境下初始化项目数据库（spare_db）。当数据库不存在或需要重置时执行此 Skill。
---

# Init Database — 数据库初始化工作流

在 Windows PowerShell 环境下，`mysql < file.sql` 重定向语法**不可用**，必须使用 Python 脚本来执行 SQL 文件。

---

## 关键注意事项

| 问题 | 原因 | 解决方案 |
|---|---|---|
| `<` 重定向不可用 | PowerShell 不支持 stdin 重定向 | 改用 Python subprocess |
| `Get-Content \| mysql` 报错 | 文件编码不兼容 | 用 Python 指定编码读取 |
| `-e "source xxx.sql"` 失败 | source 只能在交互模式用 | 改用 Python subprocess |
| `init.sql` 编码为 GBK | 文件历史原因 | Python 读取时指定 `encoding="gbk"` |
| `add_tables.sql` 编码为 UTF-8 | 新文件默认编码 | Python 读取时指定 `encoding="utf-8"` |

---

## 步骤 1：确认数据库密码

查看 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    password: 123456   # ← 记下这个密码
```

---

## 步骤 2：创建执行脚本

创建临时 Python 脚本 `C:\tmp\run_sql.py`：

```python
import subprocess

# 根据实际情况调整文件路径和编码
files = [
    (r"e:\GitHub\langdong\sql\init.sql", "gbk"),       # 主初始化脚本（GBK编码）
    (r"e:\GitHub\langdong\sql\add_tables.sql", "utf-8"), # 补充建表脚本（UTF-8编码）
]

DB_PASSWORD = "123456"  # 从 application.yml 读取

for f, enc in files:
    print(f">>> 执行: {f} (编码: {enc})")
    try:
        with open(f, "r", encoding=enc, errors="replace") as fp:
            sql = fp.read()
    except Exception as e:
        print(f"    读取文件失败: {e}")
        continue
    result = subprocess.run(
        ["mysql", "-u", "root", f"-p{DB_PASSWORD}", "--default-character-set=utf8mb4"],
        input=sql.encode("utf-8"),
        capture_output=True,
    )
    if result.returncode == 0:
        print(f"    ✅ 成功！")
    else:
        err = result.stderr.decode("utf-8", errors="replace")
        print(f"    ❌ 失败：{err[:800]}")
    if result.stdout:
        print(result.stdout.decode("utf-8", errors="replace")[:200])
```

---

## 步骤 3：用 Anaconda Python 执行脚本

> PowerShell 内置的 `python` 命令可能不可用，使用 Anaconda 的完整路径：

```powershell
D:\Anaconda3\python.exe C:\tmp\run_sql.py
```

若 Anaconda 路径不同，先用以下命令查找：

```powershell
cmd /c "where python"
```

---

## 步骤 4：验证数据库

创建验证脚本 `C:\tmp\verify_db.py`：

```python
import subprocess

result = subprocess.run(
    ["mysql", "-u", "root", "-p123456", "--default-character-set=utf8mb4",
     "spare_db", "-e", "SHOW TABLES;"],
    capture_output=True,
)
out = result.stdout.decode("utf-8", errors="replace")
err = result.stderr.decode("utf-8", errors="replace")
print("数据库中的表：")
print(out)
if err and "Warning" not in err:
    print("错误：", err)
```

执行：

```powershell
D:\Anaconda3\python.exe C:\tmp\verify_db.py
```

输出中出现表名列表（如 `user`、`spare_part`、`menu` 等）即为成功。

---

## 预期的数据库表清单

`spare_db` 中应包含以下表：

| 表名 | 用途 |
|---|---|
| `user` | 用户账号 |
| `role` | 角色 |
| `user_role` | 用户-角色关联 |
| `menu` | 菜单权限 |
| `spare_part` | 备件主数据 |
| `spare_part_location` | 备件库位 |
| `spare_part_location_stock` | 库位库存 |
| `supplier` | 供应商 |
| `supply_category` | 供应分类 |
| `purchase_order` | 采购订单 |
| `purchase_order_item` | 采购订单明细 |
| `stock_in_receipt` | 入库单 |
| `stock_in_item` | 入库明细 |
| `classification_strategy` | 分类策略 |
| `classification_result` | 分类结果 |
| `classification_adjustment_record` | 分类调整记录 |

---

## 数据库连接信息

| 参数 | 值 |
|---|---|
| Host | `localhost:3306` |
| 数据库名 | `spare_db` |
| 用户名 | `root` |
| 密码 | `123456`（见 `application.yml`） |
| 字符集 | `utf8mb4` |

默认管理员账号：`admin` / `123456`
