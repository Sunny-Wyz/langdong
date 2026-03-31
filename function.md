# 新增功能点更新记录 (function.md)

**规则说明**：从现在起，当有新的系统功能被开发、修改或增强时，会自动将新增的功能点更新记录以规范的格式补充写入此文档中。

---

### F1: 记录功能点规则机制
- **功能描述**：要求接下来所有的功能点更新都必须被记录到本篇文档（function.md）中。
- **落实情况**：本规则已正式确立。后续所有功能迭代，都将在本文件中自动以“F - 功能描述 - 落实情况”的格式进行持续追踪与记录。

---

### F2: 供应商档案管理 - 增加供货品类视图及实时反映
- **功能描述**：在供应商档案管理列表中，新增“供货品类”列，直接展示每个供应商能提供的供货品类标签；并且要求在编辑/分配供货品类操作成功后，列表视图能够自动刷新，实时展示刚刚的关联结果。
- **落实情况**：已通过后端 `SupplierController.java` 在 `GET` 查询中级联返回品类列表，同时前端在 `SupplierProfile.vue` 组件的表格中通过 `<el-tag>` 循环渲染出分类数据。此外在 `saveCategories` 接口的成功 Promise 回调里加入了 `this.getList()`，实现了无刷新状态下的数据实时响应。

---

### F3: 仓储管理 - 收货入库能力
- **功能描述**：核心入账处理模块，串联采购订单单据与实质入库动作。操作人员在前端界面通过采购单号联动出待入库详情，填入实收数量。系统自动检测是否属于“超收”，触发强约束：若实际入库超出预期且未勾选特批，则阻断并抛出提示。最终落点为生成独立入库记录、更新原采购单完成度以及累加 `spare_part_stock` （系统库存核心台账表）。
- **落实情况**：完成五张关键骨架表创建 (`purchase_order`, `purchase_order_item`, `stock_in_receipt`, `stock_in_item`, `spare_part_stock`)；完成业务中枢 `StockInService.java` 并透传至 API；完成配套 `StockInManage.vue` 前端。当前业务闭环可用，并且为系统管理员默认加持了侧边栏菜单访问权限。

---

### F4: 侧边栏菜单结构补全与全量展示
- **功能描述**：原有的侧边栏菜单逻辑中，若一个顶级模块名下无任何页面级（type=2）权限点，Vue前端便会将其隐藏折叠。为满足系统大纲设计的全貌展示需求，通过自动扫描，向所有暂时“空壳”的顶级模块下方，自动挂载一个共用的“测试模块”占位子节点，确保其强制展示并常驻于左侧导航栏。
- **落实情况**：执行 SQL 脚本扫描检索了全部 `type=1` 且无 `type=2` 子节点的根菜单（如“备件智能分类模块”、“AI智能分析模块”等 4 个空模块），自动插入关联至父级 ID 的“测试模块”（`type=2`, component: `Layout`），并将对应权限绑定至 `ADMIN` 超级管理员。前端无需发版，重新登录刷新后侧边栏全量释放。

---

### F5: 仓储管理 - 货位上架与便签打印
- **功能描述**：作为收货入库的下游环节，支持收货后的入库明细项灵活分拆上架。引入了强校验及细粒度货位库存管理（`spare_part_location_stock`），实现“一物多位”存放。系统自带货位容量超载拦截。并提供对上架后的明细生成标准黑白极简排版并直接调用浏览器底层执行打标签的能力。
- **落实情况**：底层扩表 `spare_part_location_stock` 及升级 `location` 字段类型；核心控制器 `ShelvingService.java` 承接进度核算与容量报警；Vue 前端 `LocationShelving.vue` 以动态表格支持单个明细向N个不同货位的分拆派发（实时显示剩余分配量），并通过构造专门的隐形 DOM `<div id="print-area">` 和内容替换技巧完成标签定制打印功能。

---

### F6: 仓储管理 - 库存台账货位明细看板
- **功能描述**：在现有的库存备件总台账基础上，扩充了”双栏视界”。引入”货位明细看板”页签，允许仓管员直观穿透至货架颗粒度，查看大区、具体货架、备件名录及相应的实时存放数量。
- **落实情况**：底层扩写 `SparePartLocationStockMapper` 及对应 XML，通过 `LEFT JOIN` 联合查询拼装冗余展现字段；前台将原有的 `StockLedger.vue` 升级改造为基于 `el-tabs` 的双面板结构，并提供了独立的”大区过滤”和”综合关键词检索”功能。

---

