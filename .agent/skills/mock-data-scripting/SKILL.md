---
name: mock-data-scripting
description: Best practices for generating and importing complex structured mock data using Python scripts into MySQL.
---

# Generating and Importing Mock Data via Scripts

When generating or appending a large number of structured mock data (e.g., to populate a dashboard or report) for the Langdong project, follow these best practices to ensure data integrity and avoid SQL import errors.

## 1. Avoid Manual SQL Insertion for Complex Data
Do not attempt to write complex data simulations (like randomizing timeframes, distributing prices, establishing relationship bounds and calculating percentages) directly by hand or within `bash`/`cat`. 
Instead, write a simple Python script (e.g., in `/tmp/generate_sql.py`) to handle the logic. 

## 2. Python Scripting Guidelines
- **Open File with Correct Encoding**: Always write out the generated `.sql` file with `encoding="utf-8"`, especially if generating Chinese characters. 
- **Verify Schema References**: Always double-check target table column names before formulating the `INSERT` statements to avoid common `Error 1054: Unknown column` issues. Never guess the table structure; refer directly to the `.sql` schema definitions (e.g., `init.sql`, `purchase_module.sql`).
- **Use TRUNCATE Before INSERT**: When re-generating testing data for a specific feature, consider truncating the target tables to ensure clean results (unless those tables contain critical data, like the `admin` user mapping; in that case, see `data-preservation` skill).
- **Control Foreign Key Checks**: Wrap the generated SQL in `SET FOREIGN_KEY_CHECKS = 0;` and `SET FOREIGN_KEY_CHECKS = 1;` to avoid dependency sequence errors if truncating multiple interrelated tables.

**Example Python Template:**
```python
import random
from datetime import datetime, timedelta

def build_sql():
    sql = "USE spare_db;\nSET FOREIGN_KEY_CHECKS = 0;\n"
    sql += "TRUNCATE TABLE target_table;\n"
    
    for i in range(1, 10):
        # ... your randomization logic ...
        dt_str = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        sql += f"INSERT INTO target_table (col1, col2, created_at) VALUES ('{val1}', {val2}, '{dt_str}');\n"
        
    sql += "SET FOREIGN_KEY_CHECKS = 1;\n"
    
    with open("/tmp/mock_target.sql", "w", encoding="utf-8") as f:
        f.write(sql)

if __name__ == "__main__":
    build_sql()
```

## 3. SQL Execution Guidelines
Once the script successfully outputs a `.sql` file, run the execution using `mysql` command line wrapper. 

**CRITICAL: Define Character Set**
Always specify `--default-character-set=utf8mb4` during the import command to prevent encoding corruption (garbled text) when saving Chinese values to the database.

```bash
# Correct Command Workflow
python3 /tmp/generate_sql.py && mysql -u root --default-character-set=utf8mb4 spare_db < /tmp/mock_target.sql
```

## 4. Verification
After the command completes, always execute a query via the terminal to confirm row counts or perform a quick data integrity spot-check:
```bash
mysql -u root spare_db -e "SELECT count(*) FROM target_table;"
```
