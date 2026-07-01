-- ================================================================================
-- M8 需求预测与库存决策模块完整数据库 Schema 归档脚本
-- 兼容 MySQL 5.7.24
-- ================================================================================

USE spare_db;

-- --------------------------------------------------------------------------------
-- 1. 创建 AI 需求预测结果表: ai_forecast_result
-- --------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_forecast_result` (
    `id`                 bigint(19)     NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    `part_code`          varchar(20)    NOT NULL COMMENT '备件编码，关联 spare_part.code',
    `forecast_month`     varchar(7)     NOT NULL COMMENT '预测目标月份（yyyy-MM）',
    `predict_qty`        decimal(8,2)   NOT NULL DEFAULT 0.00 COMMENT '预测需求均值（件）',
    `lower_bound`        decimal(8,2)   NOT NULL DEFAULT 0.00 COMMENT '90%置信区间下界',
    `upper_bound`        decimal(8,2)   NOT NULL DEFAULT 0.00 COMMENT '90%置信区间上界',
    `occurrence_prob`    decimal(5,4)   DEFAULT NULL COMMENT '需求发生概率',
    `positive_qty`       decimal(8,2)   DEFAULT NULL COMMENT '条件正需求量预测均值',
    `lead_time_quantile` decimal(8,2)   DEFAULT NULL COMMENT '采购提前期分位数（安全水位上限，未向上取整前）',
    `algo_type`          varchar(20)    NOT NULL DEFAULT 'RF' COMMENT '预测算法类型: RF/SBA/FALLBACK/TWO_STAGE',
    `mase`               decimal(6,4)   DEFAULT NULL COMMENT 'MASE评估指标',
    `model_version`      varchar(20)    NOT NULL DEFAULT 'v1.0' COMMENT '模型版本号',
    `create_time`        datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预测计算时间',
    PRIMARY KEY (`id`),
    KEY `idx_part_code`      (`part_code`),
    KEY `idx_forecast_month` (`forecast_month`),
    KEY `idx_part_month`     (`part_code`, `forecast_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI需求预测结果表';

-- [升级兼容] 如果 ai_forecast_result 表已经存在，但没有新字段，通过以下过程进行补建：

