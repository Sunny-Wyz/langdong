-- 修复两阶段重算 model_version 过短导致的 #22001 Data truncation
-- 根因：StockThresholdService 写入 "two-stage-python-yyyy-MM"（24 字符），
--       ai_forecast_result.model_version / ai_model_registry.model_version 原为 varchar(20)
-- 兼容 MySQL 5.7+
--
-- 建议一次执行本脚本，同时覆盖预测结果表与模型注册表。

USE spare_db;

ALTER TABLE `ai_forecast_result`
    MODIFY COLUMN `model_version` varchar(64) NOT NULL DEFAULT 'v1.0' COMMENT '模型版本号';

ALTER TABLE `ai_model_registry`
    MODIFY COLUMN `model_name`    varchar(64) NOT NULL COMMENT '模型名称',
    MODIFY COLUMN `model_version` varchar(64) NOT NULL COMMENT '模型版本号';
