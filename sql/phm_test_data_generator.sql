USE spare_db;

-- ================================================================
-- PHM模块测试数据生成器（3年历史数据：2023-01 至 2026-03）
-- ================================================================
-- 用途：为PHM模块生成完整的3年模拟设备运行数据
-- 生成时间范围：2023年1月 至 2026年3月（39个月）
-- 数据来源：基于设备重要性级别模拟运行时长、故障次数、工单数、换件数
-- 使用说明：在PHM模块开发完成后执行，为健康评估和故障预测提供测试数据
-- ================================================================

SET @start_date = '2023-01-01';
SET @months_to_generate = 39;

-- ================================================================
-- 第一步：清空现有PHM测试数据
-- ================================================================
DELETE FROM ai_device_feature WHERE stat_month >= '2023-01';
DELETE FROM ai_device_health;
DELETE FROM ai_fault_prediction;
DELETE FROM biz_maintenance_suggestion;

SELECT '已清空现有PHM测试数据' AS step1_result;

-- ================================================================
-- 第二步：生成3年设备运行特征数据
-- ================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS generate_device_features$$

CREATE PROCEDURE generate_device_features()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE curr_device_id BIGINT;
    DECLARE curr_device_code VARCHAR(100);
    DECLARE curr_importance VARCHAR(50);
    DECLARE month_counter INT;
    DECLARE base_run_hours DECIMAL(8,1);
    DECLARE base_fault_count INT;
    DECLARE trend_factor DECIMAL(5,3);
    DECLARE curr_run_hours DECIMAL(8,1);
    DECLARE curr_fault_count INT;
    DECLARE curr_workorder_count INT;
    DECLARE curr_part_replace INT;
    DECLARE curr_mtbf DECIMAL(10,2);
    DECLARE curr_mttr DECIMAL(10,2);
    DECLARE curr_availability DECIMAL(5,4);
    DECLARE curr_stat_month VARCHAR(7);
    DECLARE curr_month_date DATE;

    -- 游标：遍历所有设备
    DECLARE device_cursor CURSOR FOR
        SELECT id, code, COALESCE(importance_level, 'NORMAL')
        FROM equipment
        WHERE status = '正常'  -- 只处理正常状态设备
        LIMIT 100;  -- 限制处理设备数量，避免数据量过大

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN device_cursor;

    device_loop: LOOP
        FETCH device_cursor INTO curr_device_id, curr_device_code, curr_importance;
        IF done THEN
            LEAVE device_loop;
        END IF;

        -- 根据设备重要性设定基础参数
        CASE curr_importance
            WHEN 'CRITICAL' THEN
                SET base_run_hours = 650.0;   -- 高负荷运行
                SET base_fault_count = 2;     -- 基础故障次数
                SET trend_factor = 1.050;     -- 劣化趋势因子（每年增加5%）
            WHEN 'IMPORTANT' THEN
                SET base_run_hours = 550.0;   -- 中等负荷
                SET base_fault_count = 1;
                SET trend_factor = 1.020;     -- 每年增加2%
            ELSE  -- NORMAL
                SET base_run_hours = 400.0;   -- 低负荷
                SET base_fault_count = 0;
                SET trend_factor = 1.000;     -- 无劣化趋势
        END CASE;

        -- 生成39个月数据（2023-01到2026-03）
        SET month_counter = 0;
        WHILE month_counter < @months_to_generate DO
            -- 计算当前月份
            SET curr_month_date = DATE_ADD(@start_date, INTERVAL month_counter MONTH);
            SET curr_stat_month = DATE_FORMAT(curr_month_date, '%Y-%m');

            -- 计算当月统计值（随劣化因子递增）
            -- 运行时长 = 基础值 × 劣化因子^(月数/12) + 随机波动±50
            SET curr_run_hours = base_run_hours * POW(trend_factor, month_counter / 12.0)
                                + (RAND() * 100 - 50);

            -- 故障次数 = 基础值 + 每6个月增加1次 + 30%概率额外+1
            SET curr_fault_count = base_fault_count
                                 + FLOOR(month_counter / 6)
                                 + IF(RAND() > 0.7, 1, 0);

            -- 工单数 = 故障次数 + 50%概率额外+1（预防性维护）
            SET curr_workorder_count = curr_fault_count + IF(RAND() > 0.5, 1, 0);

            -- 换件数 = 故障次数 × 2 + 40%概率额外+1
            SET curr_part_replace = curr_fault_count * 2 + IF(RAND() > 0.6, 1, 0);

            -- 计算MTBF（平均故障间隔时间，单位：小时）
            IF curr_fault_count > 0 THEN
                SET curr_mtbf = curr_run_hours / curr_fault_count;
            ELSE
                SET curr_mtbf = 9999.99;  -- 无故障时设为极大值
            END IF;

            -- 计算MTTR（平均修复时间，每次故障修复2-8小时）
            IF curr_fault_count > 0 THEN
                SET curr_mttr = (RAND() * 6 + 2);  -- 随机2-8小时
            ELSE
                SET curr_mttr = 0;
            END IF;

            -- 计算可用率 = (运行时长 - 故障修复时长) / 运行时长
            IF curr_run_hours > 0 THEN
                SET curr_availability = (curr_run_hours - curr_mttr * curr_fault_count) / curr_run_hours;
                -- 确保可用率在0-1之间
                IF curr_availability < 0 THEN
                    SET curr_availability = 0;
                END IF;
                IF curr_availability > 1 THEN
                    SET curr_availability = 1;
                END IF;
            ELSE
                SET curr_availability = 1.0;
            END IF;

            -- 插入ai_device_feature表
            INSERT INTO ai_device_feature
                (device_id, stat_month, run_hours, fault_count, work_order_count, part_replace_qty,
                 mtbf, mttr, availability, last_major_fault_date)
            VALUES
                (curr_device_id,
                 curr_stat_month,
                 curr_run_hours,
                 curr_fault_count,
                 curr_workorder_count,
                 curr_part_replace,
                 curr_mtbf,
                 curr_mttr,
                 curr_availability,
                 -- 如果当月故障次数>=3，记录故障日期
                 IF(curr_fault_count >= 3,
                    DATE_ADD(curr_month_date, INTERVAL FLOOR(RAND() * 28) DAY),
                    NULL)
                );

            SET month_counter = month_counter + 1;
        END WHILE;

    END LOOP device_loop;

    CLOSE device_cursor;
