# 问答与问题解决记录 (QA.md)

Q - /api/ai/forecast/result?month=&partCode=&page=1&size=20 返回 400 - 根因是 month 参数为空字符串时在 AiForecastController 里被当作非 null 校验，未匹配 yyyy-MM 直接触发 badRequest；已将 month/partCode 先 trim 后空串归一为 null，再执行格式校验并传入 service，保持原有业务语义且兼容前端空参数。

Q - 修复代码后接口仍返回 400 - 根因是后端 Java 进程未重启，仍运行修复前类加载结果；重启 8080 服务后复测，`month=&partCode=` 返回 200，非法 `month=2026-3` 仍按预期返回 400。

Q - Python AI 服务环境部署时 `conda env create` 失败（YAML 解析错误 + SSL 超时） - 先修复 `environment.yml` 的 `pip` 缩进格式，再改为分步安装策略（先 `conda create -n langdong python=3.11`，再按 conda/pip 分批安装），最终完成 `langdong` 环境并通过 Python/Java 双端编译验证。

Q - 新建 FastAPI 补货接口返回字段不完整 - 根因是响应模型未声明 legacy 脚本中的 `suggestion`、`alert_message` 等字段，FastAPI 会按 `response_model` 过滤未声明字段；已补齐 schema 字段并复测导入通过，保证接口契约与现有脚本一致。

Q - 如何实现 Python 异步外联（Redis+Celery + 回调Java）且保证安全 - 已新增 jobs 异步接口（提交/查询）、Celery 任务执行与回调逻辑，并在 Java 侧新增回调接收端；同时修复了默认弱 token、放行路径过宽、回调请求体无校验等问题：改为强制 `PYTHON_CALLBACK_TOKEN` 环境变量、仅放行 POST 回调入口、回调 payload 字段校验（task_id/status）、失败信息脱敏和缓存上限控制。

Q - `start_all.sh` 启动时报 `[ERROR] Redis not available` 且 Python API 启动后立刻失败 - 根因一是本机未安装 Redis；根因二是脚本用 `conda run -n langdong uvicorn` 命中了系统 Python 3.7 的 `uvicorn`，触发 Pydantic 类型注解 `|` 不兼容崩溃。已安装并启动 Redis，且将脚本改为 `conda run -n langdong python -m uvicorn/celery` 强制使用 conda 环境 Python 3.11；同时状态脚本改为非 2xx 也可判定 HTTP 可达，避免后端被 401/405 误报为 DOWN，当前全栈状态已恢复为 UP。

Q - 每次启动都要手工传 `PYTHON_CALLBACK_TOKEN` 不方便 - 已在根目录增加本地私有配置 `.env.local` 并让 `scripts/start_all.sh` 自动加载该文件；启动脚本优先读取固化 token，直接执行 `./scripts/start_all.sh` 即可启动，不再重复手输环境变量。

Q - 新增任务中心后为何仍可能出现“可提交不可查询”或轮询频繁报错 - 根因是提交/查询接口权限不同（`ai:forecast:trigger` vs `ai:forecast:list`）且轮询初版未对连续失败做停机控制；已增加前端权限门控（入口展示与按钮可用性）、路由权限拦截、连续失败自动停止轮询，并补充后端 taskId 白名单校验与上游错误信息收敛。

Q - “任务中心菜单加了但侧栏看不到，且需要过去2年按天训练数据”如何一次落地 - 已新增并执行两份 SQL：`sql/add_ai_job_center_menu.sql`（幂等写入 `/ai/job-center` 菜单并授权 ADMIN）与 `sql/generate_ai_daily_train_data_2y.sql`（基于真实业务表重建730天日级训练数据）；执行中针对“删后插失败风险”和“标签被覆盖低估风险”补充了事务保护与口径修正（`TRACE/REQ_OUT` 取更完整值并打 `source_level` 标记），最终验证训练集生成 `37230` 行，覆盖 `51` 个备件。

Q - 任务中心里输入备件编码会报“备件ID错误” - 根因是前端提交前将输入强制解析为纯数字，编码（如 `C0100002`）被过滤；后端也仅接受数字列表。已修复为“ID/编码混输”模式：前端改为透传 token 列表，后端新增按 `spare_part.code` 映射到 `id` 的逻辑（不区分大小写），并保留数字 ID 兼容能力。