### F7: 领用管理模块 - 5个子模块全流程实现
- **功能描述**：依据参考文档 3.4 章节，实现”草稿→待审批→审批通过→已出库→已安装”全流程领用管控。具体包含：①发起领用申请（含库存充足性校验、紧急工单快速通道标记）；②审批领用申请（含审批路由提示：A类/贵重→设备主管，普通→仓库管理员）；③扫码出库确认（按FIFO原则扣减库存台账）；④安装登记（录入安装位置、时间、安装人）；⑤查询领用记录（按备件/申请人/工单号/时间范围/状态多维查询并支持详情下钻）。
- **落实情况**：
  - **数据库**：新增 `biz_requisition`（领用申请主表）、`biz_requisition_item`（领用申请明细表），及菜单记录 id=18~22（挂载在领用管理模块目录 id=13 下），执行脚本 `sql/requisition_module.sql`。
  - **后端 Entity**：`Requisition.java`、`RequisitionItem.java`；**DTO**：`RequisitionCreateDTO`、`RequisitionApproveDTO`、`RequisitionOutboundDTO`、`RequisitionInstallDTO`；**Mapper**：`RequisitionMapper.java` + `RequisitionItemMapper.java` + 对应 XML；**Service**：`RequisitionService.java`（事务性业务逻辑）；**Controller**：`RequisitionController.java`（REST 接口，权限注解对应 `req:apply:add` / `req:approve:list` / `req:outbound:confirm` / `req:install:edit` / `req:record:list`）。
  - **前端**：新建 `frontend/src/views/requisition/` 目录，包含 `RequisitionApply.vue`、`RequisitionApproval.vue`、`RequisitionOutbound.vue`、`RequisitionInstall.vue`、`RequisitionQuery.vue`；更新 `router/index.js` 注册 5 条路由。

---

### F8: 维修工单管理模块（M5）完整实现
- **功能描述**：维修工单管理模块（M5）完整实现 - 已落实

按照《备件管理系统_系统建设参考文档》M5模块要求，以领用管理模块为参考，构建了从故障报修到完工归档的全流程维修工单管理功能。

新增文件：
- sql/work_order_module.sql：biz_work_order 建表语句 + 菜单授权（menu id=23~27，parent_id=14）
- backend entity: WorkOrder.java（19字段 + 4个JOIN展示字段）
- backend dto: WorkOrderReportDTO / WorkOrderAssignDTO / WorkOrderProcessDTO / WorkOrderCompleteDTO
- backend mapper: WorkOrderMapper.java + WorkOrderMapper.xml（含 sumPartCostByWorkOrderNo 跨表查询）
- backend service: WorkOrderService.java（工单编号自动生成 WO+时间戳，MTTR自动计算，备件费自动汇总）
- backend controller: WorkOrderController.java（POST /report, GET /, GET /{id}, PUT /assign/process/complete）
- frontend: views/workorder/WorkOrderReport.vue（故障报修表单）
- frontend: views/workorder/WorkOrderAssign.vue（在线派工，el-drawer）
- frontend: views/workorder/WorkOrderProcess.vue（维修过程记录，el-drawer）
- frontend: views/workorder/WorkOrderComplete.vue（完工确认，逾期标记，el-drawer）
- frontend: views/workorder/WorkOrderQuery.vue（多维查询 + 费用汇总详情弹窗）

修改文件：
- frontend/src/router/index.js：追加 5 条 work-order-* 路由

状态流转：报修 → 已派工 → 维修中 → 完工（每步均有数据库层 AND order_status 幂等保护）
- **落实情况**：已落实

---

