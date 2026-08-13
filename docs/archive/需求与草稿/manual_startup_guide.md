# 备件管理系统手动启动指南 (Manual Startup Guide)

如果您希望在独立的终端窗口中手动启动并监控各个服务的实时日志，请按照以下步骤依次操作。

---

## 准备工作：环境变量与配置文件
本地开发默认读取项目根目录下的 `.env.local` 配置文件：
* 数据库用户名：`admin`
* 数据库密码：`123456`
* 本地 Token：`langdong-local-dev-20260330`

---

## 第一步：启动基础中间件 (MySQL & Redis)

请打开一个终端窗口，执行以下命令拉起数据库和缓存：

### 1. 启动 MySQL 数据库 (Anaconda 版)
```bash
/opt/anaconda3/bin/mysql.server start
```
*验证连接*：可以使用 `mysql -u admin -p123456 -h 127.0.0.1` 确认是否连接正常。

### 2. 启动 Redis 缓存
```bash
brew services start redis
```

---

## 第二步：启动 Python AI 算法微服务

请打开一个新的终端窗口（**终端窗口 A**），执行以下命令：

```bash
# 1. 进入 Python 项目目录
cd /Users/weiyaozhou/Documents/langdong/python-ai-service

# 2. 激活 Conda 算法环境
conda activate langdong

# 3. 注入数据库与 Token 环境变量
export DB_USERNAME=admin
export DB_PASSWORD=123456
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=spare_db
export JAVA_CALLBACK_TOKEN=langdong-local-dev-20260330
export PYTHONPATH="/Users/weiyaozhou/Documents/langdong"

# 4. 启动 FastAPI 接口服务 (端口 8000)
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

## 第三步：启动 Celery 异步任务队列

请打开一个新的终端窗口（**终端窗口 B**），执行以下命令：

```bash
# 1. 进入 Python 项目目录
cd /Users/weiyaozhou/Documents/langdong/python-ai-service

# 2. 激活 Conda 环境
conda activate langdong

# 3. 注入与微服务完全一致的环境变量
export DB_USERNAME=admin
export DB_PASSWORD=123456
export DB_HOST=127.0.0.1
export DB_PORT=3306
export DB_NAME=spare_db
export JAVA_CALLBACK_TOKEN=langdong-local-dev-20260330
export PYTHONPATH="/Users/weiyaozhou/Documents/langdong"

# 4. 启动 Celery 工作进程
python -m celery -A app.services.celery_app:celery_app worker -l info
```

---

## 第四步：启动 Java Spring Boot 后端服务

请打开一个新的终端窗口（**终端窗口 C**），执行以下命令：

```bash
# 1. 进入 Java 项目目录
cd /Users/weiyaozhou/Documents/langdong/backend

# 2. 指定 JDK 21 的安装路径
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jdk-21.0.11+10/Contents/Home

# 3. 注入 Token 与数据库配置
export PYTHON_CALLBACK_TOKEN=langdong-local-dev-20260330
export DB_USERNAME=admin
export DB_PASSWORD=123456

# 4. 运行 Spring Boot
/Users/weiyaozhou/IdeaProjects/apache-maven-3.8.8-bin/apache-maven-3.8.8/bin/mvn spring-boot:run
```

---

## 第五步：启动 Vite 前端服务

请打开一个新的终端窗口（**终端窗口 D**），执行以下命令：

```bash
# 1. 进入前端项目目录
cd /Users/weiyaozhou/Documents/langdong/frontend

# 2. 启动 Vite 开发服务器 (默认占用端口 3000)
npm run dev
```

启动完成后，您即可在浏览器中访问 [http://localhost:3000](http://localhost:3000) 登录系统。

---

## 🛑 如何手动关闭服务

* 在运行 Python、Celery、Java 和前端的终端窗口中，直接按 **`Ctrl + C`** 即可终止对应的服务。
* **关闭 MySQL 数据库**：
  ```bash
  /opt/anaconda3/bin/mysql.server stop
  ```
* **关闭 Redis 缓存**：
  ```bash
  brew services stop redis
  ```
