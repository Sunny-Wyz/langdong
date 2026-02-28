---
name: mysql57-compat
description: MySQL 5.7 compatibility rules for writing SQL migration scripts. Use this skill when writing any SQL DDL scripts (ALTER TABLE, CREATE TABLE, etc.) for the langdong project. The database uses MySQL 5.7.24, which lacks several syntactic features available in MySQL 8+. Always follow these rules to avoid runtime errors.
---

# MySQL 5.7 Compatibility Rules

This project uses **MySQL 5.7.24**. The following MySQL 8.0+ features are NOT supported and must be substituted.

## Critical Substitutions

### 1. `ADD COLUMN IF NOT EXISTS` → Not supported

**Wrong (MySQL 8+ only):**
```sql
ALTER TABLE spare_part
    ADD COLUMN IF NOT EXISTS is_critical tinyint(1) NOT NULL DEFAULT 0;
```

**Correct – use a PREPARE/EXECUTE guard:**
```sql
SET @exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'spare_part'
      AND COLUMN_NAME  = 'is_critical'
);
SET @sql = IF(@exists = 0,
    'ALTER TABLE spare_part ADD COLUMN is_critical tinyint(1) NOT NULL DEFAULT 0',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

> Repeat the pattern for every additional column in the same ALTER statement (one block per column).

### 2. `CREATE TABLE IF NOT EXISTS` → Supported ✓

Standard `CREATE TABLE IF NOT EXISTS` works fine in MySQL 5.7.

### 3. `INSERT IGNORE` with explicit IDs → Use with caution

`INSERT IGNORE` silently skips rows whose primary key already exists. If an ID is already used by a different row (e.g., menu IDs reused by a previous migration), the insert is dropped without error.

**Rule:** Never hard-code IDs when inserting menu items if those IDs may already exist in the database. Prefer auto-increment:
```sql
-- Good: let MySQL assign the id, then grant role in a follow-up query
INSERT INTO menu (parent_id, name, path, component, permission, type, icon, sort)
VALUES (14, '故障报修', '/home/work-order-report', 'workorder/WorkOrderReport', 'wo:report:add', 2, 'el-icon-warning', 1);

INSERT INTO role_menu (role_id, menu_id)
SELECT 1, id FROM menu WHERE path = '/home/work-order-report';
```

### 4. Window functions (`ROW_NUMBER`, `RANK`, etc.) → Not supported

Avoid them; use subqueries or application-layer ordering instead.

### 5. `JSON_TABLE` → Not supported

Use application-layer JSON parsing.

## Quick Checklist for SQL Scripts

Before delivering any `.sql` migration file, verify:

- [ ] No `ADD COLUMN IF NOT EXISTS`
- [ ] No `DROP COLUMN IF EXISTS`  
- [ ] No hard-coded menu/permission IDs that could clash with pre-existing rows
- [ ] No window functions
- [ ] `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` on all new tables
