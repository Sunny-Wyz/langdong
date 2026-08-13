# 运维手册 (Runbook)

## 目录

- [部署流程](#部署流程)
- [健康检查](#健康检查)
- [常见故障处理](#常见故障处理)
- [回滚操作](#回滚操作)
- [数据库维护](#数据库维护)

---

## 部署流程

### 后端部署

```bash
# 1. 在项目根目录拉取最新代码
git pull origin master

# 2. 打包
cd backend
mvn clean package -DskipTests

# 3. 停止当前进程（如使用 pm2 或 systemd）
# pm2 stop spare-backend
# 或: kill $(lsof -ti:8080)

# 4. 修改生产配置（确保不使用默认密码）
# 编辑 application.yml 或使用环境变量覆盖：
# export SPRING_DATASOURCE_PASSWORD=<prod_password>
# export JWT_SECRET=<64字符以上的随机字符串>
# export APP_CORS_ALLOWED_ORIGIN=https://your-domain.com

# 5. 启动
java -jar target/spare-1.0.0.jar \
  --spring.datasource.password=${DB_PASSWORD} \
  --jwt.secret=${JWT_SECRET} \
  --app.cors.allowed-origin=${CORS_ORIGIN}

# 或用 pm2 管理：
# pm2 start "java -jar target/spare-1.0.0.jar" --name spare-backend
```

### 前端部署

```bash
cd frontend

# 1. 安装依赖
npm ci

# 2. 生产构建
npm run build

# 3. 将 dist/ 部署至 Web 服务器（Nginx 等）
# cp -r dist/* /var/www/spare/
```

### Nginx 参考配置

```nginx
server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    root /var/www/spare;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 反向代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## 健康检查

### 后端

```bash
# 检查进程是否运行
curl -s http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | jq .

# 预期返回：包含 token 字段的 JSON
```

### 数据库

```bash
# 检查连接和关键表
mysql -u admin -p spare_db -e "
  SELECT 'users' AS tbl, COUNT(*) AS cnt FROM user
  UNION ALL
  SELECT 'menus', COUNT(*) FROM menu
  UNION ALL
  SELECT 'spare_parts', COUNT(*) FROM spare_part;
"
```

### 前端

访问 `http://localhost:3000`（开发）或部署域名，能看到登录页则正常。

---

## 常见故障处理

### 后端启动失败：`Access denied for user`

**原因**：数据库用户名或密码错误。

**处理**：
```bash
# 检查配置
grep -A3 "datasource" backend/src/main/resources/application.yml

# 测试数据库连接
mysql -u admin -p spare_db -e "SELECT 1;"
```

---

### 后端启动失败：`Table 'spare_db.xxx' doesn't exist`

**原因**：SQL 迁移脚本未完整执行。

**处理**：
```bash
# 检查当前数据库中的表
mysql -u admin -p spare_db -e "SHOW TABLES;"

# 按需补充缺失的迁移脚本
mysql -u admin -p spare_db < sql/<missing_module>.sql
```

---

### 接口返回 401 Unauthorized

**原因**：JWT Token 缺失、过期或无效。

**处理**：
- 前端：清除 localStorage 重新登录
- 排查：`jwt.expiration` 是否配置合理（默认 86400000ms = 24h）

---

### 接口返回 403 Forbidden

**原因**：`@PreAuthorize` 权限校验失败，当前角色未分配该权限。

**处理**：
1. 登录管理员账号
2. 进入 系统管理 → 角色与权限分配
3. 找到对应角色，勾选缺失的菜单/按钮权限
4. 重新登录相关用户账号

---

### AI/分类任务无结果

**原因**：
1. 历史消耗数据不足（需至少 6~12 个月）
2. `@EnableScheduling` / `@EnableAsync` 未生效
3. 定时任务 Cron 表达式配置问题

**处理**：
```bash
# 手动触发分类重算（需 classify:trigger:run 权限）
curl -X POST http://localhost:8080/api/classify/trigger \
  -H "Authorization: Bearer <admin-token>"

# 手动触发 AI 预测（需 ai:forecast:trigger 权限）
curl -X POST http://localhost:8080/api/ai/forecast/trigger \
  -H "Authorization: Bearer <admin-token>"
```

---

### 前端白屏 / 路由失效

**原因**：Nginx 未配置 `try_files $uri /index.html`（SPA 路由回退）。

**处理**：参考上方 [Nginx 参考配置](#nginx-参考配置)，添加 `try_files` 指令后重载 Nginx。

---

### CORS 错误（跨域）

**原因**：`app.cors.allowed-origin` 与前端实际访问地址不符。

**处理**：
```yaml
# 修改 application.yml
app:
  cors:
    allowed-origin: https://your-actual-frontend-domain.com
```
重启后端后生效。

---

## 回滚操作

### 代码回滚

```bash
# 查看最近提交
git log --oneline -10

# 回滚到指定 commit（本地）
git reset --hard <commit-hash>

# 重新打包部署
cd backend && mvn clean package -DskipTests
```

### 数据库回滚

> ⚠️ 数据库变更不可自动回滚，需要手工操作。

```bash
# 回滚前，先备份当前数据库
mysqldump -u admin -p spare_db > backup_$(date +%Y%m%d_%H%M%S).sql

# 如果迁移脚本有对应的回滚语句，手动执行
mysql -u admin -p spare_db -e "ALTER TABLE xxx DROP COLUMN yyy;"
```

---

## 数据库维护

### 常规备份

```bash
# 每日备份（建议加入 crontab）
mysqldump -u admin -p spare_db \
  --single-transaction \
  --routines \
  --triggers \
  > /backup/spare_db_$(date +%Y%m%d).sql
```

### 查看表行数（数据量检查）

```bash
mysql -u admin -p spare_db -e "
  SELECT
    table_name,
    table_rows AS est_rows,
    ROUND(data_length / 1024 / 1024, 2) AS data_mb
  FROM information_schema.tables
  WHERE table_schema = 'spare_db'
  ORDER BY data_length DESC;
"
```

### 重置管理员密码

管理员密码使用 BCrypt 加密，需要通过后端 API 修改：

```bash
# 使用管理员 token 调用修改接口
curl -X PUT http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"password": "new-strong-password"}'
```