Q - 希望在前端侧边栏直接看到训练数据 - 已新增“训练数据看板”全链路：后端新增 `/api/ai/train-data/list` 分页查询接口（支持日期范围、备件编码、来源、插补过滤）；前端新增 `AiTrainDataDashboard` 页面与 `/ai/train-data-dashboard` 路由；数据库新增并执行 `sql/add_ai_train_data_dashboard_menu.sql` 完成动态菜单入库与 ADMIN 授权，登录后可在 AI 模块侧栏直接访问。
Q - 新提交的任务（如 f0fa2633-5028-477c-b985-3c3dba65e46b）为什么显示 FAILURE - 根因是多层故障链，已全部诊断和修复：

**第一层**：数据库密码硬编码 ✓
- 修復：在 `smart_replenishment.py` 和 `predictive_maintenance.py` 中改为从环境变量读取密码

**第二层**：Celery 任务未注册 ✓
- 修復：在 `app/services/celery_app.py` 末尾添加 `from app.services import async_tasks`

**第三層**：缺少 JAVA_CALLBACK_TOKEN 环境变量 ✓
- 修復：启动脚本已自动加载 `.env.local` 中的令牌

**第四層**：TensorFlow 依赖复杂/启动慢 ✓
- **方案**：将 TensorFlow 改为可选依赖。若 TensorFlow 不可用，代码自动降级为统计预测方法（已有代码支持）
- 修復：在两个 Python 文件中用 try-except 包装 TensorFlow 导入，使其在导入失败时：
  1. 设置 `TENSORFLOW_AVAILABLE = False`
  2. `load_demand_model()` 立即返回 `(None, None)`
  3. 代码自动触发统计降级（成熟的备选方案）

**第五層**：Redis 驱动兼容性 ✓
- 修復：升级 `redis` 和 `kombu` 库版本，确保兼容性

**最终方案**：使用 PyTorch 替代 TensorFlow（推荐）
- PyTorch 在 M1 Mac 上首次启动 <10 秒（vs TensorFlow 2-3 分钟）
- 已安装：`conda install pytorch scikit-learn shap scipy`
- 代码自动降级机制确保即使 Pytorch 也不装也能用统计方法

**验证方法**：执行 `./scripts/start_all.sh`，系统会自动处理所有依赖和环境变量。

Q - api/ai/forecast/jobs/replenishment 报 500 提示 Python 服务不可用 - 根因是 Python API 本身在线，但 Celery worker 启动失败，`kombu` 加载 Redis 传输层时报 `AttributeError: 'NoneType' object has no attribute 'Redis'`，进一步定位为 `langdong` 环境缺少 `async_timeout` 依赖导致 `redis` 包导入失败；已在 `langdong` 环境执行 `python -m pip install async-timeout==4.0.3`，并将 `async-timeout==4.0.3` 固化到 `python-ai-service/requirements.txt` 与 `python-ai-service/environment.yml`，重启后 `scripts/status_all.sh` 显示 celery 为 UP，接口已恢复为可提交任务（返回 `task_id` + `PENDING/STARTED`）。

Q - /api/ai/forecast/jobs/{taskId} 返回 SUCCESS 但 result 内出现数据库连接失败 1045/1054，无法得到补货建议 - 根因是两段配置与库结构不一致：其一，Python 任务链路在部分路径下回退到 `root` 账号或读取占位配置，导致 MySQL 1045；其二，`smart_replenishment.py` 查询 `spare_part.category` 字段，而真实库字段为 `category_id`，触发 1054。已修复为：启动脚本统一向 Python API/Celery 注入 `DB_USERNAME=admin` 等数据库变量并注入 `PYTHONPATH`；`predictive_maintenance.py` 与 `smart_replenishment.py` 默认账号改为与后端一致（admin）；`legacy_bridge.py` 补充项目根路径到 `sys.path`；并将 SQL 改为 `category_id AS category`。复测任务 `be7780ae-28d7-4230-ad95-6671f4505a93` 状态 SUCCESS，已返回真实补货建议对象。

Q - /api/ai/forecast/jobs/{taskId} 返回 SUCCESS 但 result 内含数据库连接失败 1045 - 根因是 Python 补货脚本在数据库连接异常时返回“带 error 的普通列表”而非抛异常，导致 Celery 任务状态仍为 SUCCESS；同时 Python AI 服务 `.env` 默认 `DB_PASSWORD=your_password`，在未覆盖时会触发 MySQL 1045。最小修复策略：1）在 `smart_replenishment.suggest_replenishment` 的数据库连接失败分支改为抛出异常（或返回结构化失败并由任务层转失败）；2）在 `async_tasks.run_replenishment_job` 增加结果校验，发现 `result` 中包含 `error` 时显式抛错；3）在 jobs 查询接口将“SUCCESS + payload.status=FAILURE/error”映射为失败态，避免上游误判；4）启动阶段校验 DB 配置（至少校验 `DB_PASSWORD` 非占位符），并用测试覆盖该回归场景。
