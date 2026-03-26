# 数据库核心表设计速查表

**最后更新**: 2026-03-26 | **总表数**: 20  

---

## 📋 表名导航（按模块分组）

| 模块 | 表名 | 功能 |
|------|------|------|
| **系统** | sys_user, sys_role, sys_menu | 权限管理 |
| **基础** | spare_part, location, equipment, supplier | 字典数据 |
| **仓储** | stock_in_item, spare_part_stock | FIFO 批次管理 |
| **领用** | biz_requisition, biz_requisition_item | 申请审批链 |
| **维修** | biz_work_order, biz_work_order_item | 工单管理 |
| **采购** | biz_purchase_order, biz_purchase_item | 订单管理 |
| **AI** | ai_forecast_data, ai_forecast_result, biz_part_classify | 预测分类 |
| **报表** | (无专用表) | 由上述表组织查询 |

---

## 🔐 基础表（UC-BASE）

### `spare_part` - 备件档案

```sql
CREATE TABLE spare_part (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) UNIQUE NOT NULL COMMENT '备件编号（8位，如 SP20001）',
    name VARCHAR(100) NOT NULL COMMENT '备件名称',
    category_id BIGINT COMMENT '品类分类 ID',
    supplier_id BIGINT COMMENT '常用供应商 ID',
    price DECIMAL(10,2) COMMENT '单价（元）',
    lead_time_days INT DEFAULT 7 COMMENT '供应商提前期（天）',
    
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    remark VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_code (code),
    INDEX idx_category (category_id),
    INDEX idx_status (status)
);
```

**关键字段说明**：
- `code`: 备件唯一识别码，自动化系统依赖此字段
- `lead_time_days`: 用于 ROP 计算公式 `ROP = d×L + SS`
- `price`: 用于 ABC 分类金额统计

---

### `location` - 货位档案

```sql
CREATE TABLE location (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) UNIQUE NOT NULL COMMENT '货位编码（如 A01-01-01）',
    warehouse_id BIGINT COMMENT '所属仓库',
    section VARCHAR(20) COMMENT '分区（A/B/C 区）',
    shelf_num INT COMMENT '架号',
    layer_num INT COMMENT '层号',
    position_num INT COMMENT '位号',
    
    capacity INT COMMENT '容积(件)',
    current_qty INT DEFAULT 0 COMMENT '当前存放数量',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_warehouse (warehouse_id),
    INDEX idx_section (section)
);
```

---

### `equipment` - 设备档案

```sql
CREATE TABLE equipment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) UNIQUE NOT NULL COMMENT '设备编码',
    model VARCHAR(50) COMMENT '设备型号',
    department BIGINT COMMENT '所属部门',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_dept (department)
);
```

---

### `supplier` - 供应商档案

```sql
CREATE TABLE supplier (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    contact_person VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(50),
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_code (code)
);
```

---

## 📦 仓储表（UC-WH）

### `spare_part_stock` - 总库存快照

```sql
CREATE TABLE spare_part_stock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spare_part_id BIGINT UNIQUE NOT NULL COMMENT '备件 ID',
    quantity INT DEFAULT 0 COMMENT '总库存数量',
    last_update DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_spare_part (spare_part_id),
    FOREIGN KEY (spare_part_id) REFERENCES spare_part(id)
);
```

**关键设计**：
- 冗余总库存字段加快查询
- 批次库存 sum(stock_in_item.remaining_qty) 应与此相等
- 每次出库同时更新此表

---

### `stock_in_item` - 入库批次（FIFO 核心）

```sql
CREATE TABLE stock_in_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_order_id BIGINT COMMENT '采购订单 ID',
    spare_part_id BIGINT NOT NULL,
    
    in_qty INT NOT NULL COMMENT '入库数量',
    remaining_qty INT NOT NULL COMMENT '当前剩余量（FIFO 出库时扣减）',
    in_time DATETIME NOT NULL COMMENT '入库时间（FIFO 排序依据）',
    
    location_id BIGINT COMMENT '货位 ID',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    /** FIFO 核心索引 **/
    INDEX idx_spare_part_fifo (spare_part_id, in_time, remaining_qty),
    INDEX idx_location (location_id),
    
    FOREIGN KEY (spare_part_id) REFERENCES spare_part(id),
    FOREIGN KEY (location_id) REFERENCES location(id)
);
```

**FIFO 查询模式**：
```sql
-- 按 in_time 升序找可用批次
SELECT * FROM stock_in_item
WHERE spare_part_id = ?
  AND remaining_qty > 0
ORDER BY in_time ASC;
```