-- 1a. 补建 occurrence_prob 列
SET @exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'ai_forecast_result'
      AND COLUMN_NAME  = 'occurrence_prob'
);
SET @sql = IF(@exists = 0,
    'ALTER TABLE ai_forecast_result ADD COLUMN occurrence_prob decimal(5,4) DEFAULT NULL COMMENT \'需求发生概率\'',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1b. 补建 positive_qty 列
SET @exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'ai_forecast_result'
      AND COLUMN_NAME  = 'positive_qty'
);
SET @sql = IF(@exists = 0,
    'ALTER TABLE ai_forecast_result ADD COLUMN positive_qty decimal(8,2) DEFAULT NULL COMMENT \'正需求量预测均值\'',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 1c. 补建 lead_time_quantile 列
SET @exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'ai_forecast_result'
      AND COLUMN_NAME  = 'lead_time_quantile'
);
SET @sql = IF(@exists = 0,
    'ALTER TABLE ai_forecast_result ADD COLUMN lead_time_quantile decimal(8,2) DEFAULT NULL COMMENT \'提前期分位数\'',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- --------------------------------------------------------------------------------
-- 2. 创建备件 ABC/XYZ 分类与阈值计算结果表: biz_part_classify
-- --------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_part_classify` (
    `id`              bigint(19)     NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    `part_code`       varchar(20)    NOT NULL COMMENT '备件编码',
    `classify_month`  varchar(7)     NOT NULL COMMENT '分类所属月份，格式yyyy-MM',
    `abc_class`       varchar(2)     NOT NULL COMMENT 'ABC分类结果(A/B/C)',
    `xyz_class`       varchar(2)     NOT NULL COMMENT 'XYZ分类结果(X/Y/Z)',
    `composite_score` decimal(5, 2)  NOT NULL DEFAULT 0.00 COMMENT 'ABC综合加权得分(0~100)',
    `annual_cost`     decimal(10, 2) NOT NULL DEFAULT 0.00 COMMENT '年消耗金额（元）',
    `adi`             decimal(8, 4)  DEFAULT NULL COMMENT '平均需求间隔ADI',
    `cv2`             decimal(8, 4)  NOT NULL DEFAULT 0.00 COMMENT '需求变异系数CV²',
    `safety_stock`    int            NOT NULL DEFAULT 0 COMMENT '安全库存SS（件）',
    `reorder_point`   int            NOT NULL DEFAULT 0 COMMENT '补货触发点ROP（件）',
    `service_level`   decimal(5, 2)  DEFAULT NULL COMMENT '目标服务水平（%）',
    `strategy_code`   varchar(10)    DEFAULT NULL COMMENT 'ABC×XYZ联合策略编码，如AX/BZ',
    `create_time`     datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_part_code`      (`part_code`),
    KEY `idx_classify_month` (`classify_month`),
    KEY `idx_abc_xyz`        (`abc_class`, `xyz_class`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备件分类与库存水位表';


-- --------------------------------------------------------------------------------
-- 3. 创建智能补货建议表: biz_reorder_suggest
-- --------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `biz_reorder_suggest` (
    `id`            bigint(19)     NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    `part_code`     varchar(20)    NOT NULL COMMENT '备件编码，关联 spare_part.code',
    `suggest_month` varchar(7)     NOT NULL COMMENT '建议提出所属属期（yyyy-MM）',
    `current_stock` int            NOT NULL COMMENT '触发推荐时的系统可用库存',
    `reorder_point` int            NOT NULL COMMENT '计算得出的补货触发水位ROP',
    `suggest_qty`   int            NOT NULL COMMENT '推荐采购补货量',
    `forecast_qty`  decimal(8,2)   DEFAULT NULL COMMENT '预测消耗量均值',
    `lower_bound`   decimal(8,2)   DEFAULT NULL COMMENT '置信区间下界',
    `upper_bound`   decimal(8,2)   DEFAULT NULL COMMENT '置信区间上界',
    `urgency`       varchar(10)    NOT NULL DEFAULT '正常' COMMENT '紧急度判定: 紧急/正常',
    `status`        varchar(10)    NOT NULL DEFAULT '待处理' COMMENT '建议状态: 待处理/已确认/已驳回',
    `created_at`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建议提出时间',
    `updated_at`    datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_part_code`      (`part_code`),
    KEY `idx_suggest_month`  (`suggest_month`),
    KEY `idx_status`         (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能补货建议表';


-- --------------------------------------------------------------------------------
-- 4. 创建 AI 模型版本注册表: ai_model_registry
-- --------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_model_registry` (
    `id`            bigint(19)     NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    `model_name`    varchar(50)    NOT NULL COMMENT '模型名称 (例如 demand-forecaster)',
    `model_version` varchar(30)    NOT NULL COMMENT '模型版本号 (例如 two-stage-2026-07)',
    `algo_type`     varchar(20)    NOT NULL DEFAULT 'TWO_STAGE' COMMENT '算法类型',
    `mlflow_run_id` varchar(50)    DEFAULT NULL COMMENT '机器学习运行实验ID，关联用',
    `artifact_path` varchar(255)   NOT NULL COMMENT '模型训练快照存放路径',
    `mae`           decimal(8,4)   DEFAULT NULL COMMENT '平均绝对误差',
    `rmse`          decimal(8,4)   DEFAULT NULL COMMENT '均方根误差',
    `mase`          decimal(8,4)   DEFAULT NULL COMMENT '平均绝对比例误差',
    `crps`          decimal(8,4)   DEFAULT NULL COMMENT '连续排定概率评分',
    `train_parts`   int            NOT NULL DEFAULT 0 COMMENT '参与训练的备件种类数量',
    `train_weeks`   int            NOT NULL DEFAULT 0 COMMENT '训练集的历史覆盖总时长（评估回看周/月）',
    `status`        varchar(20)    NOT NULL DEFAULT 'PRODUCTION' COMMENT '状态: PRODUCTION/ARCHIVED/DRAFT',
    `deploy_time`   datetime       DEFAULT NULL COMMENT '上线/部署时间',
    `create_time`   datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '模型创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_model_name`    (`model_name`),
    KEY `idx_model_version` (`model_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型注册表';


-- --------------------------------------------------------------------------------
-- 5. 创建 AI 设备特征月度汇总表: ai_device_feature
-- --------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_device_feature` (
    `id`               bigint(19)    NOT NULL AUTO_INCREMENT COMMENT '主键，自增',
    `device_id`        bigint(19)    NOT NULL COMMENT '设备ID，关联 equipment.id',
    `stat_month`       varchar(7)    NOT NULL COMMENT '统计月份（yyyy-MM）',
    `run_hours`        decimal(7,2)  NOT NULL DEFAULT 0.00 COMMENT '月度总运行时长（小时）',
    `work_order_count` int           NOT NULL DEFAULT 0 COMMENT '月度产生的总维修工单数',
    `create_time`      datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_month` (`device_id`, `stat_month`),
    KEY `idx_stat_month` (`stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI关联设备月度特征表';
