# 报表与看板模块（M8）— Claude Code 提示词

你是一位 Java/Vue3 全栈工程师。请基于以下需求规格，为备件管理系统实现「报表与看板模块（M8）」。

## 技术栈

- 后端：Spring Boot 3.x + MyBatis-Plus + MySQL 5.7 + Redis
- 前端：Vue 3 + Element Plus + ECharts 5
- 报表导出：Apache POI (Excel) + iText/EasyPDF (PDF导出)
- 权限认证：Spring Security + JWT

---

## 业务功能要求（6个子模块）

### 子模块1：管理层驾驶舱 (Dashboard)
- **面向角色**：管理层（主）
- **核心模块描述**：5项核心KPI可视化大屏展示。
- **展示内容**：
  1. 库存总金额
  2. 库存周转率
  3. 本月采购额
  4. 本月维修费用
  5. 设备可用率
- **功能特性**：支持按时间段和产线切换数据视图，前端使用 ECharts 绘制仪表盘和趋势图。

### 子模块2：库存分析报告
- **面向角色**：管理层 + 仓库管理员
- **核心模块描述**：多维度的库存状况分析。
- **展示内容**：
  1. 备件ABC分类分布饼图
  2. 库存金额趋势折线图
  3. 滞库备件清单（长期未流动的备件列表）
  4. 库存周转分析柱状图
- **功能特性**：支持一键导出 Excel 和 PDF。

### 子模块3：备件消耗趋势分析
- **面向角色**：全部角色
- **核心模块描述**：分析历史备件的消耗规律。
- **展示内容**：
  1. 各备件历史消耗折线图
  2. Top 10 高消耗备件排行榜
  3. 月度消耗对比图
- **功能特性**：支持选取不同备件联合对比，图表联动。支持一键导出 Excel 和 PDF。

### 子模块4：供应商绩效报告
- **面向角色**：采购员 + 管理层
- **核心模块描述**：供应商综合评估视图。
- **展示内容**：
  1. 供应商交货准时率柱状图
  2. 供应商质量合格率图表
  3. 供应商价格趋势折线图
  4. 季度评分排名列表
- **功能特性**：支持按季度快速筛选，支持一键导出 Excel 和 PDF。

### 子模块5：维修费用分析
- **面向角色**：管理层 + 设备工程师
- **核心模块描述**：设备与备件维护的成本核算。
- **展示内容**：
  1. 月度维修费用构成环形图（包含：备件费、人工费、外协费比例）
  2. 设备维修成本排名（Top 排行榜）
- **功能特性**：支持切换不同车间/产线，支持一键导出。

### 子模块6：预警任务中心
- **面向角色**：全部角色
- **核心模块描述**：系统中所有异常和提醒情况的统一汇聚入口。
- **展示内容**：
  1. 低库存预警（备件库存低于 ROP）
  2. 临期备件预警（快过期备件）
  3. 逾期工单提醒（超时未完成的任务）
  4. 采购延期提醒（逾期未到货的采购单）
- **功能特性**：预警列表支持“一键跳转”到对应模块的处理页面。

---

## 权限控制要求（@PreAuthorize）

| 模块操作 | 允许访问的数据/角色 |
|---|---|
| 管理层驾驶舱 | ROLE_MANAGER / ROLE_ADMIN |
| 库存分析报告 | ROLE_MANAGER / ROLE_ADMIN / ROLE_WAREHOUSE |
| 备件消耗趋势分析 | 所有登录用户（根据部门/数据域过滤） |
| 供应商绩效报告 | ROLE_MANAGER / ROLE_ADMIN / ROLE_PURCHASER |
| 维修费用分析 | ROLE_MANAGER / ROLE_ADMIN / ROLE_ENGINEER |
| 预警任务中心 | 所有登录用户（仅可见与自己相关/授权的预警） |
| 报表导出操作 | 与页面查看权限一致 |

---