---

### `biz_outbound_batch_trace` - 批次追溯表

```sql
CREATE TABLE biz_outbound_batch_trace (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    req_item_id BIGINT NOT NULL COMMENT '领用明细 ID',
    stock_in_item_id BIGINT NOT NULL COMMENT '出库自哪个入库批次',
    spare_part_id BIGINT NOT NULL COMMENT '冗余，加速查询',
    
    deduct_qty INT NOT NULL COMMENT '本次扣减数量',
    outbound_time DATETIME NOT NULL COMMENT '出库时间',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_req_item (req_item_id),
    INDEX idx_stock_in (stock_in_item_id),
    INDEX idx_spare_part (spare_part_id),
    
    FOREIGN KEY (stock_in_item_id) REFERENCES stock_in_item(id)
);
```

**追溯查询**：
```sql
-- 某个领用明细使用了哪些批次
SELECT t.*, s.in_time, s.in_qty 
FROM biz_outbound_batch_trace t
JOIN stock_in_item s ON t.stock_in_item_id = s.id
WHERE t.req_item_id = ?;
```

---

## 📝 领用表（UC-REQ）

### `biz_requisition` - 领用单头

```sql
CREATE TABLE biz_requisition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '申请单号',
    requester_id BIGINT COMMENT '申请人 ID',
    approver_id BIGINT COMMENT '批准人 ID',
    
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态：DRAFT/PENDING/APPROVED/OUTBOUND/COMPLETED/REJECTED',
    reason VARCHAR(255) COMMENT '申请原因',
    
    request_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    approve_time DATETIME COMMENT '批准时间',
    confirm_time DATETIME COMMENT '确认时间',
    
    created_at DATETIME,
    updated_at DATETIME,
    
    INDEX idx_requester (requester_id),
    INDEX idx_status (status),
    INDEX idx_request_time (request_time)
);
```

---

### `biz_requisition_item` - 领用单明细

```sql
CREATE TABLE biz_requisition_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    req_id BIGINT NOT NULL,
    spare_part_id BIGINT NOT NULL,
    
    request_qty INT NOT NULL COMMENT '申请数量',
    out_qty INT COMMENT '实际出库数量',
    
    install_loc VARCHAR(100) COMMENT '安装位置',
    install_time DATETIME COMMENT '安装时间',
    
    batch_info VARCHAR(500) COMMENT '批次分配信息（JSON），FIFO 追溯用',
    
    created_at DATETIME,
    
    INDEX idx_req (req_id),
    INDEX idx_spare_part (spare_part_id),
    
    FOREIGN KEY (req_id) REFERENCES biz_requisition(id)
);
```

---

## 🛠️ 维修表（UC-WO）

### `biz_work_order` - 工单头

```sql
CREATE TABLE biz_work_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL,
    equipment_id BIGINT,
    
    description TEXT COMMENT '故障描述',
    priority VARCHAR(20) COMMENT '优先级：LOW/MEDIUM/HIGH/URGENT',
    status VARCHAR(20) DEFAULT 'NEW' COMMENT 'NEW/ASSIGNED/IN_PROGRESS/COMPLETED',
    
    reported_by BIGINT,
    assigned_to BIGINT,
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME,
    
    INDEX idx_status (status),
    INDEX idx_equipment (equipment_id),
    INDEX idx_assigned (assigned_to)
);
```

---

### `biz_work_order_item` - 工单维修记录

```sql
CREATE TABLE biz_work_order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    work_order_id BIGINT NOT NULL,
    
    spare_part_id BIGINT COMMENT '更换备件',
    qty_replaced INT COMMENT '更换数量',
    failure_analysis TEXT COMMENT '故障分析',
    repair_notes TEXT COMMENT '维修说明',
    
    labor_hours INT COMMENT '维修工时（小时）',
    labor_cost DECIMAL(10,2) COMMENT '人工费（元）',
    material_cost DECIMAL(10,2) COMMENT '物料费',
    
    created_at DATETIME,
    
    INDEX idx_work_order (work_order_id),
    INDEX idx_spare_part (spare_part_id),
    
    FOREIGN KEY (work_order_id) REFERENCES biz_work_order(id)
);
```

---

## 🛒 采购表（UC-PO）

### `biz_purchase_order` - 采购订单

```sql
CREATE TABLE biz_purchase_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL,
    supplier_id BIGINT NOT NULL,
    
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/CONFIRMED/RECEIVED/CLOSED',
    est_delivery DATETIME COMMENT '预计交期',
    actual_delivery DATETIME COMMENT '实际交期',
    
    total_amount DECIMAL(12,2),
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME,
    
    INDEX idx_supplier (supplier_id),
    INDEX idx_status (status)
);
```

