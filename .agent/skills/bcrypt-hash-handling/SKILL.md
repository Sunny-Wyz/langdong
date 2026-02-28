---
name: bcrypt-hash-handling
description: Best practices for handling and inserting BCrypt password hashes in SQL scripts to avoid bash string interpolation corruption.
---

# Safe Handling of BCrypt Hashes in SQL

When inserting or updating BCrypt password hashes (which regularly contain `$` characters like `$2a$10$...`) via scripts or command-line wrappers, extreme care must be taken to prevent the host shell (e.g., bash) from interpreting the `$` as an environment variable and corrupting the hash.

## The Problem
Running an inline insert/update command like this will silently corrupt the hash because bash evaluates `$2a` and `$10` as empty variables before passing the string to MySQL:
```bash
# BAD: Bash will interpolate $2a and $10, corrupting the inserted password!
mysql -u root -e "UPDATE user SET password = '$2a$10$LaRzdak9...' WHERE username = 'admin';"
```

If you generate SQL files using bash `cat << EOF > file.sql` or similar heredocs, the same bad interpolation happens unless quotes are used around the EOF delimiter.

## Best Practices

### 1. Native File Creation (Recommended)
Always place BCrypt hashes inside a plain `.sql` file created by a proper file-writing tool (e.g. your native `write_to_file` agent tool or an IDE text editor) where bash variable expansion does not apply.

```sql
-- Inside fix_passwords.sql
UPDATE user SET password = '$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa' WHERE username = 'admin';
```
Then execute the file using MySQL input redirection:
```bash
mysql -u user -p db_name < fix_passwords.sql
```

### 2. Disabling Heredoc Interpolation
If you MUST use bash heredocs to create an SQL file containing hashes, quote the `EOF` marker (`'EOF'`) to completely disable variable interpolation for the entire text block:
```bash
# GOOD: The single quotes around 'EOF' prevent bash from expanding $ variables
cat << 'EOF' > fix_admin.sql
UPDATE user SET password = '$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa' WHERE username = 'admin';
EOF
```

### 3. Python Generation
When generating SQL inserts via Python, pass the raw BCrypt string directly into the file writer. Python will natively ignore `$` symbols because it doesn't use them for substitution.
```python
hash_val = "$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa"
f.write(f"INSERT INTO user (username, password) VALUES ('admin', '{hash_val}');\n")
```

### 4. Escaping in Inline Bash Commands (Discouraged)
If executing directly via `-e`, you must aggressively escape the `$` variables with `\\` depending on the quotes used. Because this is highly error-prone to edge cases across environments, it should generally be avoided in favor of writing a pure `.sql` file first.
```bash
# Strongly discouraged due to escape complexity. Use a .sql file instead.
mysql -e "UPDATE user SET password = '\\$2a\\$10\\$LaRzdak9...'"
```