## 后端代码结构要求

后端需要提供聚合查询的数据接口，建议包结构如下：

```
com.yourproject.report
  ├── controller/
  │   ├── DashboardController.java
  │   ├── InventoryReportController.java
  │   ├── ConsumptionReportController.java
  │   ├── SupplierReportController.java
  │   ├── MaintenanceReportController.java
  │   └── WarningCenterController.java
  ├── service/
  │   ├── ... (各对应的 Service 接口)
  │   └── impl/ ...
  ├── mapper/
  │   ├── ReportMapper.java (存放复杂的联表统计图表 SQL)
  │   └── WarningMapper.java
  ├── dto/
  │   ├── KpiSummaryDTO.java
  │   ├── ChartSeriesDTO.java
  │   └── WarningItemDTO.java
  └── util/
      └── ExportUtil.java (Excel/PDF的通用导出工具类)
```

- API 路径后缀：统一加前端路由映射或 `/api/v1/report/***`
- 响应格式统一：`{ "code": 200, "msg": "success", "data": {} }`

---

## 前端页面结构要求（Vue 3 + Element Plus + ECharts）

| 路由地址 | 页面组件名 | 说明 |
|---|---|---|
| `/report/dashboard` | `Dashboard.vue` | 5项核心KPI数据块 + 产线时间过滤器 |
| `/report/inventory` | `InventoryReport.vue` | ABC饼图、滞留清单、库存周转等图表集合 + 导出按钮 |
| `/report/consumption`| `ConsumptionReport.vue`| 折线图、Top10柱状图、对比等图表 + 导出按钮 |
| `/report/supplier` | `SupplierReport.vue` | 准时率/合格率/价格图表、季度排名等 + 导出按钮 |
| `/report/maintenance`| `MaintenanceReport.vue`| 维修费用构成统计与排行图表 + 导出按钮 |
| `/warning/center` | `WarningCenter.vue` | 预警消息汇总列表（通过页签分隔不同预警类型）及操作跳转 |

---

## 注意事项与约束

1. **复杂SQL聚合**：
   - 报表模块的数据多来自于其他模块（库存台账、领用单、采购订单、工单、备件分类等），禁止在 for 循环中执行数据库单行查询。
   - 所有图表所需数据，须尽量通过 MyBatis XML 中的复杂 SQL 联表、聚合函数 (`GROUP BY`, `SUM`, `COUNT`) 一次性查出。
   - 注意 MySQL 5.7 `GROUP BY` 严格模式：SELECT 的字段必须在 GROUP BY 短语中。

2. **视图层性能 (ECharts)**：
   - 前端绘图的数据结构请由后端直接拼接为 `X轴` (如：`['1月','2月']`) 和 `Y轴数据体` 的格式，减少前端的二次数据遍历开销。
   
3. **数据导出支持**：
   - 导出为 Excel 和 PDF 必须保证与页面查询条件相匹配，避免全量无条件拉取。

4. **预警任务设计**：
   - 预警中心页面需要显示各分类未读/未处理角标。

---

## 💡 开发分步建议给 Claude Code：

建议按以下顺序把需求逐步发送，可保证成功率：

| 步骤 | 给 Claude Code 发出的独立指令 |
|---|---|
| 1️⃣ | 「先帮我实现核心的 `ReportMapper.xml` 复杂统计查询 SQL（库存金额/周转率、KPI、Top10、逾期预警查询等）。」 |
| 2️⃣ | 「基于上一步的 Mapper，生成后端的所有 ReportService 及对应 Controller 接口。」 |
| 3️⃣ | 「实现数据导出功能，编写并接入 `ExportUtil` (支持 Apache POI 的 Excel 导出)。」 |
| 4️⃣ | 「最后生成前端的 6 个 Vue3 大屏报告和看板页面，接入后端并用 ECharts 渲染出对应的仪表盘和折线/柱状/饼图组件。」 |
