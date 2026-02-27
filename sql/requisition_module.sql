-- 领用管理模块 - 数据库迁移脚本
USE spare_db;

-- 领用申请主表
CREATE TABLE IF NOT EXISTS `biz_requisition` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `req_no`         VARCHAR(30)  NOT NULL COMMENT '领用单号，全局唯一',
    `applicant_id`   BIGINT       NOT NULL COMMENT '申请人ID（设备工程师）',
    `work_order_no`  VARCHAR(50)  DEFAULT NULL COMMENT '关联维修工单号',
    `device_id`      BIGINT       DEFAULT NULL COMMENT '关联设备ID',
    `req_status`     VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED/OUTBOUND/INSTALLED',
    `is_urgent`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否紧急（走快速审批通道）',
    `approve_id`     BIGINT       DEFAULT NULL COMMENT '审批人ID',
    `approve_time`   DATETIME     DEFAULT NULL COMMENT '审批时间',
    `approve_remark` VARCHAR(200) DEFAULT NULL COMMENT '审批意见',
    `apply_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    `remark`         VARCHAR(500) DEFAULT NULL COMMENT '申请备注',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_req_no` (`req_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领用申请主表';

-- 领用申请明细表
CREATE TABLE IF NOT EXISTS `biz_requisition_item` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '明细ID，主键',
    `req_id`       BIGINT       NOT NULL COMMENT '领用申请ID',
    `spare_part_id` BIGINT      NOT NULL COMMENT '备件ID',
    `apply_qty`    INT          NOT NULL COMMENT '申请数量',
    `out_qty`      INT          DEFAULT NULL COMMENT '实际出库数量',
    `install_loc`  VARCHAR(100) DEFAULT NULL COMMENT '安装位置',
    `install_time` DATETIME     DEFAULT NULL COMMENT '安装时间',
    `installer_id` BIGINT       DEFAULT NULL COMMENT '安装人ID',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_req_id` (`req_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领用申请明细表';

-- 领用管理子模块菜单（parent_id = 13，即领用管理模块目录）
INSERT IGNORE INTO `menu` (`id`, `parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`) VALUES
(18, 13, '发起领用申请', '/home/requisition-apply',    'requisition/RequisitionApply',    'req:apply:add',      2, 'el-icon-plus',               1),
(19, 13, '审批领用申请', '/home/requisition-approval',  'requisition/RequisitionApproval', 'req:approve:list',   2, 'el-icon-s-check',            2),
(20, 13, '出库确认',     '/home/requisition-outbound',  'requisition/RequisitionOutbound', 'req:outbound:confirm', 2, 'el-icon-sold-out',         3),
(21, 13, '安装登记',     '/home/requisition-install',   'requisition/RequisitionInstall',  'req:install:edit',   2, 'el-icon-s-opportunity',      4),
(22, 13, '查询领用记录', '/home/requisition-query',     'requisition/RequisitionQuery',    'req:record:list',    2, 'el-icon-search',             5);

-- 将新菜单项授权给超级管理员角色
INSERT IGNORE INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 18), (1, 19), (1, 20), (1, 21), (1, 22);
