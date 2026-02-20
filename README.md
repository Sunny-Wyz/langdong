# 备件管理系统

技术栈：Spring Boot 3.x + MyBatis + MySQL | Vue 2 + Element UI

## 快速开始

### 1. 数据库
```bash
mysql -u root -p < sql/init.sql
```

### 2. 后端
修改 `backend/src/main/resources/application.yml` 中的数据库密码，然后：
```bash
cd backend
mvn spring-boot:run
```
后端运行在 http://localhost:8080

### 3. 前端
```bash
cd frontend
npm install
npm run serve
```
前端运行在 http://localhost:3000

## 默认账号
- 用户名：`admin`
- 密码：`123456`