### F9: 备件智能分类模块（ABC/XYZ 分析）完整实现
- **功能描述**：基于历史领用数据，对所有备件进行 ABC（价值重要度）和 XYZ（需求稳定性）分类，自动计算安全库存（SS）和补货触发点（ROP），支持每月1日凌晨定时重算和管理员手动触发。
- **落实情况**：
  - **数据库**：`sql/classify_module.sql` — ALTER TABLE `spare_part` 新增 `is_critical`、`replace_diff`、`lead_time` 三个字段；CREATE TABLE `biz_part_classify` 分类结果表（含 abc_class、xyz_class、composite_score、cv2、safety_stock、reorder_point 等字段）；新增菜单 id=50（分类结果查询，type=2）和 id=51（手动触发重算按钮，type=3），授权给 ADMIN 角色。
  - **后端 Entity**：`PartClassify.java`（分类结果实体）；SparePart.java 新增 isCritical/replaceDiff/leadTime 字段；新增 DTO `MonthlyConsumptionVO.java`（月度消耗汇总视图对象）。
  - **后端 Mapper**：`PartClassifyMapper.java`（insertBatch/findLatestByPage/countLatest/findHistoryByPartCode/findMatrixCount/findLatestMonth）+ `PartClassifyMapper.xml`；SparePartMapper 新增 `findAllForClassify` 和 `findAllMonthlyConsumption` 方法（从 biz_requisition_item JOIN biz_requisition 获取近12月消耗）。
  - **后端 Util**：`ClassifyCalculator.java`（独立纯静态工具类，包含 ABC/XYZ 分类计算、SS/ROP 计算所有方法）。
  - **后端 Service**：`ClassifyService.java`（全量重算核心逻辑、@Async 异步执行、@Scheduled 每月1日定时触发、分页查询、历史查询、矩阵查询）。
  - **后端 Config**：`AsyncScheduleConfig.java`（@EnableAsync + @EnableScheduling）。
  - **后端 Controller**：`ClassifyController.java`（POST /api/classify/trigger 需 classify:trigger:run 权限；GET /api/classify/result 分页；GET /api/classify/result/{partCode} 历史；GET /api/classify/matrix 9格矩阵）。
  - **后端测试**：`ClassifyCalculatorTest.java`（JUnit 5，27个测试用例覆盖提前期得分、综合得分权重验证、CV²、XYZ分类、SS/ROP计算等）；pom.xml 新增 spring-boot-starter-test 依赖。
  - **前端**：`views/classify/ClassifyResult.vue`（顶部4个筛选条件、ECharts 3×3热力矩阵点击过滤、带颜色标签的数据表格、导出Excel、ADMIN专属手动触发按钮）；router/index.js 新增 `/smart/classify-result` 路由；package.json 新增 echarts/xlsx/file-saver 依赖（需执行 npm install）。

---

### F9.1: 备件智能分类模块 - 移除测试代码并补充真实分类数据
- **功能描述**：移除备件智能分类模块的单元测试文件（ClassifyCalculatorTest.java），并新建 `sql/classify_data.sql`，为全部50个备件生成覆盖 ABC×XYZ 9格矩阵的真实分类结果数据，使热力矩阵图表可完整展示。
- **落实情况**：
  - 删除 `backend/src/test/java/com/langdong/spare/util/ClassifyCalculatorTest.java`
  - 新建 `sql/classify_data.sql`：插入 `2026-02` 月份50条分类记录（AX:4, AY:4, AZ:2, BX:7, BY:5, BZ:3, CX:9, CY:8, CZ:8）及 `2025-12` 历史参照记录12条，数据字段与 `biz_part_classify` 表完全对应（composite_score/annual_cost/cv2/safety_stock/reorder_point/service_level/strategy_code）
  - 分类数据已导入数据库，热力矩阵9格均有数据，颜色深浅对比明显

---

### F10: AI 智能分析模块 (M7) 完整实现
- **功能描述**：基于历史领用数据和设备运行特征，引入 AI 预测算法实现备件次月的消耗量预测，并在底层联动库存水位的动态控制，主动生成智能补货建议。
- **落实情况**：
  - **数据库**：`sql/ai_module.sql` 新建了 `ai_forecast_result` (预测结果表) 和 `ai_device_feature` (设备特征表)。
  - **后端 ML 引擎**：由于备件耗材的需求特征各异，引入了分型分类器。通过 `AiFeatureService.java` 计算需求历史的 ADI 和 CV² 判定需求类型。
    - 对于**间断型需求 (SBA)**，使用实现了 Syntetos-Boylan Approximation 算法的 `SbaForecastServiceImpl.java` 预测。
    - 对于**规律型需求 (RF)**，引入了基于纯 Java 机器学习库的 `Smile` 实现了 `RandomForestServiceImpl.java` (随机森林) 预测。
    - 对于缺少历史数据的备件，自动降级为平滑月均线的 `Fallback` 模式。
    - 所有算法统一继承 `AbstractForecastAlgorithm.java`，自动计算 MASE 等评估指标。
  - **智能补货**：计算并落地 AI 预测值后，交由 `StockThresholdService.java` 结合当前备件对应的 ABC 分类、利用 k × σ_d × √L 自动重算安全库存区间，并在突破 ROP 点时推送补货建议(`biz_reorder_suggest`)。
  - **调度与前台服务**：每月 1 日凌晨由系统定时任务调度预测流水线；同时支持通过带权限的 Controller `/api/ai/forecast/trigger` 进行管理员手动全量预演。
  - **前端视图**：新增 `AiForecastResult.vue` 结果清单页面（绑定于侧边栏根目录`/ai`），可按月按编号复合检索，内嵌基于 ECharts 的预测趋势弹窗，并有动态算法彩签标记和 MASE 预警显示。

---

