-- 采购管理模块 M6 数据库迁移脚本
-- MySQL 5.7 兼容：不使用 ADD COLUMN IF NOT EXISTS / JSON_TABLE / 窗口函数
USE spare_db;

-- ================================================================
-- 1. 补货建议表
-- ================================================================
CREATE TABLE IF NOT EXISTS `biz_reorder_suggest` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `part_code`     VARCHAR(20)   NOT NULL COMMENT '备件编码',
    `suggest_month` VARCHAR(7)    NOT NULL COMMENT '建议月份(yyyy-MM)',
    `current_stock` INT           NOT NULL DEFAULT 0 COMMENT '当前库存',
    `reorder_point` INT           NOT NULL DEFAULT 0 COMMENT '补货触发点 ROP',
    `suggest_qty`   INT           NOT NULL DEFAULT 0 COMMENT '建议采购量',
    `forecast_qty`  DECIMAL(8,2)  NOT NULL DEFAULT 0 COMMENT '月预测消耗量',
    `lower_bound`   DECIMAL(8,2)  DEFAULT NULL COMMENT '预测置信区间下界',
    `upper_bound`   DECIMAL(8,2)  DEFAULT NULL COMMENT '预测置信区间上界',
    `urgency`       VARCHAR(10)   NOT NULL DEFAULT '正常' COMMENT '紧急程度：紧急/正常',
    `status`        VARCHAR(20)   NOT NULL DEFAULT '待处理' COMMENT '状态：待处理/已采购/已忽略',
    `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_part_code`     (`part_code`),
    KEY `idx_status`        (`status`),
    KEY `idx_suggest_month` (`suggest_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货建议表';

-- ================================================================
-- 2. 采购订单表
-- ================================================================
CREATE TABLE IF NOT EXISTS `biz_purchase_order` (
    `id`                  BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`            VARCHAR(30)    NOT NULL COMMENT '采购订单号，全局唯一',
    `spare_part_id`       BIGINT         NOT NULL COMMENT '备件ID，关联 spare_part.id',
    `supplier_id`         BIGINT         NOT NULL COMMENT '供应商ID，关联 supplier.id',
    `order_qty`           INT            NOT NULL COMMENT '采购数量',
    `unit_price`          DECIMAL(10,2)  DEFAULT NULL COMMENT '成交单价（元）',
    `total_amount`        DECIMAL(12,2)  DEFAULT NULL COMMENT '订单总金额（元）',
    `order_status`        VARCHAR(20)    NOT NULL DEFAULT '已下单' COMMENT '状态：已下单/已发货/到货/验收通过/验收失败',
    `expected_date`       DATE           DEFAULT NULL COMMENT '期望到货日期',
    `actual_date`         DATE           DEFAULT NULL COMMENT '实际到货日期',
    `reorder_suggest_id`  BIGINT         DEFAULT NULL COMMENT '关联补货建议ID',
    `purchaser_id`        BIGINT         DEFAULT NULL COMMENT '采购员ID',
    `remark`              VARCHAR(500)   DEFAULT NULL COMMENT '备注',
    `created_at`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    `updated_at`          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_spare_part_id`  (`spare_part_id`),
    KEY `idx_supplier_id`    (`supplier_id`),
    KEY `idx_order_status`   (`order_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单表';

-- ================================================================
-- 3. 供应商询价报价表
-- ================================================================
CREATE TABLE IF NOT EXISTS `biz_supplier_quote` (
    `id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`     VARCHAR(30)    NOT NULL COMMENT '关联采购订单号',
    `supplier_id`  BIGINT         NOT NULL COMMENT '供应商ID',
    `spare_part_id` BIGINT        NOT NULL COMMENT '备件ID',
    `quote_price`  DECIMAL(10,2)  NOT NULL COMMENT '报价单价（元）',
    `quote_time`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报价时间',
    `delivery_days` INT           DEFAULT NULL COMMENT '承诺交货天数',
    `is_selected`  TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '是否中标',
    `created_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_order_no`    (`order_no`),
    KEY `idx_supplier_id` (`supplier_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商询价报价表';

-- ================================================================
-- 4. 菜单：parent_id=15（采购管理模块目录），省略 id 使用 auto-increment
-- ================================================================
INSERT INTO `menu` (`parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`) VALUES
(15, '智能补货建议',   '/home/purchase-suggestions', 'purchase/PurchaseSuggestions', 'po:suggest:list',    2, 'el-icon-bell',          1),
(15, '发起采购申请',   '/home/purchase-apply',       'purchase/PurchaseApply',       'po:order:add',       2, 'el-icon-shopping-cart-2',2),
(15, '供应商询价比价', '/home/purchase-quote',       'purchase/PurchaseQuote',       'po:quote:edit',      2, 'el-icon-price-tag',      3),
(15, '采购订单管理',   '/home/purchase-orders',      'purchase/PurchaseOrders',      'po:order:list',      2, 'el-icon-document',       4),
(15, '到货验收',       '/home/purchase-acceptance',  'purchase/PurchaseAcceptance',  'po:receive:confirm', 2, 'el-icon-circle-check',   5);

-- 授权给超级管理员角色（role_id=1）
INSERT INTO `role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `menu` WHERE `path` IN (
    '/home/purchase-suggestions',
    '/home/purchase-apply',
    '/home/purchase-quote',
    '/home/purchase-orders',
    '/home/purchase-acceptance'
);

-- ================================================================
-- 5. 插入测试用补货建议数据（方便测试）
-- ================================================================
INSERT INTO `biz_reorder_suggest` (`part_code`, `suggest_month`, `current_stock`, `reorder_point`, `suggest_qty`, `forecast_qty`, `lower_bound`, `upper_bound`, `urgency`, `status`)
SELECT sp.code, DATE_FORMAT(NOW(), '%Y-%m'), 5, 10, 20, 18.5, 15.0, 22.0, '紧急', '待处理'
FROM spare_part sp LIMIT 3;
