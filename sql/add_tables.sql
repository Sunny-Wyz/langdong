USE `spare_db`;

-- 采购单主表
CREATE TABLE IF NOT EXISTS `purchase_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `po_code` varchar(50) NOT NULL COMMENT '采购单号',
  `supplier_id` bigint(20) NOT NULL COMMENT '供应商ID',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态(PENDING, RECEIVED, COMPLETED)',
  `total_amount` decimal(10,2) DEFAULT NULL COMMENT '总金额',
  `expected_delivery_date` date DEFAULT NULL COMMENT '预计交货日期',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_po_code` (`po_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单主表';

-- 采购单明细表
CREATE TABLE IF NOT EXISTS `purchase_order_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `purchase_order_id` bigint(20) NOT NULL COMMENT '采购单ID',
  `spare_part_id` bigint(20) NOT NULL COMMENT '备件ID',
  `quantity` int(11) NOT NULL COMMENT '采购数量',
  `unit_price` decimal(10,2) DEFAULT NULL COMMENT '单价',
  `received_quantity` int(11) NOT NULL DEFAULT '0' COMMENT '已收货数量',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单明细表';

-- 入库单主表
CREATE TABLE IF NOT EXISTS `stock_in_receipt` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `receipt_code` varchar(50) NOT NULL COMMENT '入库单号',
  `purchase_order_id` bigint(20) DEFAULT NULL COMMENT '关联采购单ID',
  `receipt_date` datetime NOT NULL COMMENT '入库时间',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态',
  `handler_id` bigint(20) DEFAULT NULL COMMENT '处理人',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_receipt_code` (`receipt_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单主表';

-- 入库单明细表
CREATE TABLE IF NOT EXISTS `stock_in_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_in_receipt_id` bigint(20) NOT NULL COMMENT '入库单ID',
  `purchase_order_item_id` bigint(20) DEFAULT NULL COMMENT '关联采购明细ID',
  `spare_part_id` bigint(20) NOT NULL COMMENT '备件ID',
  `expected_quantity` int(11) NOT NULL COMMENT '预计入库数量',
  `actual_quantity` int(11) NOT NULL COMMENT '实际入库数量',
  `shelved_quantity` int(11) NOT NULL DEFAULT '0' COMMENT '已上架数量',
  `location_id` bigint(20) DEFAULT NULL COMMENT '默认货位',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单明细表';

-- 备件总库存表
CREATE TABLE IF NOT EXISTS `spare_part_stock` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `spare_part_id` bigint(20) NOT NULL COMMENT '备件ID',
  `quantity` int(11) NOT NULL DEFAULT '0' COMMENT '总数量',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_spare_part_id` (`spare_part_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件总库存表';

-- 备件货位库存表（台账）
CREATE TABLE IF NOT EXISTS `spare_part_location_stock` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `location_id` bigint(20) NOT NULL COMMENT '货位ID',
  `spare_part_id` bigint(20) NOT NULL COMMENT '备件ID',
  `quantity` int(11) NOT NULL DEFAULT '0' COMMENT '数量',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_location_spare` (`location_id`, `spare_part_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货位库存台账表';