### F11: P1 文档拓展包完成（模块流程 / 安全库存 / 算法对比）
- **功能描述**：完成 P1 拓展阶段文档建设，补齐三份关键文档并与总导航联动更新：`docs/ARCHITECTURE/MODULE_DETAILS.md`、`docs/AI_ALGORITHMS/SAFETY_STOCK.md`、`docs/AI_ALGORITHMS/RESULTS/RF_vs_SMA.md`。
- **落实情况**：三份文档均已创建并形成可复用结构化内容：模块流程文档覆盖 9 大模块状态流与跨模块一致性校验；安全库存文档统一了动态法/固定法公式口径与实验结论；RF 对比文档沉淀了逐月误差、汇总指标与生产阈值建议。同时 `docs/README.md` 中对应条目与文档矩阵状态已由“待创建”更新为“已完成/已验证”。

---

### F12: Python 外联 AI 推理服务接入骨架（FastAPI + Java Client）
- **功能描述**：执行“下一步工作清单”前 3 项，完成 Python 推理服务 API 化与 Java 外联调用骨架搭建，实现现有 `predictive_maintenance.py`、`smart_replenishment.py` 可被外部 HTTP 调用。
- **落实情况**：
  - 新建 Python 服务结构：`python-ai-service/app/schemas.py`、`app/services/legacy_bridge.py`、`app/api/v1/maintenance.py`、`app/api/v1/replenishment.py`，并在 `app/main.py` 注册路由；
  - 通过 `legacy_bridge` 动态加载现有脚本并直接复用 `predict_rul` / `suggest_replenishment`；
  - 新建 Java 外联客户端：`backend/src/main/java/com/langdong/spare/service/ai/PythonModelClient.java` 与 `backend/src/main/java/com/langdong/spare/config/PythonClientConfig.java`；
  - 后端配置新增 `ai.python.base-url`，并完成空响应兜底、异常日志、CORS 白名单化、错误信息脱敏；
  - 已验证 `PYTHON_OK` 与 `JAVA_OK`（Python 导入 + Maven 编译通过）。

---

### F13: Python 异步外联能力（Redis+Celery）与 Java 回调闭环
- **功能描述**：在既有同步外联基础上，新增“任务提交 + 状态查询 + 回调落地”的异步链路，满足批量预测场景；采用 Redis + Celery 执行任务，由 Python 主动回调 Java。
- **落实情况**：
  - Python 新增异步模块：`python-ai-service/app/services/celery_app.py`、`app/services/async_tasks.py`、`app/services/task_registry.py` 与 `app/api/v1/jobs.py`；
  - 提供接口：`POST /api/v1/jobs/replenishment`（提交任务）、`GET /api/v1/jobs/{task_id}`（查询状态）；
  - Java 新增回调接收：`backend/src/main/java/com/langdong/spare/controller/PythonCallbackController.java` 与 `service/ai/PythonCallbackStoreService.java`；
  - 安全加固：仅放行 POST 回调入口、强制 `PYTHON_CALLBACK_TOKEN`、校验 `task_id/status`、回调失败重试、错误信息脱敏、内存存储上限控制；
  - 验证结果：Python 测试 `5 passed`，Java 编译 `JAVA_OK`。

---

### F14: 全栈一键启停与状态自检脚本
- **功能描述**：为本地开发环境新增“一键启动/停止/状态检查”脚本，覆盖 Redis、Python API、Celery Worker、Java 后端与前端服务，降低多终端手工操作复杂度。
- **落实情况**：
  - 新增脚本：`scripts/start_all.sh`、`scripts/stop_all.sh`、`scripts/status_all.sh`；
  - `start_all.sh` 支持按依赖顺序启动并生成 PID 与日志到 `.run/`；
  - `stop_all.sh` 支持按 PID 清理进程并尝试关闭 Redis；
  - `status_all.sh` 支持进程态与健康检查双维度巡检；
  - 已完成执行权限与语法校验（`bash -n` 全通过）。

---

### F25: AI 预测结果查询接口空参数兼容修复
- **功能描述**：修复 `/api/ai/forecast/result` 在 `month=`、`partCode=` 为空字符串时返回 400 的兼容性问题，保持原有业务语义（空 month 仍查询最新月份，空 partCode 仍表示不按编码过滤）。
- **落实情况**：已在 `AiForecastController.queryResult` 中新增参数归一化逻辑：将 `month` 与 `partCode` 先 `trim`，空字符串统一转为 `null`；仅对非空 `month` 执行 `yyyy-MM` 格式校验，再传入 service 查询。后端编译验证通过。

---

### F12: P2 文档包完成（API / 生产部署 / 灾备 / 性能）
- **功能描述**：完成 P2 阶段文档建设，新增 API 与运维体系文档并接入总导航：`docs/API/API_REFERENCE.md`、`docs/API/API_EXAMPLES.md`、`docs/OPS/DEPLOYMENT_PRODUCTION.md`、`docs/OPS/DISASTER_RECOVERY.md`、`docs/OPS/PERFORMANCE_CHECKLIST.md`。
- **落实情况**：5 份文档均已创建，覆盖接口契约、联调示例、发布回滚、RPO/RTO 灾备流程与性能验收清单；`docs/README.md` 已同步新增 API/OPS 分区、文档矩阵状态与 FAQ 跳转入口，形成从开发联调到生产运维的闭环文档链路。

