-- ================================================================
-- PHM（预测性维护）模块 - 数据库迁移脚本
-- 版本：v1.0.0
-- 日期：2026-03-23
-- 说明：创建PHM所需的4张新表、扩展2张现有表、添加菜单权限
-- ================================================================

USE spare_db;

-- ================================================================
-- 1. 新建表：ai_device_health（设备健康记录表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `ai_device_health` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` BIGINT NOT NULL COMMENT '设备ID（关联equipment.id）',
  `record_date` DATE NOT NULL COMMENT '记录日期',
  `health_score` DECIMAL(5,2) NOT NULL COMMENT '健康评分（0-100）',
  `risk_level` VARCHAR(20) NOT NULL COMMENT '风险等级（LOW/MEDIUM/HIGH/CRITICAL）',

  -- 评分维度明细
  `runtime_score` DECIMAL(5,2) COMMENT '运行时长评分',
  `fault_score` DECIMAL(5,2) COMMENT '故障频次评分',
  `workorder_score` DECIMAL(5,2) COMMENT '工单数量评分',
  `replacement_score` DECIMAL(5,2) COMMENT '换件频次评分',

  -- 预测结果
  `predicted_failure_days` INT COMMENT '预测剩余天数（NULL表示低风险）',
  `confidence_level` DECIMAL(5,2) COMMENT '预测置信度（0-1）',

  -- 元数据
  `algorithm_version` VARCHAR(50) COMMENT '算法版本号',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_date` (`device_id`, `record_date`),
  INDEX `idx_risk_level` (`risk_level`),
  INDEX `idx_record_date` (`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备健康评估记录';

-- ================================================================
-- 2. 新建表：ai_fault_prediction（故障预测结果表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `ai_fault_prediction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` BIGINT NOT NULL COMMENT '设备ID',
  `prediction_date` DATE NOT NULL COMMENT '预测生成日期',
  `target_month` VARCHAR(7) NOT NULL COMMENT '预测目标月份（YYYY-MM）',

  -- 预测值
  `predicted_fault_count` INT NOT NULL COMMENT '预测故障次数',
  `predicted_downtime_hours` DECIMAL(10,2) COMMENT '预测停机时长（小时）',
  `failure_probability` DECIMAL(5,4) NOT NULL COMMENT '故障概率（0-1）',

  -- 置信区间（90%）
  `fault_count_lower` INT COMMENT '故障次数下限',
  `fault_count_upper` INT COMMENT '故障次数上限',

  -- 预测依据
  `feature_importance` JSON COMMENT '特征重要性（{"runHours":0.35, "faultCount":0.28, ...}）',
  `model_type` VARCHAR(50) NOT NULL COMMENT '使用的模型类型（LOGISTIC_REGRESSION/RANDOM_FOREST/HYBRID）',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_target` (`device_id`, `target_month`),
  INDEX `idx_prediction_date` (`prediction_date`),
  INDEX `idx_failure_prob` (`failure_probability`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备故障预测结果';

-- ================================================================
-- 3. 新建表：biz_maintenance_suggestion（预防性维护建议表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `biz_maintenance_suggestion` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_id` BIGINT NOT NULL COMMENT '设备ID',
  `health_record_id` BIGINT NOT NULL COMMENT '关联健康记录ID',
  `suggestion_date` DATE NOT NULL COMMENT '建议生成日期',

  -- 维护建议
  `maintenance_type` VARCHAR(50) NOT NULL COMMENT '维护类型（PREVENTIVE/PREDICTIVE/EMERGENCY）',
  `priority_level` VARCHAR(20) NOT NULL COMMENT '优先级（HIGH/MEDIUM/LOW）',
  `suggested_start_date` DATE NOT NULL COMMENT '建议开始日期',
  `suggested_end_date` DATE NOT NULL COMMENT '建议完成日期',

  -- 关联备件需求
  `related_spare_parts` JSON COMMENT '关联配套备件列表（[{"partId", "partCode", "quantity", "reason"}, ...]）',
  `estimated_cost` DECIMAL(15,2) COMMENT '预估维护成本',

  -- 处理状态
  `status` VARCHAR(50) NOT NULL DEFAULT 'PENDING' COMMENT '状态（PENDING/ACCEPTED/SCHEDULED/COMPLETED/REJECTED）',
  `workorder_id` BIGINT COMMENT '关联工单ID（采纳后创建）',
  `requisition_id` BIGINT COMMENT '关联领用单ID（自动生成）',

  -- 决策信息
  `reason` TEXT NOT NULL COMMENT '建议原因（风险评估摘要）',
  `reject_reason` TEXT COMMENT '拒绝原因',
  `handled_by` BIGINT COMMENT '处理人ID',
  `handled_at` DATETIME COMMENT '处理时间',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  INDEX `idx_device_status` (`device_id`, `status`),
  INDEX `idx_suggestion_date` (`suggestion_date`),
  INDEX `idx_priority` (`priority_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预防性维护建议';

-- ================================================================
-- 4. 新建表：sys_device_health_config（设备健康阈值配置表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `sys_device_health_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `device_type` VARCHAR(100) COMMENT '设备类型（NULL表示全局默认）',
  `importance_level` VARCHAR(50) COMMENT '设备重要性（CRITICAL/IMPORTANT/NORMAL）',

  -- 健康评分阈值
  `critical_threshold` DECIMAL(5,2) NOT NULL DEFAULT 40 COMMENT '严重风险阈值（<此值）',
  `high_threshold` DECIMAL(5,2) NOT NULL DEFAULT 60 COMMENT '高风险阈值（<此值）',
  `medium_threshold` DECIMAL(5,2) NOT NULL DEFAULT 80 COMMENT '中风险阈值（<此值）',

  -- 评分权重
  `runtime_weight` DECIMAL(4,3) NOT NULL DEFAULT 0.25 COMMENT '运行时长权重',
  `fault_weight` DECIMAL(4,3) NOT NULL DEFAULT 0.35 COMMENT '故障频次权重',
  `workorder_weight` DECIMAL(4,3) NOT NULL DEFAULT 0.20 COMMENT '工单数量权重',
  `replacement_weight` DECIMAL(4,3) NOT NULL DEFAULT 0.20 COMMENT '换件频次权重',

  -- 预测参数
  `prediction_window_days` INT NOT NULL DEFAULT 90 COMMENT '预测窗口（天）',
  `min_history_months` INT NOT NULL DEFAULT 6 COMMENT '最少历史数据月数',

  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_type_importance` (`device_type`, `importance_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备健康阈值配置';

-- 插入默认配置（3个重要性级别）
INSERT IGNORE INTO `sys_device_health_config`
(`device_type`, `importance_level`, `critical_threshold`, `high_threshold`, `medium_threshold`)
VALUES
(NULL, 'CRITICAL', 30, 50, 70),
(NULL, 'IMPORTANT', 40, 60, 80),
(NULL, 'NORMAL', 50, 70, 85);

-- ================================================================
-- 5. 扩展现有表：ai_device_feature（增加MTBF/MTTR等指标）
-- ================================================================

-- 检查并添加字段（逐个添加，避免IF NOT EXISTS兼容性问题）
SET @dbname = 'spare_db';
SET @tablename = 'ai_device_feature';

-- 添加mtbf字段
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'mtbf') = 0,
    'ALTER TABLE `ai_device_feature` ADD COLUMN `mtbf` DECIMAL(10,2) COMMENT ''平均故障间隔时间（小时）''',
    'SELECT ''mtbf字段已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加mttr字段
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'mttr') = 0,
    'ALTER TABLE `ai_device_feature` ADD COLUMN `mttr` DECIMAL(10,2) COMMENT ''平均修复时间（小时）''',
    'SELECT ''mttr字段已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加availability字段
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'availability') = 0,
    'ALTER TABLE `ai_device_feature` ADD COLUMN `availability` DECIMAL(5,4) COMMENT ''可用率（0-1）''',
    'SELECT ''availability字段已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加last_major_fault_date字段
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'last_major_fault_date') = 0,
    'ALTER TABLE `ai_device_feature` ADD COLUMN `last_major_fault_date` DATE COMMENT ''最近重大故障日期''',
    'SELECT ''last_major_fault_date字段已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = 'idx_stat_month') = 0,
    'ALTER TABLE `ai_device_feature` ADD INDEX `idx_stat_month` (`stat_month`)',
    'SELECT ''idx_stat_month索引已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ================================================================
-- 6. 扩展现有表：equipment（增加重要性、安装日期等）
-- ================================================================

SET @tablename = 'equipment';

-- 添加importance_level字段
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'importance_level') = 0,
    'ALTER TABLE `equipment` ADD COLUMN `importance_level` VARCHAR(50) DEFAULT ''NORMAL'' COMMENT ''设备重要性（CRITICAL/IMPORTANT/NORMAL）''',
    'SELECT ''importance_level字段已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加install_date字段
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'install_date') = 0,
    'ALTER TABLE `equipment` ADD COLUMN `install_date` DATE COMMENT ''安装日期''',
    'SELECT ''install_date字段已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加warranty_end_date字段
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'warranty_end_date') = 0,
    'ALTER TABLE `equipment` ADD COLUMN `warranty_end_date` DATE COMMENT ''质保到期日期''',
    'SELECT ''warranty_end_date字段已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加last_maintenance_date字段
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = 'last_maintenance_date') = 0,
    'ALTER TABLE `equipment` ADD COLUMN `last_maintenance_date` DATE COMMENT ''最近维护日期''',
    'SELECT ''last_maintenance_date字段已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = 'idx_importance') = 0,
    'ALTER TABLE `equipment` ADD INDEX `idx_importance` (`importance_level`)',
    'SELECT ''idx_importance索引已存在，跳过'' AS ''info'''
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ================================================================
-- 7. 新增菜单权限（在"智能分析"模块下新增PHM子菜单）
-- ================================================================

-- 在智能分析模块（parent_id=11）下新增3个PHM子菜单 + 4个按钮权限
INSERT IGNORE INTO `menu`
    (id, parent_id, name, path, component, permission, type, icon, sort)
VALUES
    (60, 11, '设备健康监控', '/smart/health-monitor', 'phm/HealthMonitor', 'phm:health:view', 2, 'el-icon-monitor', 2),
    (61, 60, '批量评估(按钮)', NULL, NULL, 'phm:health:evaluate', 3, NULL, 1),
    (62, 11, '故障预测分析', '/smart/fault-prediction', 'phm/FaultPrediction', 'phm:prediction:view', 2, 'el-icon-warning', 3),
    (63, 62, '手动预测(按钮)', NULL, NULL, 'phm:prediction:predict', 3, NULL, 1),
    (64, 11, '维护建议管理', '/smart/maintenance-suggestion', 'phm/MaintenanceSuggestion', 'phm:suggestion:view', 2, 'el-icon-s-order', 4),
    (65, 64, '采纳建议(按钮)', NULL, NULL, 'phm:suggestion:approve', 3, NULL, 1),
    (66, 64, '拒绝建议(按钮)', NULL, NULL, 'phm:suggestion:reject', 3, NULL, 2);

-- 授权给管理员角色（role_id=1）
INSERT IGNORE INTO role_menu (role_id, menu_id)
VALUES (1, 60), (1, 61), (1, 62), (1, 63), (1, 64), (1, 65), (1, 66);

-- ================================================================
-- 迁移完成
-- ================================================================
SELECT '✓ PHM模块数据库迁移完成' AS 'Status',
       'ai_device_health, ai_fault_prediction, biz_maintenance_suggestion, sys_device_health_config' AS 'New Tables',
       'ai_device_feature, equipment' AS 'Extended Tables',
       '7个菜单项（3主菜单+4按钮）' AS 'Menus';

-- 验证表创建
SELECT TABLE_NAME, ENGINE, TABLE_ROWS, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'spare_db'
  AND TABLE_NAME IN (
      'ai_device_health',
      'ai_fault_prediction',
      'biz_maintenance_suggestion',
      'sys_device_health_config'
  );
