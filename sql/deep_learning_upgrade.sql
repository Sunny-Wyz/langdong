-- 深度学习升级迁移脚本（Phase 3）
-- 新增：周粒度预测结果表、模型注册表
-- MySQL 5.7 兼容
USE spare_db;

-- ================================================================
-- 1. 周粒度 TFT/DeepAR 预测结果表
-- ================================================================
CREATE TABLE IF NOT EXISTS `ai_weekly_forecast` (
    `id`                bigint(19)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `part_code`         varchar(20)    NOT NULL COMMENT '备件编码',
    `week_start`        date           NOT NULL COMMENT '预测目标周起始日（周一）',
    `predict_qty`       decimal(10,2)  NOT NULL DEFAULT 0.00 COMMENT '预测中位数（p50）出库量',
    `p10`               decimal(10,2)  DEFAULT NULL COMMENT '10%%分位数',
    `p25`               decimal(10,2)  DEFAULT NULL COMMENT '25%%分位数',
    `p75`               decimal(10,2)  DEFAULT NULL COMMENT '75%%分位数',
    `p90`               decimal(10,2)  DEFAULT NULL COMMENT '90%%分位数',
    `dist_mu`           decimal(10,4)  DEFAULT NULL COMMENT '分布均值（Normal μ）',
    `dist_sigma`        decimal(10,4)  DEFAULT NULL COMMENT '分布标准差（Normal σ）',
    `algo_type`         varchar(20)    NOT NULL DEFAULT 'TFT' COMMENT '算法: TFT/DeepAR',
    `model_version`     varchar(20)    NOT NULL DEFAULT '2.0.0' COMMENT '模型版本号',
    `adi`               decimal(8,4)   DEFAULT NULL COMMENT 'ADI（需求间隔指数）',
    `cv2`               decimal(8,4)   DEFAULT NULL COMMENT 'CV²（需求变异系数）',
    `create_time`       datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预测生成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_part_week`    (`part_code`, `week_start`),
    KEY `idx_week_start`         (`week_start`),
    KEY `idx_part_code`          (`part_code`),
    KEY `idx_algo_type`          (`algo_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 周粒度深度学习需求预测结果';

-- ================================================================
-- 2. 模型注册表（追踪各版本模型元信息）
-- ================================================================
CREATE TABLE IF NOT EXISTS `ai_model_registry` (
    `id`            bigint(19)     NOT NULL AUTO_INCREMENT COMMENT '主键',
    `model_name`    varchar(50)    NOT NULL COMMENT '模型名称（demand-forecaster / rul-predictor）',
    `model_version` varchar(20)    NOT NULL COMMENT '版本号',
    `algo_type`     varchar(20)    NOT NULL COMMENT '算法类型（TFT / DeepAR / InceptionTime）',
    `mlflow_run_id` varchar(64)    DEFAULT NULL COMMENT 'MLflow Run ID',
    `artifact_path` varchar(255)   DEFAULT NULL COMMENT '模型文件存储路径',
    `mae`           decimal(10,4)  DEFAULT NULL COMMENT 'MAE 评估指标',
    `rmse`          decimal(10,4)  DEFAULT NULL COMMENT 'RMSE 评估指标',
    `mase`          decimal(10,4)  DEFAULT NULL COMMENT 'MASE 评估指标',
    `crps`          decimal(10,4)  DEFAULT NULL COMMENT 'CRPS 评估指标（概率预测）',
    `train_parts`   int(6)         DEFAULT NULL COMMENT '训练备件数量',
    `train_weeks`   int(6)         DEFAULT NULL COMMENT '训练周数',
    `status`        varchar(20)    NOT NULL DEFAULT 'STAGED' COMMENT '状态: STAGED/PRODUCTION/ARCHIVED',
    `deploy_time`   datetime       DEFAULT NULL COMMENT '上线时间',
    `create_time`   datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (`id`),
    KEY `idx_model_name`    (`model_name`),
    KEY `idx_status`        (`status`),
    KEY `idx_create_time`   (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型注册表';

-- ================================================================
-- 3. 菜单：周粒度预测结果页（在 AI 智能分析目录下追加）
-- ================================================================
INSERT IGNORE INTO `menu` (`parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`)
SELECT id, '周粒度预测', '/ai/weekly-forecast', 'ai/WeeklyForecastResult',
       'ai:weekly:list', 2, 'el-icon-date', 3
FROM `menu` WHERE `path` = '/ai' ORDER BY id DESC LIMIT 1;

-- 按钮：触发周预测
INSERT IGNORE INTO `menu` (`parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`)
SELECT id, '触发预测(按钮)', NULL, NULL, 'ai:weekly:trigger', 3, NULL, 1
FROM `menu` WHERE `path` = '/ai/weekly-forecast' ORDER BY id DESC LIMIT 1;

-- 授权给管理员角色（role_id=1）
INSERT IGNORE INTO `role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `menu`
WHERE `path` IN ('/ai/weekly-forecast')
   OR `permission` IN ('ai:weekly:list', 'ai:weekly:trigger');
