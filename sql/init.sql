SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS spare_db DEFAULT CHARACTER SET utf8mb4;
USE spare_db;

CREATE TABLE IF NOT EXISTS `user` (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(50)  NOT NULL COMMENT '用户名',
    name       VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    password   VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    status     TINYINT      DEFAULT 1 COMMENT '状态(1正常 0停用)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
    `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色档案表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限字典表';

CREATE TABLE IF NOT EXISTS `user_role` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS `role_menu` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS `spare_part_category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code` VARCHAR(4) NOT NULL COMMENT '分类编码(大类占1位，小类加3位)',
    `name` VARCHAR(100) NOT NULL COMMENT '分类名称',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父类ID(大类为空)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件分类字典表';

CREATE TABLE IF NOT EXISTS `spare_part` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(8)     NOT NULL COMMENT '备件统一8位编码',
    name       VARCHAR(100)   NOT NULL COMMENT '备件名称',
    model      VARCHAR(100)   DEFAULT NULL COMMENT '型号规格',
    quantity   INT            NOT NULL DEFAULT 0 COMMENT '库存数量',
    unit       VARCHAR(20)    DEFAULT '个' COMMENT '单位',
    price      DECIMAL(10, 2) DEFAULT NULL COMMENT '单价',
    category_id BIGINT        NOT NULL COMMENT '所属分类ID',
    supplier   VARCHAR(100)   DEFAULT NULL COMMENT '供应商',
    remark     TEXT           DEFAULT NULL COMMENT '备注',
    location_id BIGINT        DEFAULT NULL COMMENT '所属货位ID',
    supplier_id BIGINT        DEFAULT NULL COMMENT '供应商ID',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY `uk_spare_part_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件档案表';

CREATE TABLE IF NOT EXISTS `location` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(50)    NOT NULL COMMENT '货位编码',
    name       VARCHAR(100)   NOT NULL COMMENT '货位名称',
    zone       VARCHAR(50)    NOT NULL COMMENT '所属专区(1-12)',
    capacity   VARCHAR(50)    DEFAULT NULL COMMENT '容量',
    remark     TEXT           DEFAULT NULL COMMENT '备注',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='货位档案表';

CREATE TABLE IF NOT EXISTS `equipment` (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(50)    NOT NULL COMMENT '设备编码',
    name       VARCHAR(100)   NOT NULL COMMENT '设备名称',
    model      VARCHAR(100)   DEFAULT NULL COMMENT '规格型号',
    department VARCHAR(100)   DEFAULT NULL COMMENT '所属部门/产线',
    status     VARCHAR(50)    DEFAULT '正常' COMMENT '设备状态',
    remark     TEXT           DEFAULT NULL COMMENT '备注',
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备档案表';

CREATE TABLE IF NOT EXISTS `equipment_spare_part` (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    equipment_id   BIGINT   NOT NULL COMMENT '设备ID',
    spare_part_id  BIGINT   NOT NULL COMMENT '备件ID',
    quantity       INT      DEFAULT 1 COMMENT '配套数量',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_eq_sp (equipment_id, spare_part_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备备件配套关联表';

-- 初始账号：admin / 123456
INSERT INTO `user` (id, username, name, password, status) VALUES (
    1,
    'admin',
    '系统管理员',
    '$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa',
    1
);

INSERT INTO `role` (`id`, `code`, `name`, `remark`) VALUES (1, 'ADMIN', '超级管理员', '系统最高权限');
INSERT INTO `user_role` (`user_id`, `role_id`) VALUES (1, 1);

INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`) VALUES
(1, NULL, '系统管理', '/sys', 'Layout', NULL, 1, 'el-icon-setting', 99),
(2, 1, '用户管理', '/sys/users', 'sys/UserManage', 'sys:user:list', 2, 'el-icon-user', 1),
(3, 1, '角色与菜单分配', '/sys/roles', 'sys/RoleManage', 'sys:role:list', 2, 'el-icon-key', 2),
(4, NULL, '基础数据管理', '/home', 'Layout', NULL, 1, 'el-icon-suitcase-1', 1),
(5, 4, '备件档案管理', '/home/spare-parts', 'SparePartList', 'base:spare:list', 2, 'el-icon-s-order', 1),
(6, 5, '备件新增(按钮)', NULL, NULL, 'base:spare:add', 3, NULL, 1),
(7, 4, '货位档案管理', '/home/location-profiles', 'LocationProfile', 'base:location:list', 2, 'el-icon-location-information', 2),
(8, 4, '设备档案管理', '/home/equipment-profiles', 'EquipmentProfile', 'base:equipment:list', 2, 'el-icon-odometer', 3),
(9, 4, '供应商档案管理', '/home/supplier-profiles', 'SupplierProfile', 'base:supplier:list', 2, 'el-icon-truck', 4),
(10, 4, '品类字典表', '/home/supply-categories', 'SupplyCategory', 'base:category:list', 2, 'el-icon-collection-tag', 5),
(11, NULL, '备件智能分类模块', '/smart', 'Layout', NULL, 1, 'el-icon-collection', 2),
(12, NULL, '仓储管理模块', '/warehouse', 'Layout', NULL, 1, 'el-icon-box', 3),
(13, NULL, '领用管理模块', '/requisition', 'Layout', NULL, 1, 'el-icon-sell', 4),
(14, NULL, '维修工单管理模块', '/maintenance', 'Layout', NULL, 1, 'el-icon-s-tools', 5),
(15, NULL, '采购管理模块', '/procurement', 'Layout', NULL, 1, 'el-icon-shopping-cart-full', 6),
(16, NULL, 'AI智能分析模块', '/ai', 'Layout', NULL, 1, 'el-icon-cpu', 7),
(17, NULL, '报表与看板模块', '/reports', 'Layout', NULL, 1, 'el-icon-data-board', 8);

INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6),
(1, 7), (1, 8), (1, 9), (1, 10), (1, 11), (1, 12), (1, 13), (1, 14), (1, 15), (1, 16), (1, 17);
