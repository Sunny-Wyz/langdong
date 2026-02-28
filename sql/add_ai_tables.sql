SET NAMES utf8mb4;
USE spare_db;

-- 1. AI设备特征记录表
CREATE TABLE IF NOT EXISTS `ai_device_feature` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` bigint(20) NOT NULL COMMENT '设备ID，关联 equipment.id',
  `stat_month` varchar(20) NOT NULL COMMENT '统计月份，格式 yyyy-MM',
  `run_hours` decimal(10,2) DEFAULT '0.00' COMMENT '月运行时长（小时）',
  `fault_count` int(11) DEFAULT '0' COMMENT '当月故障次数',
  `work_order_count` int(11) DEFAULT '0' COMMENT '当月工单数',
  `part_replace_qty` int(11) DEFAULT '0' COMMENT '当月换件总数量',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_month` (`device_id`,`stat_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI设备特征记录表';

-- 2. AI需求预测结果表
CREATE TABLE IF NOT EXISTS `ai_forecast_result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `part_code` varchar(50) NOT NULL COMMENT '备件编码，关联 spare_part.code',
  `forecast_month` varchar(20) NOT NULL COMMENT '预测目标月份，格式 yyyy-MM',
  `predict_qty` decimal(10,2) DEFAULT '0.00' COMMENT '预测消耗量（件）',
  `lower_bound` decimal(10,2) DEFAULT '0.00' COMMENT '90%置信区间下界',
  `upper_bound` decimal(10,2) DEFAULT '0.00' COMMENT '90%置信区间上界',
  `algo_type` varchar(50) DEFAULT NULL COMMENT '预测算法类型：RF/SBA/FALLBACK',
  `mase` decimal(10,4) DEFAULT NULL COMMENT 'MASE 评估指标',
  `model_version` varchar(50) DEFAULT NULL COMMENT '模型版本号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '预测计算时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_part_month` (`part_code`,`forecast_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI需求预测结果表';
