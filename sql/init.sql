SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS spare_db DEFAULT CHARACTER SET utf8mb4;
USE spare_db;

CREATE TABLE IF NOT EXISTS `user` (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(50)  NOT NULL COMMENT '用户�?,
    name       VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    password   VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密�?,
    status     TINYINT      DEFAULT 1 COMMENT '状�?1正常 0停用)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户�?;

CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色档案�?;

CREATE TABLE IF NOT EXISTS `menu` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父菜单ID',
    `name` VARCHAR(50) NOT NULL COMMENT '菜单/按钮名称',
    `path` VARCHAR(200) DEFAULT NULL COMMENT '路由路径',
    `component` VARCHAR(200) DEFAULT NULL COMMENT '组件路径',
    `permission` VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
    `type` TINYINT NOT NULL COMMENT '类型(1目录 2菜单 3按钮)',
    `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标',
    `sort` INT DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限字典�?;

CREATE TABLE IF NOT EXISTS `user_role` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联�?;

CREATE TABLE IF NOT EXISTS `role_menu` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联�?;

CREATE TABLE IF NOT EXISTS `spare_part_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(4) NOT NULL COMMENT '分类编码(大类�?位，小类�?�?',
    `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父类ID(大类为空)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件分类字典�?;

CREATE TABLE IF NOT EXISTS `spare_part` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(8)     NOT NULL COMMENT '备件统一8位编�?,
    name       VARCHAR(100)   NOT NULL COMMENT '备件名称',
    model      VARCHAR(100)   DEFAULT NULL COMMENT '型号规格',
    quantity   INT            NOT NULL DEFAULT 0 COMMENT '库存数量',
    unit       VARCHAR(20)    DEFAULT '�? COMMENT '单位',
    price      DECIMAL(10, 2) DEFAULT NULL COMMENT '单价',
    category_id BIGINT        NOT NULL COMMENT '所属分类ID',
    supplier   VARCHAR(100)   DEFAULT NULL COMMENT '供应�?,
    remark     TEXT           DEFAULT NULL COMMENT '备注',
    location_id BIGINT        DEFAULT NULL COMMENT '所属货位ID',
    supplier_id BIGINT        DEFAULT NULL COMMENT '供应商ID',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY `uk_spare_part_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件档案�?;

CREATE TABLE IF NOT EXISTS `location` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(50)    NOT NULL COMMENT '货位编码',
    name       VARCHAR(100)   NOT NULL COMMENT '货位名称',
    zone       VARCHAR(50)    NOT NULL COMMENT '所属专�?1-12)',
    capacity   VARCHAR(50)    DEFAULT NULL COMMENT '容量',
    remark     TEXT           DEFAULT NULL COMMENT '备注',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货位档案�?;

CREATE TABLE IF NOT EXISTS `equipment` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(50)    NOT NULL COMMENT '设备编码',
    name       VARCHAR(100)   NOT NULL COMMENT '设备名称',
    model      VARCHAR(100)   DEFAULT NULL COMMENT '规格型号',
    department VARCHAR(100)   DEFAULT NULL COMMENT '所属部�?产线',
    status     VARCHAR(50)    DEFAULT '正常' COMMENT '设备状�?,
    remark     TEXT           DEFAULT NULL COMMENT '备注',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备档案�?;

CREATE TABLE IF NOT EXISTS `supply_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(50) NOT NULL COMMENT '品类编码',
    `name` VARCHAR(100) NOT NULL COMMENT '品类名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supply_category_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供货品类字典�?;

CREATE TABLE IF NOT EXISTS `supplier` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(50) NOT NULL COMMENT '供应商编�?,
    `name` VARCHAR(100) NOT NULL COMMENT '供应商名�?,
    `unified_social_credit_code` VARCHAR(50) DEFAULT NULL COMMENT '统一社会信用代码',
    `bank_account_info` VARCHAR(255) DEFAULT NULL COMMENT '银行账户信息',
    `contact_person` VARCHAR(50) DEFAULT NULL COMMENT '联系�?,
    `phone` VARCHAR(30) DEFAULT NULL COMMENT '联系电话',
    `address` VARCHAR(255) DEFAULT NULL COMMENT '地址',
    `status` VARCHAR(20) DEFAULT '正常' COMMENT '状�?,
    `remark` TEXT DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplier_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商档案表';

CREATE TABLE IF NOT EXISTS `supplier_category_relation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `supplier_id` BIGINT NOT NULL COMMENT '供应商ID',
    `supply_category_id` BIGINT NOT NULL COMMENT '供货品类ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_supplier_category` (`supplier_id`, `supply_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商品类关联表';

CREATE TABLE IF NOT EXISTS `equipment_spare_part` (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    equipment_id   BIGINT   NOT NULL COMMENT '设备ID',
    spare_part_id  BIGINT   NOT NULL COMMENT '备件ID',
    quantity       INT      DEFAULT 1 COMMENT '配套数量',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eq_sp (equipment_id, spare_part_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备备件配套关联�?;

-- 初始账号：admin / 123456
INSERT INTO `user` (id, username, name, password, status) VALUES (
    1,
    'admin',
    '系统管理�?,
    '$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa',
    1
);

INSERT INTO `role` (`id`, `code`, `name`, `remark`) VALUES (1, 'ADMIN', '超级管理�?, '系统最高权�?);
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 1);

INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`) VALUES
(1, NULL, '系统管理', '/sys', 'Layout', NULL, 1, 'el-icon-setting', 99),
(2, 1, '用户管理', '/sys/users', 'sys/UserManage', 'sys:user:list', 2, 'el-icon-user', 1),
(3, 1, '角色与菜单分�?, '/sys/roles', 'sys/RoleManage', 'sys:role:list', 2, 'el-icon-key', 2),
(4, NULL, '基础数据管理', '/home', 'Layout', NULL, 1, 'el-icon-suitcase-1', 1),
(5, 4, '备件档案管理', '/home/spare-parts', 'SparePartList', 'base:spare:list', 2, 'el-icon-s-order', 1),
(6, 5, '备件新增(按钮)', NULL, NULL, 'base:spare:add', 3, NULL, 1),
(7, 4, '货位档案管理', '/home/location-profiles', 'LocationProfile', 'base:location:list', 2, 'el-icon-location-information', 2),
(8, 4, '设备档案管理', '/home/equipment-profiles', 'EquipmentProfile', 'base:equipment:list', 2, 'el-icon-odometer', 3),
(9, 4, '供应商档案管�?, '/home/supplier-profiles', 'SupplierProfile', 'base:supplier:list', 2, 'el-icon-truck', 4),
(10, 4, '品类字典�?, '/home/supply-categories', 'SupplyCategory', 'base:category:list', 2, 'el-icon-collection-tag', 5),
(11, NULL, '备件智能分类模块', '/smart', 'Layout', NULL, 1, 'el-icon-collection', 2),
(12, NULL, '仓储管理模块', '/warehouse', 'Layout', NULL, 1, 'el-icon-box', 3),
(13, NULL, '领用管理模块', '/requisition', 'Layout', NULL, 1, 'el-icon-sell', 4),
(14, NULL, '维修工单管理模块', '/maintenance', 'Layout', NULL, 1, 'el-icon-s-tools', 5),
(15, NULL, '采购管理模块', '/procurement', 'Layout', NULL, 1, 'el-icon-shopping-cart-full', 6),
(16, NULL, 'AI智能分析模块', '/ai', 'Layout', NULL, 1, 'el-icon-cpu', 7),
(17, NULL, '报表与看板模�?, '/reports', 'Layout', NULL, 1, 'el-icon-data-board', 8);

INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6),
(1, 7), (1, 8), (1, 9), (1, 10), (1, 11), (1, 12), (1, 13), (1, 14), (1, 15), (1, 16), (1, 17);
CREATE TABLE IF NOT EXISTS classification_strategy ( id BIGINT NOT NULL AUTO_INCREMENT COMMENT '����', combination_code VARCHAR(10) NOT NULL COMMENT '��ϴ���(��AX)', abc_category VARCHAR(2) NOT NULL COMMENT 'ABC����', xyz_category VARCHAR(2) NOT NULL COMMENT 'XYZ����', safety_stock_multiplier DECIMAL(5,2) NOT NULL COMMENT '��ȫ���ϵ��', replenishment_cycle INT NOT NULL COMMENT '��������(��)', approval_level VARCHAR(50) NOT NULL COMMENT '�����ȼ�', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '����ʱ��', updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '����ʱ��', PRIMARY KEY (id), UNIQUE KEY uk_combination (combination_code) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='����������ñ�';
CREATE TABLE IF NOT EXISTS spare_part_classification ( id BIGINT NOT NULL AUTO_INCREMENT COMMENT '����', spare_part_id BIGINT NOT NULL COMMENT '����ID', abc_category VARCHAR(2) NOT NULL COMMENT 'ABC����(A/B/C)', xyz_category VARCHAR(2) NOT NULL COMMENT 'XYZ����(X/Y/Z)', combination_code VARCHAR(10) NOT NULL COMMENT '��ϴ���(��AX)', abc_score DECIMAL(10,2) DEFAULT NULL COMMENT 'ABC�ۺϵ÷�', xyz_cv DECIMAL(10,4) DEFAULT NULL COMMENT 'XYZ�������ϵ��', predicted_demand DECIMAL(10,2) DEFAULT NULL COMMENT '����Ԥ��������', cost_score DECIMAL(10,2) DEFAULT NULL COMMENT '�����Ľ��÷�', critical_score DECIMAL(10,2) DEFAULT NULL COMMENT '�豸�ؼ��ȵ÷�', lead_time_score DECIMAL(10,2) DEFAULT NULL COMMENT '�ɹ���ǰ�ڵ÷�', difficulty_score DECIMAL(10,2) DEFAULT NULL COMMENT '����Ѷȵ÷�', is_manual_adjusted TINYINT DEFAULT 0 COMMENT '�Ƿ��˹�����(0�� 1��)', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '����/����ʱ��', updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '����ʱ��', PRIMARY KEY (id), UNIQUE KEY uk_spare_part_id (spare_part_id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='�������ܷ�������';
CREATE TABLE IF NOT EXISTS classification_adjustment_record ( id BIGINT NOT NULL AUTO_INCREMENT COMMENT '����', spare_part_id BIGINT NOT NULL COMMENT '����ID', original_combination VARCHAR(10) NOT NULL COMMENT '����ǰ��ϴ���', new_combination VARCHAR(10) NOT NULL COMMENT '��������ϴ���', reason TEXT NOT NULL COMMENT '����ԭ��', applicant_id BIGINT NOT NULL COMMENT '������ID', approver_id BIGINT DEFAULT NULL COMMENT '������ID', status VARCHAR(20) DEFAULT 'PENDING' COMMENT '״̬(PENDING������, APPROVED��ͨ��, REJECTED�Ѿܾ�)', approval_remark TEXT DEFAULT NULL COMMENT '�������', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '����ʱ��', updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '����ʱ��', PRIMARY KEY (id) ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='�������������¼��';
INSERT IGNORE INTO classification_strategy (combination_code, abc_category, xyz_category, safety_stock_multiplier, replenishment_cycle, approval_level) VALUES ('AX', 'A', 'X', 1.50, 7, '��������'), ('AY', 'A', 'Y', 1.80, 14, '��������'), ('AZ', 'A', 'Z', 2.50, 21, '�ܼ�����'), ('BX', 'B', 'X', 1.20, 14, '��������'), ('BY', 'B', 'Y', 1.50, 21, '��������'), ('BZ', 'B', 'Z', 1.80, 30, '��������'), ('CX', 'C', 'X', 1.00, 30, 'ϵͳ�Զ�'), ('CY', 'C', 'Y', 1.20, 60, '��������'), ('CZ', 'C', 'Z', 1.50, 90, '��������');
INSERT INTO menu (id, parent_id, name, path, component, permission, type, icon, sort) VALUES (18, 11, '��������', '/home/smart/strategies', 'smart/StrategyConfig', 'smart:strategy:list', 2, 'el-icon-setting', 1), (19, 11, '����������', '/home/smart/dashboard', 'smart/ClassificationDashboard', 'smart:dashboard:list', 2, 'el-icon-data-analysis', 2), (20, 11, '��������', '/home/smart/approvals', 'smart/AdjustmentApproval', 'smart:approval:list', 2, 'el-icon-s-check', 3); INSERT INTO role_menu (role_id, menu_id) VALUES (1, 18), (1, 19), (1, 20);

USE `spare_db`;

-- 采购单主�?
CREATE TABLE IF NOT EXISTS `purchase_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `po_code` varchar(50) NOT NULL COMMENT '采购单号',
  `supplier_id` bigint(20) NOT NULL COMMENT '供应商ID',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状�?PENDING, RECEIVED, COMPLETED)',
  `total_amount` decimal(10,2) DEFAULT NULL COMMENT '总金�?,
  `expected_delivery_date` date DEFAULT NULL COMMENT '预计交货日期',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建�?,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_po_code` (`po_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单主�?;

-- 采购单明细表
CREATE TABLE IF NOT EXISTS `purchase_order_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `purchase_order_id` bigint(20) NOT NULL COMMENT '采购单ID',
  `spare_part_id` bigint(20) NOT NULL COMMENT '备件ID',
  `quantity` int(11) NOT NULL COMMENT '采购数量',
  `unit_price` decimal(10,2) DEFAULT NULL COMMENT '单价',
  `received_quantity` int(11) NOT NULL DEFAULT '0' COMMENT '已收货数�?,
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购单明细表';

-- 入库单主�?
CREATE TABLE IF NOT EXISTS `stock_in_receipt` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `receipt_code` varchar(50) NOT NULL COMMENT '入库单号',
  `purchase_order_id` bigint(20) DEFAULT NULL COMMENT '关联采购单ID',
  `receipt_date` datetime NOT NULL COMMENT '入库时间',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状�?,
  `handler_id` bigint(20) DEFAULT NULL COMMENT '处理�?,
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_receipt_code` (`receipt_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单主�?;

-- 入库单明细表
CREATE TABLE IF NOT EXISTS `stock_in_item` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `stock_in_receipt_id` bigint(20) NOT NULL COMMENT '入库单ID',
  `purchase_order_item_id` bigint(20) DEFAULT NULL COMMENT '关联采购明细ID',
  `spare_part_id` bigint(20) NOT NULL COMMENT '备件ID',
  `expected_quantity` int(11) NOT NULL COMMENT '预计入库数量',
  `actual_quantity` int(11) NOT NULL COMMENT '实际入库数量',
  `shelved_quantity` int(11) NOT NULL DEFAULT '0' COMMENT '已上架数�?,
  `location_id` bigint(20) DEFAULT NULL COMMENT '默认货位',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单明细表';

-- 备件总库存表
CREATE TABLE IF NOT EXISTS `spare_part_stock` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `spare_part_id` bigint(20) NOT NULL COMMENT '备件ID',
  `quantity` int(11) NOT NULL DEFAULT '0' COMMENT '总数�?,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_spare_part_id` (`spare_part_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件总库存表';

-- 备件货位库存表（台账�?
CREATE TABLE IF NOT EXISTS `spare_part_location_stock` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `location_id` bigint(20) NOT NULL COMMENT '货位ID',
  `spare_part_id` bigint(20) NOT NULL COMMENT '备件ID',
  `quantity` int(11) NOT NULL DEFAULT '0' COMMENT '数量',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_location_spare` (`location_id`, `spare_part_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货位库存台账�?;
USE spare_db;

-- 仓储管理目录
INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `type`, `icon`, `sort`) VALUES
(21, 0, '仓储管理', NULL, 1, 'el-icon-box', 5);

-- 入库管理
INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `type`, `icon`, `sort`) VALUES
(22, 21, '入库管理', '/home/warehouse/stock-in', 2, 'el-icon-goods', 1);

-- 货位上架
INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `type`, `icon`, `sort`) VALUES
(23, 21, '货位上架', '/home/warehouse/shelving', 2, 'el-icon-receiving', 2);

-- 库存台账
INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `type`, `icon`, `sort`) VALUES
(24, 21, '库存台账', '/home/warehouse/ledger', 2, 'el-icon-data-line', 3);

-- �?admin 角色(id=1) 关联以上三个新菜�?
INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 21), (1, 22), (1, 23), (1, 24);
SET NAMES utf8mb4;
USE spare_db;

-- 1. AI设备特征记录�?
CREATE TABLE IF NOT EXISTS `ai_device_feature` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID，关�?equipment.id',
  `stat_month` varchar(20) NOT NULL COMMENT '统计月份，格�?yyyy-MM',
  `run_hours` decimal(10,2) DEFAULT '0.00' COMMENT '月运行时长（小时�?,
  `fault_count` int(11) DEFAULT '0' COMMENT '当月故障次数',
  `work_order_count` int(11) DEFAULT '0' COMMENT '当月工单�?,
  `part_replace_qty` int(11) DEFAULT '0' COMMENT '当月换件总数�?,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_month` (`device_id`,`stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI设备特征记录�?;

-- 2. AI需求预测结果表
CREATE TABLE IF NOT EXISTS `ai_forecast_result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `part_code` varchar(50) NOT NULL COMMENT '备件编码，关�?spare_part.code',
  `forecast_month` varchar(20) NOT NULL COMMENT '预测目标月份，格�?yyyy-MM',
  `predict_qty` decimal(10,2) DEFAULT '0.00' COMMENT '预测消耗量（件�?,
  `lower_bound` decimal(10,2) DEFAULT '0.00' COMMENT '90%置信区间下界',
  `upper_bound` decimal(10,2) DEFAULT '0.00' COMMENT '90%置信区间上界',
  `algo_type` varchar(50) DEFAULT NULL COMMENT '预测算法类型：RF/SBA/FALLBACK',
  `mase` decimal(10,4) DEFAULT NULL COMMENT 'MASE 评估指标',
  `model_version` varchar(50) DEFAULT NULL COMMENT '模型版本�?,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '预测计算时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_part_month` (`part_code`,`forecast_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI需求预测结果表';
