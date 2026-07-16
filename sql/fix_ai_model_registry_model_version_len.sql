-- 修复 ai_model_registry.model_version / model_name 过短导致两阶段重算注册失败
-- 根因：StockThresholdService 写入
--   model_name    = 'demand-forecaster-two-stage'  (27 字符)
--   model_version = 'two-stage-python-yyyy-MM'     (24 字符)
-- 旧列 model_version varchar(20) 触发 MySQL #22001 Data truncation
-- 兼容 MySQL 5.7+

USE spare_db;

ALTER TABLE `ai_model_registry`
    MODIFY COLUMN `model_name`    varchar(64) NOT NULL COMMENT '模型名称',
    MODIFY COLUMN `model_version` varchar(64) NOT NULL COMMENT '模型版本号';
