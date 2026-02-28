---
name: data-preservation
description: Rules for preserving critical data (admin user, base roles) when writing or updating mock data scripts in the langdong project.
---

# Data Preservation Rules for Mock Scripts

When writing, updating, or generating SQL mock data scripts (like `mock_data.sql`), **you MUST preserve the critical system data**. Failing to do so will break the application login and role-based access control (RBAC).

## Critical Rules to Follow

1. **NEVER blanket TRUNCATE the `user` or `user_role` tables without restoring the admin account.**  
   If you must clear these tables to insert test data, you **MUST** ensure the `admin` account (ID 1) and its `ADMIN` role association are re-inserted exactly as they appear in `init.sql`.

2. **The `admin` Account Schema:**
   The `admin` account is hardcoded to `ID = 1`.  
   - `INSERT INTO user (id, username, name, password, status) VALUES (1, 'admin', '系统管理员', '$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa', 1);`
   - `INSERT INTO user_role (user_id, role_id) VALUES (1, 1);`

3. **Auto-Increment ID Shifting:**
   When using Python scripts or loops to insert test users (e.g., `testuser_1` to `testuser_10`), **DO NOT start inserting users at ID 1.** Test users should be explicitly assigned IDs starting from 2, or their IDs will conflict with or overwrite the admin account if the table is truncated.
   - Good: `INSERT INTO user (id, username, password, name) VALUES (i+1, 'testuser_i', ...)`
   - Bad: Letting `AUTO_INCREMENT` start from 1 after a TRUNCATE.

4. **Verify Role Mappings (`user_role`):**
   If you generate test users, ensure their role mappings in the `user_role` table correctly point to their new `user_id` values, taking into account that the admin occupies `ID = 1`.

## Common Pitfalls
* Using `TRUNCATE TABLE user_role;` and forgetting to restore `(1, 1)` for the admin.
* Writing a loop that implicitly assumes the new test users will get IDs 1-10, mismatching existing manual role assignments.
