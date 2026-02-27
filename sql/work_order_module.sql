USE spare_db;

-- 维修工单主表
CREATE TABLE IF NOT EXISTS `biz_work_order` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `work_order_no`  VARCHAR(30)   NOT NULL COMMENT '工单编号，全局唯一',
    `device_id`      BIGINT        NOT NULL COMMENT '故障设备ID',
    `reporter_id`    BIGINT        NOT NULL COMMENT '报修人ID',
    `fault_desc`     VARCHAR(500)  NOT NULL COMMENT '故障描述',
    `fault_level`    VARCHAR(10)   NOT NULL COMMENT '紧急程度：紧急/一般/计划',
    `order_status`   VARCHAR(20)   NOT NULL DEFAULT '报修' COMMENT '状态：报修/已派工/维修中/完工',
    `assignee_id`    BIGINT        DEFAULT NULL COMMENT '派工维修人员ID',
    `plan_finish`    DATETIME      DEFAULT NULL COMMENT '计划完成时间',
    `actual_finish`  DATETIME      DEFAULT NULL COMMENT '实际完成时间',
    `fault_cause`    VARCHAR(500)  DEFAULT NULL COMMENT '故障根因分析',
    `repair_method`  VARCHAR(500)  DEFAULT NULL COMMENT '维修方案描述',
    `mttr_minutes`   INT           DEFAULT NULL COMMENT '本次维修时长（分钟）',
    `part_cost`      DECIMAL(10,2) DEFAULT NULL COMMENT '备件费用（系统自动汇总）',
    `labor_cost`     DECIMAL(10,2) DEFAULT NULL COMMENT '人工费用',
    `outsource_cost` DECIMAL(10,2) DEFAULT NULL COMMENT '外协费用',
    `report_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报修时间',
    `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_work_order_no` (`work_order_no`),
    KEY `idx_device_id` (`device_id`),
    KEY `idx_order_status` (`order_status`),
    KEY `idx_reporter_id` (`reporter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修工单主表';

-- 子菜单（parent_id=14，init.sql 中已存在维修工单管理模块目录节点）
INSERT IGNORE INTO `menu` (`id`, `parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`) VALUES
(23, 14, '故障报修',     '/home/work-order-report',   'workorder/WorkOrderReport',   'wo:report:add',    2, 'el-icon-warning',      1),
(24, 14, '在线派工',     '/home/work-order-assign',   'workorder/WorkOrderAssign',   'wo:assign:edit',   2, 'el-icon-user',         2),
(25, 14, '维修过程记录', '/home/work-order-process',  'workorder/WorkOrderProcess',  'wo:process:edit',  2, 'el-icon-edit',         3),
(26, 14, '完工确认',     '/home/work-order-complete', 'workorder/WorkOrderComplete', 'wo:complete:edit', 2, 'el-icon-circle-check', 4),
(27, 14, '工单查询统计', '/home/work-order-query',    'workorder/WorkOrderQuery',    'wo:query:list',    2, 'el-icon-search',       5);

-- ADMIN 角色（role_id=1）授权全部菜单
INSERT IGNORE INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 23), (1, 24), (1, 25), (1, 26), (1, 27);