---

### `biz_purchase_item` - 采购订单明细

```sql
CREATE TABLE biz_purchase_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    spare_part_id BIGINT NOT NULL,
    
    qty INT NOT NULL COMMENT '订购数量',
    received_qty INT DEFAULT 0 COMMENT '已收货数量',
    unit_price DECIMAL(10,2),
    
    created_at DATETIME,
    
    INDEX idx_po (purchase_order_id),
    INDEX idx_spare_part (spare_part_id),
    
    FOREIGN KEY (purchase_order_id) REFERENCES biz_purchase_order(id)
);
```

---

## 🤖 AI 表（UC-AI）

### `ai_forecast_data` - 历史消耗数据

```sql
CREATE TABLE ai_forecast_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spare_part_id BIGINT NOT NULL,
    year_month VARCHAR(10) NOT NULL COMMENT '格式：YYYY-MM',
    
    consume_qty INT DEFAULT 0 COMMENT '该月消耗数量',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uniq_spare_ym (spare_part_id, year_month),
    INDEX idx_spare_part (spare_part_id),
    
    FOREIGN KEY (spare_part_id) REFERENCES spare_part(id)
);
```

**数据来源**：每月底从 biz_requisition_item 聚合消耗数据

---

### `ai_forecast_result` - 预测结果存储

```sql
CREATE TABLE ai_forecast_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spare_part_id BIGINT NOT NULL,
    forecast_month VARCHAR(10) COMMENT '预测月份（YYYY-MM）',
    
    algorithm_type VARCHAR(20) COMMENT 'RF/SBA/FALLBACK',
    predict_qty INT COMMENT '预测数量',
    lower_bound INT COMMENT '90% CI 下界',
    upper_bound INT COMMENT '90% CI 上界',
    confidence DECIMAL(3,2) COMMENT '置信度',
    
    is_valid TINYINT DEFAULT 1 COMMENT '是否有效',
    validation_error VARCHAR(255),
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uniq_spare_month (spare_part_id, forecast_month),
    INDEX idx_algorithm (algorithm_type)
);
```

---

### `biz_part_classify` - ABC×XYZ 分类结果

```sql
CREATE TABLE biz_part_classify (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spare_part_id BIGINT NOT NULL,
    year_month VARCHAR(10) COMMENT '分类所用数据月份',
    
    abc_class VARCHAR(1) COMMENT 'A/B/C',
    xyz_class VARCHAR(1) COMMENT 'X/Y/Z',
    
    annual_consumption_value DECIMAL(12,2) COMMENT '年消耗金额',
    adi DECIMAL(5,2) COMMENT '平均需求间隔',
    cv2 DECIMAL(5,3) COMMENT '变异系数平方',
    
    safety_stock INT COMMENT '该月计算的安全库存',
    reorder_point INT COMMENT '补货点 ROP',
    
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uniq_spare_month (spare_part_id, year_month),
    INDEX idx_class (abc_class, xyz_class)
);
```

---

## 📊 查询模板

### 1. FIFO 可用批次查询
```sql
-- 按入库时间升序，先出库最老的批次
SELECT id, spare_part_id, in_time, remaining_qty
FROM stock_in_item
WHERE spare_part_id = @spare_part_id
  AND remaining_qty > 0
ORDER BY in_time ASC
LIMIT 10;
```

### 2. 月度消耗汇总
```sql
-- 聚合该月出库数量
SELECT 
    spare_part_id,
    SUM(out_qty) as month_consume
FROM biz_requisition_item ri
JOIN biz_requisition r ON ri.req_id = r.id
WHERE YEAR_MONTH(r.confirm_time) = '2026-02'
  AND r.status = 'COMPLETED'
GROUP BY spare_part_id;
```

### 3. 库存预警
```sql
-- 库存 ≤ ROP 需要补货
SELECT 
    sp.code, sp.name,
    sps.quantity as current_stock,
    bpc.reorder_point as rop,
    (bpc.reorder_point - sps.quantity) as shortage
FROM spare_part sp
JOIN spare_part_stock sps ON sp.id = sps.spare_part_id
JOIN biz_part_classify bpc ON sp.id = bpc.spare_part_id
WHERE sps.quantity <= bpc.reorder_point
ORDER BY shortage DESC;
```

---

**维护人**: 数据库架构团队  
**版本**: 1.0 (2026-03-26)