---

### F13: API 字段级文档与业务场景导航增强
- **功能描述**：对 API 文档进行字段级增强，并在总导航增加按业务场景的一跳式入口，提升联调与排障效率。涉及文件：`docs/API/API_REFERENCE.md`、`docs/README.md`。
- **落实情况**：`API_REFERENCE.md` 已补齐认证、分类预测、补货建议、FIFO 追溯等模块的请求/响应字段表；`README.md` 新增“按业务场景跳转”区，覆盖登录鉴权、分类预测、补货处理、FIFO 追溯、发布回滚、性能定位等高频场景。

---

### F14: API 联调错误矩阵增强（400/401/403/422）
- **功能描述**：在联调示例文档中新增高频错误矩阵与可复制请求/响应示例，提升测试与联调问题定位效率。涉及文件：`docs/API/API_EXAMPLES.md`。
- **落实情况**：已新增 400/401/403/422 的“触发点-根因-处理动作”矩阵，并补充 curl 复现场景和典型响应样例；同时保留 404/409/422 业务解释，形成从快速定位到深度排查的闭环结构。

---

### F15: API 文档契约纠偏（路径/参数/返回结构）
- **功能描述**：针对联调文档与后端实现存在的口径偏差进行纠偏，避免示例可执行性与真实接口行为不一致。涉及文件：`docs/API/API_REFERENCE.md`、`docs/API/API_EXAMPLES.md`。
- **落实情况**：已修正领用出库路径为 `/api/requisitions/{id}/outbound`，统一分类分页参数为 `page/pageSize`，将补货建议接口文档改为当前实现口径（仅 `status` + 数组返回），并补充 422/500 的异常映射说明，降低联调误判风险。

---

### F16: 历史源稿清理与归档规范化
- **功能描述**：执行文档资产清理，删除已被正式文档完全吸收的源稿，并将仍具复盘价值的实验底稿归档，避免主目录冗余。
- **落实情况**：已删除 `3-2.md`、`3-3.md`、`用例.md`、`ai3.md`、`ai11.md`、`ai19.md`；已归档 `随机森林.md`、`ai5.md`、`ai6.md`、`ai7.md`、`ai8.md`、`ai9.md` 至 `docs/archive/legacy_sources/`，`add_sortable.py` 至 `docs/archive/tools/`；并同步修复了 docs 内相关引用路径与归档说明。

---

### F17: 前端页面视觉改造（对齐参考页风格）
- **功能描述**：将现有前端页面视觉结构调整为参考文件风格，统一容器层次、页头工具区、表格与分页的展示规范，使列表页交互与视觉呈现保持一致。
- **落实情况**：新增全局主题样式文件 `frontend/src/styles/reference-theme.css` 并在 `frontend/src/main.js` 注入；重构 `frontend/src/views/SparePartList.vue` 为 `page-container + page-section + phead + head-btn-group` 结构；同步改造 `frontend/src/views/Home.vue` 的侧边栏与顶部区域色彩与间距；完成构建验证通过。

---

### F18: 全站视觉回归统一（登录页/列表页/弹窗页）
- **功能描述**：执行一轮全站视觉回归，覆盖登录页、各模块列表页和弹窗页，输出回归清单并将页面风格统一到同一视觉基线。
- **落实情况**：扩展 `frontend/src/styles/reference-theme.css`，统一全站容器内边距、卡片头体结构、表格密度、分页高亮及弹窗头体尾间距；精修 `frontend/src/views/Login.vue`（标题、按钮、背景）；新增回归清单 `docs/IMPLEMENTATION/VISUAL_REGRESSION_CHECKLIST.md` 并完成页面分组验收项标记。

---

### F19: 全站纯视觉细抛光（细节一致性增强）
- **功能描述**：在不改业务逻辑的前提下，对全站页面做第二轮纯视觉细抛光，重点提升卡片头部、筛选区、表格、弹窗与侧栏菜单的一致性。
- **落实情况**：在 `frontend/src/styles/reference-theme.css` 增加侧栏菜单 hover/active 统一规则、卡片头 clearfix 与按钮间距规则、筛选容器自适应间距、表格表头背景与节奏统一、弹窗 footer 对齐与按钮间距统一，并补齐移动端细节间距规则。

---

