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

Q - AI任务中心重算成功后，需求预测结果页没有体现重算值 - 根因是异步回调结果仅用于任务状态展示，未写入 `ai_forecast_result`；结果页 `AiForecastResult` 查询的仍是旧表数据。解决方案：在 `PythonCallbackController` 的 SUCCESS 分支接入 `AiForecastService.applyAsyncForecastResult`，将回调 `result` 映射为 `AiForecastResult` 并按 `partCode + forecastMonth` 先删后插覆盖写入，保持幂等；原 `/api/ai/forecast/trigger` 全量重算逻辑不变。联调已验证覆盖生效。

Q - “预测需求”和“预测消耗量”数值不一致，用户难以理解 - 根因是两个指标口径不同：任务中心展示的是多月需求口径（`predicted_demand.total`），结果页展示的是单月口径（`predictQty`），未并排展示导致认知偏差。解决方案：同时执行两项优化：1）结果页新增 `demand3Months` 字段展示“未来3个月累计需求”；2）任务中心结果详情新增“未来3个月总需求”列并明确命名。后端在 `queryResult` 中计算并返回 `demand3Months`，前端双页同步展示，保持原有重算逻辑不变。

Q - 结果页“未来3个月累计需求”可能与文案不一致、且回调覆盖后补货建议未同步 - 根因一是累计算法按“从起始月向后取前3条可用记录”而非“连续3个自然月”，缺失月份会被跳过，语义偏差；根因二是异步回调覆盖只写 `ai_forecast_result`，未触发 `StockThresholdService` 重算，可能出现“预测值更新但补货建议仍旧值”。解决方案：在 `AiForecastService` 将累计算法改为按 `startMonth/startMonth+1/startMonth+2` 严格求和（缺失月按0）；并在 `applyAsyncForecastResult` 写库后按受影响 `month + partCode` 重新构造上下文并调用 `stockThresholdService.recalcAndPush`，保证回调链路与主重算链路的联动一致。

Q - AI 任务中心重算后“预测消耗量”几乎都为 5 - 根因是 Python 补货逻辑在无月度消耗数据时会进入默认回退分支：`predict_demand` 中 `len(df)==0` 时将 `monthly_demand` 固定为 `[5.0, 5.0, 5.0]`；同时 Java 回调映射使用 `monthly_detail[0]` 写入 `predictQty`，于是结果页显示为 5。实测数据库 `spare_part_consumption_log` 当前总行数为 0（空表），与该回退路径一致。解决方案：补齐 `spare_part_consumption_log` 历史数据（至少每个备件 1~6 个月）后重算；或将默认值逻辑改为基于真实业务表动态估算，避免固定 5。

Q - 月度消耗日志表为空导致模型训练与预测失真，且手动重算缺少进度可视化 - 根因是 `smart_replenishment.py` 仅从 `spare_part_consumption_log` 读取训练数据；当该表无数据时走固定值回退，且需求预测页面手动触发后无状态查询接口支撑轮询。解决方案：1）训练数据改为优先从真实业务表 `biz_requisition_item + biz_requisition` 聚合月度消耗（`OUTBOUND/INSTALLED`、`out_qty>0`、按 `COALESCE(approve_time, apply_time)` 分月）；2）无数据回退改为动态估算（备件历史→同类目→全局→最小保护值），并返回 `fallback_source`；3）新增重算状态链路：后端提供 `/api/ai/forecast/trigger/status`，前端 `AiForecastResult` 增加进度条与轮询，进入页面若发现 RUNNING 自动继续轮询，完成后自动刷新列表。

Q - `/api/ai/forecast/trigger/status` 返回 403，页面每次进入都提示“获取重算进度失败” - 根因是进度接口权限仅配置为 `ai:forecast:list`，而部分用户仅有触发权限；同时前端页面创建时无条件请求状态接口且以非静默模式执行，导致每次进入页面都弹错误。解决方案：后端将进度接口权限调整为 `hasAnyAuthority('ai:forecast:list','ai:forecast:trigger')`；前端增加 `hasProgressPermission` 守卫，仅有权限时请求状态接口，首次请求改为静默，并对 403 做静默停止轮询处理，避免重复弹错。

Q - 进度弹窗不再出现但 `/api/ai/forecast/trigger/status` 仍是 403 - 根因是后端与前端修复已生效后，403 变为真实权限结果：当前登录账号未被授予 `ai:forecast:list` 或 `ai:forecast:trigger`。已验证后端运行进程已重启并加载新注解；库内权限映射显示仅 `ADMIN/EQUIPMENT_ENGINEER/MANAGEMENT/SYSTEM_ADMIN` 拥有上述权限，`BUYER` 角色无该权限。解决方案：给当前账号追加具备预测权限的角色，或在 `role_menu` 中给其现有角色授权 `ai:forecast:list`/`ai:forecast:trigger` 菜单权限。

Q - AI 任务中心任务 `4a67972d-7b7c-4602-a4a4-f963baa3c1be` 状态为 FAILURE（TASK_FAILED） - 根因是 `smart_replenishment.py` 新增真实业务聚合 SQL 在 MySQL `ONLY_FULL_GROUP_BY` 模式下触发 1055（分组表达式与选择表达式不一致）：`load_consumption_data_from_business` 中 `SELECT STR_TO_DATE(DATE_FORMAT(...))` 与 `GROUP BY DATE_FORMAT(...,'%Y-%m')` 不兼容，导致 Celery 任务抛 `OperationalError(1055)` 并回调失败态。解决方案：将月字段统一为 `DATE_FORMAT(...,'%Y-%m-01')` 字符串，并按相同表达式分组排序，消除 1055。复测：新任务 `5748c34e-4da4-4849-bf14-fc49251b1cf9` 已 SUCCESS，返回完整 `result` 与回调数据。

Q - AI 任务中心“未来3月总需求”和需求预测结果页不一致，且异步回调后的补货建议写入存在覆盖错位 - 根因有两点：1）异步回调映射优先写 `monthly_detail[0]`（首月）而任务中心展示 `predicted_demand.total`（3个月总量），口径不一致；2）回调若含多月明细，阈值联动会在同一建议月份反复覆盖，导致建议数据偏移。解决方案：在 `AiForecastService.applyAsyncForecastResult` 中按 `monthly_detail` 展开写入连续月份记录（无明细时再回退 total/predict_qty），并按月级置信区间写入上下界；在 `StockThresholdService.recalcAndPush` 中将建议月份改为 `forecastMonth`（非法值回退当前月），同时按 `min(forecasts, contexts)` 安全遍历并跳过空值，避免三个月数据互相覆盖。已完成后端编译与测试验证通过。
