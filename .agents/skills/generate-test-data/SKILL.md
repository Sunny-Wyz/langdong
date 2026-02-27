---
name: generate-test-data
description: 以安全且健壮的方式，为 Spring Boot + MySQL 项目通过 Python 脚本批量生成和插入数据库的业务测试数据。
---

# 批量生成与插入业务测试数据指南

在本地开发或者排查时，为了验证新功能（如：查询筛选、统计分析、外键级联）经常需要填充批量的测试数据。此 Skill 记录了标准的动态生成数据的流程，避免因为硬编码旧的 DDL 或手动敲 SQL 导致的字段错位和乱码问题。

---

## 阶段 1：确认目标表真实的 DDL 字段结构

**重要原则：绝不凭记忆直接写 `INSERT` 脚本！** 
在编写测试数据前，必须检查目标表（如 `spare_part`）当前数据库里究竟有哪些字段（是否在最近的 migration 中被改过）。

### 方法：
通过 Python 执行以下命令导出表结构，查看精确的列名和默认约束：

```python
import subprocess

r = subprocess.run(
    ["mysql", "-u", "root", "-p123456", "--default-character-set=utf8mb4", "spare_db", "-e", "SHOW CREATE TABLE spare_part;"],
    capture_output=True,
)
print(r.stdout.decode("utf-8", "replace"))
```
*（注意修改对应的密码、数据库名和表名）*

找到必须填写的 `NOT NULL` 字段以及相关的关联主外键。

---

## 阶段 2：通过 Python 生成动态测试数据

Python 脚本生成数据的优势在于：
1. **可以安全解决中文字符编码**：直接给 `mysql` 进程喂 UTF-8 bytes。
2. **便于随机生成参数**：使用 `random` 模块可以大批量制造价格、型号、等级等具备区分度的数据集。

### Python 脚本范例

创建一个 `insert_test_data.py` 文件：

```python
import subprocess
import random

# 【注意】按照刚才查出来的真实表结构，决定要写入的列，不需要插入的填默认值或自动生成的列无需列出
values = []
for i in range(1, 11): # 假设制造 10 条数据
    # 为保证数据质量，对于 VARCHAR 请自行增加单引号，如: f"'{xxx}'"
    code = f"'TY-{10000+i}'"
    name = f"'测试物资{i}'"
    model = f"'X型-{i}'"
    quantity = "0"          # INT 直接转换为字符串
    unit = "'个'"
    price = f"{round(random.uniform(50.0, 800.0), 2)}" # 制造随机价格
    category_id = "1"       # 关联键，必须确保目标表存在该 ID（例如基础依赖字典）
    supplier = f"'默认供应商'"
    remark = f"'用于测试某某功能自动生成'"
    
    # 将组装好的一行 VALUE 放进去
    values.append(f"({code}, {name}, {model}, {quantity}, {unit}, {price}, {category_id}, {supplier}, {remark})")

# 组装批量 INSERT 语句
sql = f"""
USE spare_db;
INSERT INTO spare_part (code, name, model, quantity, unit, price, category_id, supplier, remark) 
VALUES {', '.join(values)};
"""

# 执行命令插入
r = subprocess.run(
    ["mysql", "-u", "root", "-p123456", "--default-character-set=utf8mb4"],
    input=sql.encode("utf-8"), # 非常重要，确保 MySQL 接到的是 UTF-8 编码的字节流
    capture_output=True,
)

if r.returncode == 0:
    print("✅ 测试数据批量插入成功！")
else:
    print("❌ 插入失败:", r.stderr.decode("utf-8", "replace"))
```

---

## 阶段 3：执行脚本和验证

使用 Anaconda 或 Python 环境运行该脚本：

```powershell
D:\Anaconda3\python.exe insert_test_data.py
```

执行后，可以用同样的手段或手动前往 MySQL 中查询最新的插入集：

```python
import subprocess

r = subprocess.run(
    ["mysql", "-u", "root", "-p123456", "--default-character-set=utf8mb4", "spare_db", "-e", "SELECT id, name, code, price FROM spare_part ORDER BY id DESC LIMIT 10;"],
    capture_output=True,
)
print("最近插入的数据：\n", r.stdout.decode("utf-8", "replace"))
```

### 提示总结

- **解决 SQL 文件导入报错的最佳平替**：使用 Python 代替了 `.sql` 文本文件和 `source` 命令。
- **关联表注意顺序**：如果需要插入的数据涉及主外键约束（如先增加“分类”，后插入“备件”），请一定在 `values.append()` 或拆分为多个 `r = subprocess.run()` 前梳理好顺序，保证 ID 确实存在。
