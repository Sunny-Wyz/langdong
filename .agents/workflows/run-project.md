---
description: 一键启动备件管理系统（前端 Vue 和后端 Spring Boot）
---

此工作流用于在 Windows 环境下一键查杀旧进程，并启动后端的 Spring Boot 服务和前端的 Vue 服务。

1. **清理占用 8080 端口的旧后端进程**
// turbo
```powershell
cmd /c "for /f \"tokens=5\" %a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do taskkill /PID %a /F"
```

2. **编译并启动后端服务 (Spring Boot)**
// turbo
```powershell
cd backend
mvn package -DskipTests -q
start "Spare-Backend" cmd /k "java -jar target\spare-1.0.0.jar"
```

3. **启动前端服务 (Vue CLI)**
// turbo
```powershell
cd frontend
start "Spare-Frontend" cmd /k "npm run serve"
```

> **注意**: 使用 `start` 命令会为前后端分别弹出独立的命令提示符窗口，方便您直接查看各自的启动日志和请求输出。当看到前端提示 `App running at: http://localhost:3000/` 时即可在浏览器访问。默认账号 `admin`，密码 `123456`。