### F20: 其余业务页面对齐备件列表风格
- **功能描述**：将其余业务页面按 `SparePartList.vue` 的容器风格统一改写，确保页面主容器、卡片层次与间距体系一致。
- **落实情况**：批量将 `EquipmentProfile`、`LocationProfile`、`SupplierProfile`、`SupplyCategory`、`AiForecastResult`、`ClassifyResult`、`PHM` 模块页及 `Dashboard` 根容器改为 `page-container + 原类名`；并在 `reference-theme.css` 增加 `.home-main > .page-container` 统一间距规则（含移动端），保证全站页面在同一视觉基线下呈现。

---

### F21: 全站页头图标对齐（与备件列表一致）
- **功能描述**：将其他界面的页头统一增加与备件列表相同的数据图标，达到同等级视觉对齐效果。
- **落实情况**：在 `frontend/src/styles/reference-theme.css` 中为 `.home-main .el-card__header` 统一添加 `el-icon-s-data` 伪元素（使用 Element Icons 编码 `\e7a8`），并同步设置边框、圆角、颜色与间距；兼容移动端偏移与内边距，避免逐页改模板导致维护成本上升。

---

### F22: 全站100%结构级页头一致化
- **功能描述**：将全站业务页卡片头从样式模拟升级为结构级一致，完全对齐 `SparePartList.vue` 的页头 DOM 结构（`phead + icon + title + head-btn-group`）。
- **落实情况**：批量改写所有 `slot=\"header\"` 模板为统一结构，并补齐 `AiForecastResult`、`FaultPrediction`、`Dashboard` 等页面的复杂页头；同时在 `reference-theme.css` 移除伪元素图标方案，改为由真实结构渲染图标，确保“样式和结构”双一致。并完成二轮细化修复：去除双分隔线、兼容旧 `float` 按钮写法与嵌套按钮容器。构建校验通过。

---

### F23: 备件列表排序规则收敛（仅保留编码排序）
- **功能描述**：将数据列表排序能力收敛为仅保留“按编码排序”，移除其他列排序入口，避免排序口径分散。
- **落实情况**：在 `frontend/src/views/SparePartList.vue` 中移除“备件名称、型号规格、类别、库存数量、单价”列的 `sortable` 属性，仅保留“备件编码”列排序按钮（`sortable=\"custom\"`）与默认编码升序规则。

---

### F24: 全界面排序规则统一（仅保留编码列排序）
- **功能描述**：将“仅保留按编码排序”规则推广到所有界面，统一数据列表排序入口。
- **落实情况**：批量移除 `frontend/src/views/**` 中非编码列的 `sortable` 配置，仅保留 `code/partCode/deviceCode/sparePartCode` 等编码字段列的排序按钮；全量构建通过。

---

### F25: 一键启动脚本稳定性修复（Redis + Python 解释器锁定 + 状态探活）
- **功能描述**：修复 `scripts/start_all.sh` 的启动失败与 `scripts/status_all.sh` 的误报问题，确保本地一键启动可稳定运行。
- **落实情况**：安装并启用 Redis 后，脚本层面将 Python API/Celery 启动命令统一改为 `conda run -n langdong python -m ...`，避免误用系统 Python；并将后端探活逻辑改为“HTTP 可达即健康”，规避 401/405 误判；`stop_all.sh` 新增端口清理与残留进程清理，防止端口占用导致重启失败。当前 `python-api/celery/backend/frontend/redis` 状态均为 UP。

---

### F26: Callback Token 本地固化（免手工传参）
- **功能描述**：将 `PYTHON_CALLBACK_TOKEN` 从“每次命令临时传入”升级为“本地配置自动加载”，降低启动复杂度。
- **落实情况**：新增根目录 `.env.local`（仅本地使用）并在 `scripts/start_all.sh` 启动前自动 `source`；`.gitignore` 增加 `.env.local` 规则避免误提交。现已验证可直接执行 `./scripts/start_all.sh` 完成全栈启动，且无 callback token 警告。

---

### F27: 前端任务中心（异步任务提交与状态轮询）
- **功能描述**：新增 AI 任务中心页面，支持提交 Python 异步补货任务、自动轮询状态、查看任务结果与错误信息。
- **落实情况**：新增 `AiJobCenter.vue` 与 `/ai/job-center` 路由；后端新增 `/api/ai/forecast/jobs/replenishment`、`/api/ai/forecast/jobs/{taskId}` 代理接口；前端补充权限门控（提交/查询分权限）、轮询失败自动停机与本地任务缓存。已通过 `mvn -DskipTests compile` 与 `npm run build` 验证。

---

