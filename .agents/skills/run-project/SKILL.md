---
name: run-project
description: 启动完整的备件管理系统的前后端服务。包括检查端口占用、清理旧进程，以及重新编译启动。
---

# 项目启动指南

此技能记录了如何在本地开发环境中启动我们的 Spring Boot + Vue 2 项目，核心解决了因为以往遗留进程导致的 8080 端口占用和 404 错误。

你也可以随时对助手说 **“启动项目”** 或者 **`/run-project`** 来触发同名的自动化工作流。

## 1. 彻底清理旧的后端进程

如果之前尝试过 `mvn spring-boot:run` 然后通过 `Ctrl+C` 退出，大概率 Java 进程仍然在后台挂起并占用 8080 端口，这会导致后续无论怎么启动新代码都不会生效（前端访问抛出 404）。

每次启动前，建议先杀掉旧进程：

```powershell
# 查找并强杀 8080 端口的进程 (Windows PowerShell)
cmd /c "for /f `"tokens=5`" %a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do taskkill /PID %a /F"
```

## 2. 编译并启动后端 (Spring Boot 3 + Java 17)

相较于直接运行 `mvn spring-boot:run` 时可能产生的日志截断或挂起现象，使用 `java -jar` 直接运行打包好的二进制文件更为直观和稳定：

```powershell
cd backend
# 跳过测试，快速打包
mvn package -DskipTests -q
# 运行 jar
java -jar target\spare-1.0.0.jar
```
*当控制台出现包含 `Started SpareApplication in xxx seconds` 和 `Tomcat started on port(s): 8080` 字样时，说明后端成功运行。*

## 3. 启动前端 (Vue 2)

前端基于 Vue CLI，运行在 `3000` 端口。

```powershell
cd frontend
npm run serve
```
*当输出 `App running at: - Local: http://localhost:3000/` 时，表示项目前端已就绪。*

## 测试与登录
- 系统入口：`http://localhost:3000/`
- 代理配置：前端发往 `/api/` 的请求将自动通过 VUE 的 `devServer.proxy` 转发到 `http://localhost:8080`。
- **默认管理员账号**：`admin`
- **默认管理员密码**：`123456`
