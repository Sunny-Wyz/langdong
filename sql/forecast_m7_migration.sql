-- M7 数据库迁移脚本：为 ai_forecast_result 新增六标准字段持久化支持
-- MySQL 5.7 兼容，支持重复执行

USE spare_db;

-- 1. 增加发生概率字段：occurrence_prob
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

-- 2. 增加正需求量预测均值字段：positive_qty
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

-- 3. 增加提前期分位数：lead_time_quantile
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