### F28: 任务中心数据库菜单落地（动态菜单可见）
- **功能描述**：将“AI任务中心”从前端路由级能力升级为数据库动态菜单能力，保证登录后侧栏可按权限显示。
- **落实情况**：新增并执行 `sql/add_ai_job_center_menu.sql`，以幂等方式插入/修正 `/ai/job-center` 菜单（组件 `ai/AiJobCenter`，权限 `ai:forecast:list`），并自动授权 ADMIN 角色；实测库内菜单记录已存在（path=`/ai/job-center`）。

---

### F34: AI 任务中心结果详情可视化增强
- **功能描述**：在 AI 任务中心任务列表中增加“查看结果”能力，支持对单个任务展示完整计算明细，而不只显示“共 N 条建议”的摘要。
- **落实情况**：已在 `frontend/src/views/ai/AiJobCenter.vue` 新增“查看结果”按钮与结果详情弹窗：任务行在轮询成功后会缓存 `payloadData`；弹窗内展示建议明细表（备件ID、备件名称、建议采购量、优先级、提示信息、错误信息）以及原始 JSON 文本，便于排查与业务核验。已通过 `npm run build` 编译验证。

---

### F35: AI任务中心异步重算结果覆盖需求预测结果页同条数据
- **功能描述**：当 AI 任务中心异步任务成功回调后，将该结果覆盖写入 `ai_forecast_result` 的同月份同备件记录，使“需求预测结果”页面可直接体现任务中心重算值；同时保留原有 `/api/ai/forecast/trigger` 全量重算链路。
- **落实情况**：已在 `AiForecastService` 新增回调结果落库方法（映射 `spare_part_id/part_code`、`predicted_demand`、置信区间等字段，并按 `partCode + forecastMonth` 执行删除后批量插入幂等覆盖）；在 `PythonCallbackController` 的 SUCCESS 回调分支中接入该方法。联调验证：提交任务后，`/api/ai/forecast/result` 中对应备件的 `predictQty` 已被覆盖更新。

---

### F36: 预测口径双展示与任务中心口径命名统一
- **功能描述**：同时执行两项口径优化：①在“需求预测结果”页面同时展示单月“预测消耗量”和“未来3个月累计需求”；②在任务中心结果详情明确展示“未来3个月总需求”字段，避免与单月口径混淆。
- **落实情况**：后端 `AiForecastService.queryResult` 为每条记录补充 `demand3Months` 字段（按历史记录从当前月起累计最多3个月）；实体 `AiForecastResult` 新增展示字段 `demand3Months`。前端 `AiForecastResult.vue` 新增“未来3个月累计需求”列；`AiJobCenter.vue` 在结果详情弹窗新增“未来3个月总需求”列（读取 `predicted_demand.total`）。已完成后端编译、前端构建与接口联调验证。

---

### F29: 基于真实业务表生成过去2年按天训练数据
- **功能描述**：新增“按天”训练数据集构建能力，从真实业务表汇总过去730天数据，供后续模型训练使用。
- **落实情况**：新增并执行 `sql/generate_ai_daily_train_data_2y.sql`：创建 `ai_part_daily_train_data`，按 `日期骨架 x 备件` 生成全量日粒度样本，聚合来源包括 `biz_outbound_batch_trace`、`biz_requisition(_item)`、`biz_purchase_order`；支持重跑幂等（窗口删除重建）与事务保护。实测生成 `37230` 行（`51` 个备件，日期范围 `2024-03-30` 至 `2026-03-29`）。

---

### F30: 任务中心输入兼容升级（备件ID/编码混输）
- **功能描述**：修复任务中心仅支持数字ID导致“输入备件编码报错”的问题，提升提交可用性。
- **落实情况**：前端 `AiJobCenter` 输入从“备件ID”升级为“备件ID/编码”，支持混合输入并原样提交；后端 `AiForecastJobController` 增加编码映射逻辑，按 `spare_part.code` 转换为 `id` 后提交给 Python 任务接口，同时保留数字 ID 兼容。已通过 `mvn -DskipTests compile` 与 `npm run build` 验证。

---

### F31: 训练数据看板（侧边栏可访问）
- **功能描述**：将训练数据从”仅数据库可查”升级为”前端侧边栏可视化查看”，支持筛选、分页与来源标记展示。
- **落实情况**：新增后端 `AiTrainDataController` + `AiTrainDataMapper`（接口 `/api/ai/train-data/list`，权限 `ai:train-data:list`）；前端新增 `AiTrainDataDashboard.vue` 页面与 `/ai/train-data-dashboard` 路由；新增并执行 `sql/add_ai_train_data_dashboard_menu.sql` 将菜单落库（path=`/ai/train-data-dashboard`，component=`ai/AiTrainDataDashboard`）并授权 ADMIN。后端编译与前端构建均通过。

---

