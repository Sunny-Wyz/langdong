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
