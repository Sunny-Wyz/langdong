# 数据库说明文档

**数据库名**: `spare_db`  
**字符集**: `utf8mb4`  
**总表数**: 34 张  
**最后更新**: 2026-04-02  

---

## 目录

1. [模块总览](#模块总览)
2. [表关联关系图](#表关联关系图)
3. [系统权限模块（5张）](#一系统权限模块)
4. [基础档案模块（8张）](#二基础档案模块)
5. [库存管理模块（6张）](#三库存管理模块)
6. [采购管理模块（3张）](#四采购管理模块)
7. [领用管理模块（2张）](#五领用管理模块)
8. [维修工单模块（1张）](#六维修工单模块)
9. [智能分类模块（1张）](#七智能分类模块)
10. [AI 分析模块（4张）](#八ai-分析模块)
11. [PHM 预测性维护模块（4张）](#九phm-预测性维护模块)

---

## 模块总览

| 模块 | 表数 | 核心表 | 说明 |
|------|------|--------|------|
| 系统权限 | 5 | `user`, `role`, `menu` | 用户、角色、菜单三级权限体系 |
| 基础档案 | 8 | `spare_part`, `equipment`, `supplier` | 备件、设备、供应商等基础字典 |
| 库存管理 | 6 | `stock_in_receipt`, `stock_in_item` | 入库收货、FIFO 批次追溯 |
| 采购管理 | 3 | `biz_purchase_order`, `biz_reorder_suggest` | 采购订单、询价、补货建议 |
| 领用管理 | 2 | `biz_requisition`, `biz_requisition_item` | 申请→审批→出库→安装全流程 |
| 维修工单 | 1 | `biz_work_order` | 报修→派工→维修→完工流程 |
| 智能分类 | 1 | `biz_part_classify` | ABC/XYZ 分类及安全库存计算结果 |
| AI 分析 | 4 | `ai_forecast_result`, `ai_device_feature` | 需求预测、设备特征、训练数据 |
| PHM 预测性维护 | 4 | `ai_device_health`, `ai_fault_prediction` | 健康评估、故障预测、维护建议 |

---

## 表关联关系图

```
user ──────────── user_role ──────────── role
                                          │
                                       role_menu ──── menu

spare_part_category ◄── spare_part ──► location (location_id)
                            │              │
                            │         spare_part_location_stock
                            │              │
                         spare_part_stock  │
                            │
                    equipment_spare_part ──► equipment
                            │                   │
                            │              biz_work_order
                            │                   │
supplier ◄──── biz_purchase_order ──► biz_reorder_suggest
     │              │
     │        purchase_order_item
     │              │
biz_supplier_quote  stock_in_receipt
                         │
                    stock_in_item ──► biz_outbound_batch_trace
                                            │
biz_requisition ──► biz_requisition_item ──┘

biz_work_order ◄── biz_maintenance_suggestion ◄── ai_device_health ◄── equipment
                                                         │
                                               ai_fault_prediction
                                                         │
                                               ai_device_feature ──► equipment

spare_part ──► biz_part_classify
spare_part ──► ai_forecast_result
spare_part ──► ai_part_daily_train_data
```

---

## 一、系统权限模块

### `user` — 用户表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| username | VARCHAR(50) UNIQUE | 登录名 |
| name | VARCHAR(50) | 真实姓名 |
| password | VARCHAR(100) | 密码（BCrypt 加密） |
| status | TINYINT | 状态：1=正常，0=停用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `role` — 角色表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| code | VARCHAR(50) UNIQUE | 角色编码，如 `ADMIN` |
| name | VARCHAR(50) | 角色名称 |
| remark | VARCHAR(255) | 备注 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `menu` — 菜单权限字典表

树形结构，通过 `parent_id` 自引用构建三级菜单（目录→菜单→按钮）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| parent_id | BIGINT | 父菜单 ID，一级目录为 NULL |
| name | VARCHAR(50) | 菜单/按钮名称 |
| path | VARCHAR(200) | 前端路由路径 |
| component | VARCHAR(200) | 前端组件路径 |
| permission | VARCHAR(100) | 权限标识，如 `sys:user:list` |
| type | TINYINT | 类型：1=目录，2=菜单页面，3=按钮 |
| icon | VARCHAR(50) | 图标名 |
| sort | INT | 排序值 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `user_role` — 用户角色关联表

多对多关联，一个用户可绑定多个角色。

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT PK | 用户 ID，关联 `user.id` |
| role_id | BIGINT PK | 角色 ID，关联 `role.id` |

---

### `role_menu` — 角色菜单关联表

多对多关联，控制角色可访问的菜单与按钮。

| 字段 | 类型 | 说明 |
|------|------|------|
| role_id | BIGINT PK | 角色 ID，关联 `role.id` |
| menu_id | BIGINT PK | 菜单 ID，关联 `menu.id` |

**权限校验流程**：`user` → `user_role` → `role` → `role_menu` → `menu.permission`，后端通过 `@PreAuthorize("hasAuthority('xxx:xxx:xxx')")` 校验。

---

## 二、基础档案模块

### `spare_part_category` — 备件分类字典表

两级树形结构（大类 1 位编码 + 小类 3 位编码，合计 4 位）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| code | VARCHAR(4) UNIQUE | 分类编码，固定 4 位，约束 `CHAR_LENGTH(code)=4` |
| name | VARCHAR(100) | 分类名称 |
| parent_id | BIGINT | 父类 ID，大类为 NULL |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `spare_part` — 备件档案表

系统核心主数据，几乎所有业务模块均引用此表。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| code | VARCHAR(8) UNIQUE | 备件统一 8 位编码，如 `SP20001` |
| name | VARCHAR(100) | 备件名称 |
| model | VARCHAR(100) | 型号规格 |
| quantity | INT | 总库存数量 |
| unit | VARCHAR(20) | 单位，默认"个" |
| price | DECIMAL(10,2) | 单价（元） |
| category_id | BIGINT FK | 所属分类，关联 `spare_part_category.id` |
| supplier | VARCHAR(100) | 供应商名称（冗余字段） |
| remark | TEXT | 备注 |
| location_id | BIGINT FK | 主存放货位，关联 `location.id` |
| supplier_id | BIGINT FK | 供应商 ID，关联 `supplier.id` |
| is_critical | TINYINT(1) | 是否关键备件（1=关键，0=非关键）|
| replace_diff | INT | 供应替代难度（1=容易～5=极难） |
| lead_time | INT | 采购提前期（天），默认 30 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

> `is_critical`、`replace_diff`、`lead_time` 三字段由 `classify_module.sql` 扩展，用于 ABC/XYZ 智能分类计算。

---

### `location` — 货位档案表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| code | VARCHAR(50) UNIQUE | 货位编码 |
| name | VARCHAR(100) | 货位名称 |
| zone | VARCHAR(50) | 所属专区（1-12） |
| capacity | VARCHAR(50) | 容量描述 |
| remark | TEXT | 备注 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `equipment` — 设备档案表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| code | VARCHAR(50) UNIQUE | 设备编码 |
| name | VARCHAR(100) | 设备名称 |
| model | VARCHAR(100) | 规格型号 |
| department | VARCHAR(100) | 所属部门/产线 |
| status | VARCHAR(50) | 设备状态，默认"正常" |
| remark | TEXT | 备注 |
| importance_level | VARCHAR(50) | 重要性等级：CRITICAL/IMPORTANT/NORMAL |
| install_date | DATE | 安装日期 |
| warranty_end_date | DATE | 质保到期日期 |
| last_maintenance_date | DATE | 最近维护日期 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

> `importance_level`、`install_date`、`warranty_end_date`、`last_maintenance_date` 由 `phm_module.sql` 扩展，用于健康评估计算。

---

### `equipment_spare_part` — 设备备件配套关联表

记录每台设备需要配套哪些备件及数量。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| equipment_id | BIGINT FK | 设备 ID，关联 `equipment.id` |
| spare_part_id | BIGINT FK | 备件 ID，关联 `spare_part.id` |
| quantity | INT | 配套数量，默认 1 |
| created_at | DATETIME | 关联时间 |

唯一约束：`(equipment_id, spare_part_id)`

---

### `supplier` — 供应商档案表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| code | VARCHAR(50) UNIQUE | 供应商编码 |
| name | VARCHAR(100) | 供应商名称 |
| unified_social_credit_code | VARCHAR(100) | 统一社会信用代码 |
| bank_account_info | VARCHAR(200) | 银行账户信息 |
| contact_person | VARCHAR(50) | 联系人 |
| phone | VARCHAR(20) | 联系电话 |
| address | VARCHAR(200) | 地址 |
| status | VARCHAR(20) | 状态：正常/停用 |
| remark | TEXT | 备注 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `supply_category` — 供应商品类字典表

描述供应商能够供应的商品品类。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| code | VARCHAR(50) UNIQUE | 品类编码 |
| name | VARCHAR(100) | 品类名称 |
| description | VARCHAR(200) | 品类描述 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `supplier_category_relation` — 供应商品类关联表

多对多关联，记录供应商可供应的品类范围。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| supplier_id | BIGINT FK | 供应商 ID，关联 `supplier.id` |
| supply_category_id | BIGINT FK | 品类 ID，关联 `supply_category.id` |
| created_at | DATETIME | 创建时间 |

---

## 三、库存管理模块

### `spare_part_stock` — 备件总库存表

备件的全局库存数量汇总，与 `spare_part.quantity` 保持同步。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| spare_part_id | BIGINT FK UNIQUE | 备件 ID，关联 `spare_part.id` |
| quantity | INT | 当前总库存数量 |
| updated_at | DATETIME | 最后更新时间 |

---

### `spare_part_location_stock` — 备件货位分布库存表

记录每件备件在每个货位的分布数量，支持多货位存放。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| location_id | BIGINT FK | 货位 ID，关联 `location.id` |
| spare_part_id | BIGINT FK | 备件 ID，关联 `spare_part.id` |
| quantity | INT | 该货位的备件数量 |
| updated_at | DATETIME | 最后更新时间 |

唯一约束：`(location_id, spare_part_id)`

---

### `stock_in_receipt` — 入库收货单主表

对应一次采购到货的收货单据。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| receipt_code | VARCHAR(50) UNIQUE | 收货单编号 |
| purchase_order_id | BIGINT FK | 关联采购订单 ID，关联 `biz_purchase_order.id` |
| receipt_date | DATE | 收货日期 |
| status | VARCHAR(20) | 状态：待验收/已完成 |
| handler_id | BIGINT FK | 收货人 ID，关联 `user.id` |
| remark | VARCHAR(200) | 备注 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `stock_in_item` — 入库收货明细表

每条记录对应一次入库批次，是 FIFO 出库管理的核心数据源。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| stock_in_receipt_id | BIGINT FK | 收货单 ID，关联 `stock_in_receipt.id` |
| purchase_order_item_id | BIGINT FK | 采购订单明细 ID，关联 `purchase_order_item.id` |
| spare_part_id | BIGINT FK | 备件 ID，关联 `spare_part.id` |
| expected_quantity | INT | 应收数量 |
| actual_quantity | INT | 实际收货数量 |
| remaining_qty | INT | 批次剩余可用数量（每次出库后递减，FIFO 核心字段） |
| shelved_quantity | INT | 已上架数量 |
| location_id | BIGINT FK | 上架货位，关联 `location.id` |
| in_time | DATETIME | 入库时间（FIFO 排序依据，升序取批次） |
| remark | VARCHAR(200) | 备注 |

索引：`(spare_part_id, in_time, remaining_qty)` 用于 FIFO 查询。

---

### `purchase_order_item` — 采购订单明细表

一张采购订单可包含多个备件明细。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| purchase_order_id | BIGINT FK | 采购订单 ID，关联 `biz_purchase_order.id` |
| spare_part_id | BIGINT FK | 备件 ID，关联 `spare_part.id` |
| quantity | INT | 采购数量 |
| unit_price | DECIMAL(10,2) | 单价（元） |
| received_quantity | INT | 已到货数量（累计入库后递增） |
| remark | VARCHAR(200) | 备注 |

---

### `biz_outbound_batch_trace` — 出库批次追溯表（FIFO）

记录每次出库从哪些入库批次扣减了多少数量，实现完整的批次追溯。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| req_item_id | BIGINT FK | 领用明细 ID，关联 `biz_requisition_item.id` |
| stock_in_item_id | BIGINT FK | 入库批次 ID，关联 `stock_in_item.id` |
| spare_part_id | BIGINT FK | 备件 ID（冗余，便于按备件维度查追溯） |
| deduct_qty | INT | 从该批次扣减的数量 |
| outbound_time | DATETIME | 出库时间 |
| created_at | DATETIME | 记录创建时间 |

**FIFO 逻辑**：出库时按 `stock_in_item.in_time` 升序取可用批次，逐批扣减 `remaining_qty`，每条扣减记录写入本表。

---

## 四、采购管理模块

### `biz_purchase_order` — 采购订单主表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| order_no | VARCHAR(30) UNIQUE | 采购订单号 |
| spare_part_id | BIGINT FK | 备件 ID，关联 `spare_part.id` |
| supplier_id | BIGINT FK | 供应商 ID，关联 `supplier.id` |
| order_qty | INT | 采购数量 |
| unit_price | DECIMAL(10,2) | 成交单价（元） |
| total_amount | DECIMAL(12,2) | 订单总金额（元） |
| order_status | VARCHAR(20) | 状态：已下单/已发货/到货/验收通过/验收失败 |
| expected_date | DATE | 期望到货日期 |
| actual_date | DATE | 实际到货日期 |
| reorder_suggest_id | BIGINT FK | 关联补货建议 ID，关联 `biz_reorder_suggest.id` |
| purchaser_id | BIGINT FK | 采购员 ID，关联 `user.id` |
| remark | VARCHAR(500) | 备注 |
| created_at | DATETIME | 下单时间 |
| updated_at | DATETIME | 更新时间 |

---

### `biz_supplier_quote` — 供应商询价报价表

一次采购可向多家供应商询价，记录各家报价及最终中标情况。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| order_no | VARCHAR(30) | 关联采购订单号 |
| supplier_id | BIGINT FK | 供应商 ID，关联 `supplier.id` |
| spare_part_id | BIGINT FK | 备件 ID，关联 `spare_part.id` |
| quote_price | DECIMAL(10,2) | 报价单价（元） |
| quote_time | DATETIME | 报价时间 |
| delivery_days | INT | 承诺交货天数 |
| is_selected | TINYINT(1) | 是否中标：1=中标，0=未中标 |
| created_at | DATETIME | 创建时间 |

---

### `biz_reorder_suggest` — 补货建议表

由 AI 预测模块或分类模块自动生成，触发采购流程的起点。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| part_code | VARCHAR(20) | 备件编码，关联 `spare_part.code` |
| suggest_month | VARCHAR(7) | 建议月份，格式 `yyyy-MM` |
| current_stock | INT | 当前库存 |
| reorder_point | INT | 补货触发点 ROP |
| suggest_qty | INT | 建议采购量 |
| forecast_qty | DECIMAL(8,2) | 月预测消耗量 |
| lower_bound | DECIMAL(8,2) | 预测置信区间下界 |
| upper_bound | DECIMAL(8,2) | 预测置信区间上界 |
| urgency | VARCHAR(10) | 紧急程度：紧急/正常 |
| status | VARCHAR(20) | 状态：待处理/已采购/已忽略 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

## 五、领用管理模块

### `biz_requisition` — 领用申请主表

记录一次完整的领用申请，流转状态：`PENDING` → `APPROVED`/`REJECTED` → `OUTBOUND` → `INSTALLED`。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| req_no | VARCHAR(30) UNIQUE | 领用单号 |
| applicant_id | BIGINT FK | 申请人 ID，关联 `user.id` |
| work_order_no | VARCHAR(50) | 关联维修工单号（可选） |
| device_id | BIGINT FK | 关联设备 ID，关联 `equipment.id` |
| req_status | VARCHAR(20) | 状态：PENDING/APPROVED/REJECTED/OUTBOUND/INSTALLED |
| is_urgent | TINYINT(1) | 是否紧急（走快速审批通道） |
| approve_id | BIGINT FK | 审批人 ID，关联 `user.id` |
| approve_time | DATETIME | 审批时间 |
| approve_remark | VARCHAR(200) | 审批意见 |
| apply_time | DATETIME | 申请时间 |
| remark | VARCHAR(500) | 申请备注 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `biz_requisition_item` — 领用申请明细表

一次领用申请可包含多种备件。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| req_id | BIGINT FK | 领用申请 ID，关联 `biz_requisition.id` |
| spare_part_id | BIGINT FK | 备件 ID，关联 `spare_part.id` |
| apply_qty | INT | 申请数量 |
| out_qty | INT | 实际出库数量 |
| batch_info | VARCHAR(500) | 批次分配信息摘要，如 `IN20240101[10件]+IN20240102[5件]` |
| install_loc | VARCHAR(100) | 安装位置 |
| install_time | DATETIME | 安装时间 |
| installer_id | BIGINT FK | 安装人 ID，关联 `user.id` |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

> `batch_info` 由 `fifo_migration_v1.sql` 扩展，出库时自动填充 FIFO 批次分配摘要。

---

## 六、维修工单模块

### `biz_work_order` — 维修工单主表

流转状态：`报修` → `已派工` → `维修中` → `完工`。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| work_order_no | VARCHAR(30) UNIQUE | 工单编号 |
| device_id | BIGINT FK | 故障设备 ID，关联 `equipment.id` |
| reporter_id | BIGINT FK | 报修人 ID，关联 `user.id` |
| fault_desc | VARCHAR(500) | 故障描述 |
| fault_level | VARCHAR(10) | 紧急程度：紧急/一般/计划 |
| order_status | VARCHAR(20) | 状态：报修/已派工/维修中/完工 |
| assignee_id | BIGINT FK | 派工维修人员 ID，关联 `user.id` |
| plan_finish | DATETIME | 计划完成时间 |
| actual_finish | DATETIME | 实际完成时间 |
| fault_cause | VARCHAR(500) | 故障根因分析 |
| repair_method | VARCHAR(500) | 维修方案描述 |
| mttr_minutes | INT | 本次维修时长（分钟） |
| part_cost | DECIMAL(10,2) | 备件费用（系统自动汇总） |
| labor_cost | DECIMAL(10,2) | 人工费用 |
| outsource_cost | DECIMAL(10,2) | 外协费用 |
| report_time | DATETIME | 报修时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

## 七、智能分类模块

### `biz_part_classify` — 备件 ABC/XYZ 分类结果表

每次重算生成新记录，保留历史分类轨迹，不做原地更新。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| part_code | VARCHAR(20) | 备件编码，关联 `spare_part.code` |
| classify_month | VARCHAR(7) | 分类所属月份，格式 `yyyy-MM` |
| abc_class | VARCHAR(2) | ABC 分类结果：A/B/C |
| xyz_class | VARCHAR(2) | XYZ 分类结果：X/Y/Z |
| composite_score | DECIMAL(5,2) | ABC 综合加权得分（0～100） |
| annual_cost | DECIMAL(10,2) | 年消耗金额（元） |
| adi | DECIMAL(8,4) | 平均需求间隔 ADI |
| cv2 | DECIMAL(8,4) | 需求变异系数 CV² |
| safety_stock | INT | 安全库存 SS（件） |
| reorder_point | INT | 补货触发点 ROP（件） |
| service_level | DECIMAL(5,2) | 目标服务水平（%） |
| strategy_code | VARCHAR(10) | ABC×XYZ 策略编码，如 `AX`/`BZ` |
| create_time | DATETIME | 记录创建时间 |

**分类矩阵说明**：
- ABC 按年消耗金额排名：A 类（前 70%）/ B 类（70%～90%）/ C 类（后 10%）
- XYZ 按需求变异系数 CV² 划分：X（稳定）/ Y（一般）/ Z（不规则）
- 策略编码组合出 9 种管理策略（AX 重点补货，CZ 按需采购等）

---

## 八、AI 分析模块

### `ai_forecast_result` — AI 需求预测结果表

存储每个备件每月的预测消耗量及置信区间。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| part_code | VARCHAR(20) | 备件编码，关联 `spare_part.code` |
| forecast_month | VARCHAR(7) | 预测目标月份，格式 `yyyy-MM` |
| predict_qty | DECIMAL(8,2) | 预测消耗量（件） |
| lower_bound | DECIMAL(8,2) | 90% 置信区间下界 |
| upper_bound | DECIMAL(8,2) | 90% 置信区间上界 |
| algo_type | VARCHAR(20) | 预测算法：RF/SBA/FALLBACK |
| mase | DECIMAL(6,4) | MASE 评估指标 |
| model_version | VARCHAR(20) | 模型版本号 |
| create_time | DATETIME | 预测计算时间 |

---

### `ai_device_feature` — 设备特征记录表

按月汇总每台设备的运行特征指标，作为 AI 模型训练输入。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| device_id | BIGINT FK | 设备 ID，关联 `equipment.id` |
| stat_month | VARCHAR(7) | 统计月份，格式 `yyyy-MM` |
| run_hours | DECIMAL(8,1) | 月运行时长（小时） |
| fault_count | INT | 当月故障次数 |
| work_order_count | INT | 当月工单数 |
| part_replace_qty | INT | 当月换件总数量 |
| mtbf | DECIMAL(10,2) | 平均故障间隔时间（小时） |
| mttr | DECIMAL(10,2) | 平均修复时间（小时） |
| availability | DECIMAL(5,4) | 可用率（0～1） |
| last_major_fault_date | DATE | 最近重大故障日期 |

唯一约束：`(device_id, stat_month)`  
> `mtbf`、`mttr`、`availability`、`last_major_fault_date` 由 `phm_module.sql` 扩展。

---

### `ai_task_result` — AI 异步任务结果持久化表

替代内存存储，持久化 Celery 异步任务的回调结果。

| 字段 | 类型 | 说明 |
|------|------|------|
| task_id | VARCHAR(128) PK | Celery 任务 ID |
| status | VARCHAR(20) | 任务状态：PENDING/SUCCESS/FAILURE |
| payload | JSON | 回调结果 JSON（SUCCESS 时包含预测数据） |
| error_msg | VARCHAR(500) | 错误信息（FAILURE 时填写） |
| created_at | DATETIME | 首次写入时间 |
| updated_at | DATETIME | 最后更新时间 |

---

### `ai_part_daily_train_data` — AI 每日训练数据集

聚合每个备件每天的出库、领用、工单、到货等维度数据，供模型训练使用。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| biz_date | DATE | 业务日期 |
| spare_part_id | BIGINT FK | 备件 ID，关联 `spare_part.id` |
| part_code | VARCHAR(20) | 备件编码（冗余） |
| daily_outbound_qty | INT | 当日出库数量（标签值） |
| daily_requisition_apply_qty | INT | 当日领用申请数量 |
| daily_requisition_out_qty | INT | 当日领用出库数量 |
| daily_install_qty | INT | 当日安装数量 |
| daily_work_order_cnt | INT | 当日关联工单数 |
| daily_purchase_arrival_qty | INT | 当日到货数量 |
| daily_purchase_arrival_orders | INT | 当日到货订单数 |
| day_of_week | TINYINT | 星期几（1=周日，7=周六） |
| is_weekend | TINYINT(1) | 是否周末 |
| source_level | VARCHAR(20) | 数据来源层级：TRACE/REQ_OUT/TRACE_REQ/NONE |
| is_imputed | TINYINT(1) | 是否为填补数据（当日无真实出库事件） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

唯一约束：`(spare_part_id, biz_date)`

---

## 九、PHM 预测性维护模块

### `ai_device_health` — 设备健康评估记录表

按天记录每台设备的健康评分，支持风险趋势分析。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| device_id | BIGINT FK | 设备 ID，关联 `equipment.id` |
| record_date | DATE | 记录日期 |
| health_score | DECIMAL(5,2) | 健康评分（0～100） |
| risk_level | VARCHAR(20) | 风险等级：LOW/MEDIUM/HIGH/CRITICAL |
| runtime_score | DECIMAL(5,2) | 运行时长评分 |
| fault_score | DECIMAL(5,2) | 故障频次评分 |
| workorder_score | DECIMAL(5,2) | 工单数量评分 |
| replacement_score | DECIMAL(5,2) | 换件频次评分 |
| predicted_failure_days | INT | 预测剩余正常运行天数（NULL 表示低风险） |
| confidence_level | DECIMAL(5,2) | 预测置信度（0～1） |
| algorithm_version | VARCHAR(50) | 算法版本号 |
| created_at | DATETIME | 创建时间 |

唯一约束：`(device_id, record_date)`

**评分阈值**（可通过 `sys_device_health_config` 配置）：
- CRITICAL 设备：< 30 严重，< 50 高风险，< 70 中风险
- IMPORTANT 设备：< 40 严重，< 60 高风险，< 80 中风险
- NORMAL 设备：< 50 严重，< 70 高风险，< 85 中风险

---

### `ai_fault_prediction` — 设备故障预测结果表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| device_id | BIGINT FK | 设备 ID，关联 `equipment.id` |
| prediction_date | DATE | 预测生成日期 |
| target_month | VARCHAR(7) | 预测目标月份，格式 `yyyy-MM` |
| predicted_fault_count | INT | 预测故障次数 |
| predicted_downtime_hours | DECIMAL(10,2) | 预测停机时长（小时） |
| failure_probability | DECIMAL(5,4) | 故障概率（0～1） |
| fault_count_lower | INT | 故障次数置信区间下限 |
| fault_count_upper | INT | 故障次数置信区间上限 |
| feature_importance | JSON | 特征重要性，如 `{"runHours":0.35,"faultCount":0.28}` |
| model_type | VARCHAR(50) | 模型类型：LOGISTIC_REGRESSION/RANDOM_FOREST/HYBRID |
| created_at | DATETIME | 创建时间 |

唯一约束：`(device_id, target_month)`

---

### `biz_maintenance_suggestion` — 预防性维护建议表

由 PHM 模块自动生成，关联健康记录，可采纳后自动创建工单和领用单。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| device_id | BIGINT FK | 设备 ID，关联 `equipment.id` |
| health_record_id | BIGINT FK | 关联健康记录 ID，关联 `ai_device_health.id` |
| suggestion_date | DATE | 建议生成日期 |
| maintenance_type | VARCHAR(50) | 维护类型：PREVENTIVE/PREDICTIVE/EMERGENCY |
| priority_level | VARCHAR(20) | 优先级：HIGH/MEDIUM/LOW |
| suggested_start_date | DATE | 建议开始日期 |
| suggested_end_date | DATE | 建议完成日期 |
| related_spare_parts | JSON | 关联备件需求列表 |
| estimated_cost | DECIMAL(15,2) | 预估维护成本 |
| status | VARCHAR(50) | 状态：PENDING/ACCEPTED/SCHEDULED/COMPLETED/REJECTED |
| workorder_id | BIGINT FK | 采纳后创建的工单 ID，关联 `biz_work_order.id` |
| requisition_id | BIGINT FK | 自动生成的领用单 ID，关联 `biz_requisition.id` |
| reason | TEXT | 建议原因（风险评估摘要） |
| reject_reason | TEXT | 拒绝原因 |
| handled_by | BIGINT FK | 处理人 ID，关联 `user.id` |
| handled_at | DATETIME | 处理时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

---

### `sys_device_health_config` — 设备健康阈值配置表

全局可配置的健康评分阈值与权重，支持按设备类型和重要性差异化配置。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键，自增 |
| device_type | VARCHAR(100) | 设备类型（NULL 表示全局默认） |
| importance_level | VARCHAR(50) | 设备重要性：CRITICAL/IMPORTANT/NORMAL |
| critical_threshold | DECIMAL(5,2) | 严重风险阈值（评分低于此值触发），默认 40 |
| high_threshold | DECIMAL(5,2) | 高风险阈值，默认 60 |
| medium_threshold | DECIMAL(5,2) | 中风险阈值，默认 80 |
| runtime_weight | DECIMAL(4,3) | 运行时长权重，默认 0.25 |
| fault_weight | DECIMAL(4,3) | 故障频次权重，默认 0.35 |
| workorder_weight | DECIMAL(4,3) | 工单数量权重，默认 0.20 |
| replacement_weight | DECIMAL(4,3) | 换件频次权重，默认 0.20 |
| prediction_window_days | INT | 预测窗口（天），默认 90 |
| min_history_months | INT | 最少历史数据月数，默认 6 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

唯一约束：`(device_type, importance_level)`

---

## 附录：关键业务流程与表联动

### 采购入库流程
```
biz_reorder_suggest（补货建议）
  → biz_purchase_order（采购订单）+ purchase_order_item（订单明细）
  → biz_supplier_quote（询价比价）
  → stock_in_receipt（入库收货单）
  → stock_in_item（入库明细，生成 FIFO 批次）
  → spare_part_stock / spare_part_location_stock（库存更新）
```

### 领用出库流程（FIFO）
```
biz_requisition（领用申请）
  → biz_requisition_item（领用明细）
  → stock_in_item（FIFO 批次扣减，by in_time 升序）
  → biz_outbound_batch_trace（批次追溯记录）
  → spare_part_stock（总库存递减）
```

### PHM 预测性维护流程
```
ai_device_feature（设备月度特征）
  → ai_device_health（健康评估，by 日）
  → ai_fault_prediction（故障预测，by 月）
  → biz_maintenance_suggestion（维护建议）
  → biz_work_order（维修工单）+ biz_requisition（配套领用）
```

### AI 需求预测流程
```
ai_part_daily_train_data（每日训练数据）
  → [Python AI 服务训练]
  → ai_forecast_result（预测结果）
  → biz_reorder_suggest（补货建议触发）
  → ai_task_result（异步任务状态追踪）
```