END$$

DELIMITER ;

-- 执行数据生成
CALL generate_device_features();

-- ================================================================
-- 第三步：验证数据生成结果
-- ================================================================

SELECT '测试数据生成完成！' AS step2_result,
       COUNT(*) AS total_records,
       COUNT(DISTINCT device_id) AS total_devices,
       MIN(stat_month) AS start_month,
       MAX(stat_month) AS end_month,
       ROUND(COUNT(*) / COUNT(DISTINCT device_id), 0) AS avg_months_per_device
FROM ai_device_feature
WHERE stat_month >= '2023-01';

-- 验证1：检查数据完整性（每台设备应有39个月数据）
SELECT 'Data Integrity Check' AS validation_type,
       COUNT(*) AS devices_with_incomplete_data
FROM (
    SELECT device_id,
           COUNT(*) AS month_count
    FROM ai_device_feature
    WHERE stat_month >= '2023-01'
    GROUP BY device_id
    HAVING month_count < @months_to_generate
) AS incomplete_devices;

-- 验证2：查看劣化趋势示例（选择第一台设备）
SELECT 'Trend Sample (First Device)' AS validation_type,
       device_id,
       stat_month,
       run_hours,
       fault_count,
       mtbf,
       availability
FROM ai_device_feature
WHERE device_id = (SELECT MIN(id) FROM equipment WHERE status = '正常')
  AND stat_month >= '2023-01'
ORDER BY stat_month
LIMIT 10;

-- 验证3：统计高风险设备（最近3个月平均故障次数>2）
SELECT 'High Risk Devices (Last 3 Months)' AS validation_type,
       e.code AS device_code,
       e.name AS device_name,
       e.importance_level,
       ROUND(AVG(f.fault_count), 2) AS avg_faults,
       ROUND(AVG(f.mtbf), 2) AS avg_mtbf
FROM equipment e
JOIN ai_device_feature f ON e.id = f.device_id
WHERE f.stat_month >= DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 MONTH), '%Y-%m')
GROUP BY e.id
HAVING avg_faults > 2
ORDER BY avg_faults DESC
LIMIT 10;

-- 清理存储过程
DROP PROCEDURE IF EXISTS generate_device_features;

SELECT '====== PHM测试数据生成完成 ======' AS final_message;
SELECT '提示：现在可以启动Spring Boot应用，PHM定时任务将自动运行健康评估和故障预测' AS next_step;