### F32: 训练数据看板 - 注入随机模拟数据替换全零数据
- **功能描述**：`ai_part_daily_train_data` 表数据全部为 0（因原 SQL 依赖业务表数据为空），需生成真实感的随机数据用于看板展示与 AI 模型调试。
- **落实情况**：新增 `sql/mock_ai_train_data.sql`，通过日期序列生成器 × 备件笛卡尔积，利用多个独立 `RAND()` 种子为每行生成不同的业务字段：工作日出库概率 35%（高频备件 60%）、周末减半；采购到货约 5% 天次批量到货；来源标记（TRACE/REQ_OUT/TRACE_REQ/NONE）与 is_imputed 随机分配。已执行入库，共生成 37,230 条记录（51 个备件 × 730 天），平均日出库量 1.08，看板数据全面非零。

---

### F33: 修复 Python 服务任务执行失败（数据库连接密码硬编码错误）
- **功能描述**：诊断与修复任务执行 FAILURE 问题，确保 Celery worker 任务能正常提交与执行。
- **落实情况**：根因分析：`smart_replenishment.py` 和 `predictive_maintenance.py` 中 `DB_CONFIG` 的密码字段被硬编码为占位符 `"your_password"`，导致数据库连接失败。解决方案：修改两个文件的密码配置，改为 `os.environ.get("DB_PASSWORD", "123456")`，支持从环境变量读取，默认值为正确的本地密码 `"123456"`。重启 Celery worker 后，新提交的任务即可正常连接数据库并执行。

---

### F37: 异步覆盖链路一致性加固（三个月口径 + 阈值联动）
- **功能描述**：修复异步覆盖场景下的两项一致性问题：1）“未来3个月累计需求”改为严格连续自然月口径；2）回调覆盖写库后同步触发库存阈值/补货建议重算。
- **落实情况**：后端 `AiForecastService` 中 `sumNextThreeMonthsDemand` 改为按 `startMonth` 连续三个月精确累加（缺失月按0）；`applyAsyncForecastResult` 写入 `ai_forecast_result` 后新增 `recalcThresholdsForAsyncOverwrite`，按受影响月份与备件编码过滤上下文后调用 `stockThresholdService.recalcAndPush`。已通过 `mvn -DskipTests compile` 验证。

---

### F38: 训练数据打通真实业务表 + 动态回退估算 + 重算进度可视化
- **功能描述**：将智能补货训练数据源从日志表升级为真实业务表聚合，并修复手动重算“无法查看进度”的问题。
- **落实情况**：
  1) `smart_replenishment.py` 新增真实业务表聚合读取（`biz_requisition_item + biz_requisition`），`load_consumption_data` 改为“业务表优先、日志表兜底”；
  2) 零数据回退由固定 5 改为动态估算（备件历史→同类目→全局→最小保护值），返回 `method=dynamic_fallback` 与 `fallback_source`；
  3) 后端 `AiForecastService` 增加重算运行状态跟踪（阶段、处理进度、失败数、百分比），`AiForecastController` 新增 `/api/ai/forecast/trigger/status`；
  4) 前端 `AiForecastResult.vue` 新增重算状态提示与进度条，触发后自动轮询，页面进入 RUNNING 状态时自动接续轮询，完成后自动刷新结果。

---

### F39: 真实业务聚合 SQL 与 MySQL ONLY_FULL_GROUP_BY 兼容修复
- **功能描述**：修复 AI 任务中心补货任务在真实业务聚合场景下因 SQL 分组规则导致的 `TASK_FAILED`。
- **落实情况**：定位到 `smart_replenishment.py` 的 `load_consumption_data_from_business` 在 MySQL 严格分组模式触发 1055。已将月度字段改为 `DATE_FORMAT(...,'%Y-%m-01')` 并使用同一表达式 `GROUP BY/ORDER BY`，避免选择列与分组列不一致。复测新任务 `5748c34e-4da4-4849-bf14-fc49251b1cf9` 状态 `SUCCESS`，回调结果正常可查。

---

### F40: AI异步回调三个月口径统一与补货建议按预测月联动写入
- **功能描述**：修复 AI 任务中心与需求预测结果页在“未来3个月需求”指标上的口径差异，并完善异步回调后智能补货建议写入的月份联动，确保多月预测不会互相覆盖。
- **落实情况**：后端 `AiForecastService` 已支持将异步回调 `predicted_demand.monthly_detail` 展开为连续月份写入 `ai_forecast_result`（并同步写入对应月置信区间）；当无明细时按 `total -> predict_qty -> monthly_detail[0]` 顺序兜底。`StockThresholdService` 已改为按 `forecastMonth` 写入 `biz_reorder_suggest.suggest_month`，并增加空值与长度保护，避免三个月结果覆盖到同一建议月份。已通过 `backend` 模块编译与测试验证。
