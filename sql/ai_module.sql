-- AI 智能分析模块（M7）数据库迁移脚本
-- MySQL 5.7 兼容
USE spare_db;

-- ================================================================
-- 1. 新建需求预测结果表 ai_forecast_result
-- ================================================================
CREATE TABLE IF NOT EXISTS `ai_forecast_result` (
    `id`            bigint(19)     NOT NULL AUTO_INCREMENT COMMENT '预测记录ID，主键',
    `part_code`     varchar(20)    NOT NULL COMMENT '备件编码，关联 spare_part.code',
    `forecast_month` varchar(7)   NOT NULL COMMENT '预测目标月份（yyyy-MM）',
    `predict_qty`   decimal(8,2)   NOT NULL DEFAULT 0.00 COMMENT '预测消耗量（件）',
    `lower_bound`   decimal(8,2)   NOT NULL DEFAULT 0.00 COMMENT '90%%置信区间下界',
    `upper_bound`   decimal(8,2)   NOT NULL DEFAULT 0.00 COMMENT '90%%置信区间上界',
    `algo_type`     varchar(20)    NOT NULL DEFAULT 'RF' COMMENT '预测算法: RF/SBA/FALLBACK',
    `mase`          decimal(6,4)   DEFAULT NULL COMMENT 'MASE评估指标',
    `model_version` varchar(20)    NOT NULL DEFAULT 'v1.0' COMMENT '模型版本号',
    `create_time`   datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预测计算时间',
    PRIMARY KEY (`id`),
    KEY `idx_part_code`      (`part_code`),
    KEY `idx_forecast_month` (`forecast_month`),
    KEY `idx_part_month`     (`part_code`, `forecast_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI需求预测结果表';

-- ================================================================
-- 2. 新建设备特征记录表 ai_device_feature
-- ================================================================
CREATE TABLE IF NOT EXISTS `ai_device_feature` (
    `id`                bigint(19)    NOT NULL AUTO_INCREMENT COMMENT '特征记录ID，主键',
    `device_id`         bigint(19)    NOT NULL COMMENT '设备ID，关联 equipment.id',
    `stat_month`        varchar(7)    NOT NULL COMMENT '统计月份（yyyy-MM）',
    `run_hours`         decimal(8,1)  NOT NULL DEFAULT 0.0 COMMENT '月运行时长（小时）',
    `fault_count`       int(5)        NOT NULL DEFAULT 0 COMMENT '当月故障次数',
    `work_order_count`  int(5)        NOT NULL DEFAULT 0 COMMENT '当月工单数',
    `part_replace_qty`  int(5)        NOT NULL DEFAULT 0 COMMENT '当月换件总数量',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_month` (`device_id`, `stat_month`),
    KEY `idx_stat_month`         (`stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI设备特征记录表';

-- ================================================================
-- 3. 新增 AI 模块菜单项（父级目录）
--    使用 auto-increment，通过路径查询后再关联 role_menu
-- ================================================================

-- 3.1 插入 AI 智能分析 一级目录（type=1 为目录）
INSERT INTO `menu` (`parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`)
VALUES (0, 'AI智能分析', '/ai', NULL, NULL, 1, 'el-icon-cpu', 9);

-- 3.2 插入子菜单：需求预测结果（type=2 为菜单页面）
INSERT INTO `menu` (`parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`)
SELECT id, '需求预测结果', '/ai/forecast-result', 'ai/AiForecastResult', 'ai:forecast:list', 2, 'el-icon-data-analysis', 1
FROM `menu` WHERE `path` = '/ai' ORDER BY id DESC LIMIT 1;

-- 3.3 插入按钮权限：手动触发（type=3 为按钮）
INSERT INTO `menu` (`parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`)
SELECT id, '手动触发预测(按钮)', NULL, NULL, 'ai:forecast:trigger', 3, NULL, 1
FROM `menu` WHERE `path` = '/ai/forecast-result' ORDER BY id DESC LIMIT 1;

-- 3.4 将以上菜单授权给超级管理员角色（role_id=1）
INSERT INTO `role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `menu` WHERE `path` = '/ai';

INSERT INTO `role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `menu` WHERE `path` = '/ai/forecast-result';

INSERT INTO `role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `menu` WHERE `permission` = 'ai:forecast:trigger';
