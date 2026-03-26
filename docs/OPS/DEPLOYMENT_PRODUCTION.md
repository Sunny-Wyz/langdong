# 生产部署与发布流程

最后更新: 2026-03-26
适用系统: Spring Boot 3.2 + Vue2

---

## 1. 环境矩阵

| 环境 | 用途 | 域名/地址 |
|---|---|---|
| dev | 开发联调 | 本地或内网 |
| staging | 预发布验证 | 预发布域名 |
| prod | 生产服务 | 正式域名 |

---

## 2. 发布前检查

1. 代码已合入主分支
2. 后端构建通过
3. 前端构建通过
4. 数据库迁移脚本已评审
5. 关键配置不含明文秘密
6. 回滚预案已准备

命令:
```bash
cd backend && mvn clean package -DskipTests
cd ../frontend && npm ci && npm run build
```

---

## 3. 标准发布流程

## 3.1 数据库

1. 先备份数据库
2. 执行本次新增迁移脚本（按顺序）
3. 校验关键表结构和行数

```bash
mysqldump -u admin -p spare_db > backup_before_release.sql
mysql -u admin -p spare_db < sql/<release_migration>.sql
```

## 3.2 后端

1. 停止旧进程
2. 启动新版本
3. 做健康检查

```bash
cd backend
java -jar target/spare-1.0.0.jar \
  --spring.datasource.password=${DB_PASSWORD} \
  --jwt.secret=${JWT_SECRET} \
  --app.cors.allowed-origin=${CORS_ORIGIN}
```

## 3.3 前端

1. 构建 dist
2. 上传至静态目录
3. reload Nginx

```bash
cd frontend
npm run build
# 同步 dist 到 nginx root
# nginx -s reload
```

---

## 4. 发布后 10 分钟验收

1. 登录接口可用
2. 看板 KPI 可正常加载
3. 领用/FIFO 追溯接口可访问
4. 分类与预测触发接口可执行
5. 预警列表可返回数据

建议冒烟:
```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

---

## 5. 回滚流程

## 5.1 应用回滚

1. 切回上一版本 jar 或镜像
2. 重启应用
3. 复验关键接口

## 5.2 数据库回滚

1. 优先使用迁移脚本对应 rollback
2. 若无 rollback，使用发布前备份恢复

```bash
mysql -u admin -p spare_db < backup_before_release.sql
```

说明:
- 生产流程禁止使用破坏性 git 重置作为回滚手段
- 回滚后必须再次执行冒烟验证

---

## 6. 故障处理入口

参考: [RUNBOOK.md](../RUNBOOK.md)

出现严重故障时:
1. 先止损（回滚或降级）
2. 再定位（日志/SQL/资源）
3. 最后复盘（原因、影响、行动项）

---

维护人: 运维团队
版本: 1.0
