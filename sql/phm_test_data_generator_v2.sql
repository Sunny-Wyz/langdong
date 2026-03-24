USE spare_db;

-- ================================================================
-- PHM模块测试数据生成器 V2（简化版）
-- 生成时间范围：2023年1月 至 2026年3月（39个月）
-- ================================================================

-- 清空现有PHM测试数据
DELETE FROM ai_device_feature WHERE stat_month >= '2023-01';
DELETE FROM ai_device_health;
DELETE FROM ai_fault_prediction;
DELETE FROM biz_maintenance_suggestion;

SELECT '已清空现有PHM测试数据' AS step1_result;

-- ================================================================
-- 使用简单的INSERT循环生成数据（针对前10台设备，39个月）
-- ================================================================

-- 设备1（CRITICAL设备）：高负荷运行，故障频繁
INSERT INTO ai_device_feature (device_id, stat_month, run_hours, fault_count, work_order_count, part_replace_qty, mtbf, mttr, availability, last_major_fault_date)
SELECT
    1 AS device_id,
    DATE_FORMAT(DATE_ADD('2023-01-01', INTERVAL month_num MONTH), '%Y-%m') AS stat_month,
    ROUND(650 + month_num * 2.5 + (RAND() * 100 - 50), 1) AS run_hours,
    2 + FLOOR(month_num / 6) + IF(RAND() > 0.7, 1, 0) AS fault_count,
    3 + FLOOR(month_num / 6) + IF(RAND() > 0.5, 1, 0) AS work_order_count,
    4 + FLOOR(month_num / 3) + IF(RAND() > 0.6, 1, 0) AS part_replace_qty,
    ROUND(650 / (2 + FLOOR(month_num / 6) + 0.1), 2) AS mtbf,
    ROUND(3 + RAND() * 4, 2) AS mttr,
    ROUND(0.95 - month_num * 0.005, 4) AS availability,
    IF(month_num % 3 = 0, DATE_ADD('2023-01-01', INTERVAL month_num MONTH), NULL) AS last_major_fault_date
FROM (
    SELECT 0 AS month_num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION
    SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION
    SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION
    SELECT 30 UNION SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION SELECT 36 UNION SELECT 37 UNION SELECT 38
) AS months;

-- 设备2（CRITICAL设备）：高负荷运行，故障频繁
INSERT INTO ai_device_feature (device_id, stat_month, run_hours, fault_count, work_order_count, part_replace_qty, mtbf, mttr, availability, last_major_fault_date)
SELECT
    2 AS device_id,
    DATE_FORMAT(DATE_ADD('2023-01-01', INTERVAL month_num MONTH), '%Y-%m') AS stat_month,
    ROUND(670 + month_num * 3.0 + (RAND() * 100 - 50), 1) AS run_hours,
    2 + FLOOR(month_num / 6) + IF(RAND() > 0.7, 1, 0) AS fault_count,
    3 + FLOOR(month_num / 6) + IF(RAND() > 0.5, 1, 0) AS work_order_count,
    5 + FLOOR(month_num / 3) + IF(RAND() > 0.6, 1, 0) AS part_replace_qty,
    ROUND(670 / (2 + FLOOR(month_num / 6) + 0.1), 2) AS mtbf,
    ROUND(3.5 + RAND() * 4, 2) AS mttr,
    ROUND(0.94 - month_num * 0.006, 4) AS availability,
    IF(month_num % 3 = 0, DATE_ADD('2023-01-01', INTERVAL month_num MONTH), NULL) AS last_major_fault_date
FROM (
    SELECT 0 AS month_num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION
    SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION
    SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION
    SELECT 30 UNION SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION SELECT 36 UNION SELECT 37 UNION SELECT 38
) AS months;

-- 设备3（IMPORTANT设备）：中等负荷
INSERT INTO ai_device_feature (device_id, stat_month, run_hours, fault_count, work_order_count, part_replace_qty, mtbf, mttr, availability, last_major_fault_date)
SELECT
    3 AS device_id,
    DATE_FORMAT(DATE_ADD('2023-01-01', INTERVAL month_num MONTH), '%Y-%m') AS stat_month,
    ROUND(550 + month_num * 1.5 + (RAND() * 80 - 40), 1) AS run_hours,
    1 + FLOOR(month_num / 8) + IF(RAND() > 0.8, 1, 0) AS fault_count,
    2 + FLOOR(month_num / 8) + IF(RAND() > 0.6, 1, 0) AS work_order_count,
    2 + FLOOR(month_num / 4) + IF(RAND() > 0.7, 1, 0) AS part_replace_qty,
    ROUND(550 / (1 + FLOOR(month_num / 8) + 0.1), 2) AS mtbf,
    ROUND(2.5 + RAND() * 3, 2) AS mttr,
    ROUND(0.97 - month_num * 0.003, 4) AS availability,
    IF(month_num % 6 = 0 AND month_num > 0, DATE_ADD('2023-01-01', INTERVAL month_num MONTH), NULL) AS last_major_fault_date
FROM (
    SELECT 0 AS month_num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION
    SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION
    SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION
    SELECT 30 UNION SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION SELECT 36 UNION SELECT 37 UNION SELECT 38
) AS months;

-- 设备4-10（NORMAL设备）：低负荷，偶发故障
INSERT INTO ai_device_feature (device_id, stat_month, run_hours, fault_count, work_order_count, part_replace_qty, mtbf, mttr, availability, last_major_fault_date)
SELECT
    4 + seq.device_offset AS device_id,
    DATE_FORMAT(DATE_ADD('2023-01-01', INTERVAL month_num MONTH), '%Y-%m') AS stat_month,
    ROUND(400 + month_num * 0.5 + (RAND() * 60 - 30), 1) AS run_hours,
    FLOOR(month_num / 12) + IF(RAND() > 0.85, 1, 0) AS fault_count,
    FLOOR(month_num / 10) + IF(RAND() > 0.7, 1, 0) AS work_order_count,
    FLOOR(month_num / 8) + IF(RAND() > 0.8, 1, 0) AS part_replace_qty,
    ROUND(400 / (FLOOR(month_num / 12) + 0.5), 2) AS mtbf,
    ROUND(2 + RAND() * 2, 2) AS mttr,
    ROUND(0.98 - month_num * 0.001, 4) AS availability,
    IF(month_num % 12 = 0 AND month_num > 0, DATE_ADD('2023-01-01', INTERVAL month_num MONTH), NULL) AS last_major_fault_date
FROM (
    SELECT 0 AS month_num UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION
    SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION
    SELECT 20 UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 UNION SELECT 28 UNION SELECT 29 UNION
    SELECT 30 UNION SELECT 31 UNION SELECT 32 UNION SELECT 33 UNION SELECT 34 UNION SELECT 35 UNION SELECT 36 UNION SELECT 37 UNION SELECT 38
) AS months
CROSS JOIN (
    SELECT 0 AS device_offset UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6
) AS seq;

-- ================================================================
-- 验证数据生成结果
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
       device_id,
       COUNT(*) AS month_count
FROM ai_device_feature
WHERE stat_month >= '2023-01'
GROUP BY device_id
ORDER BY device_id;

-- 验证2：查看劣化趋势示例（设备1）
SELECT 'Trend Sample (Device 1 - CRITICAL)' AS validation_type,
       device_id,
       stat_month,
       run_hours,
       fault_count,
       mtbf,
       ROUND(availability, 4) AS availability
FROM ai_device_feature
WHERE device_id = 1
  AND stat_month >= '2023-01'
ORDER BY stat_month
LIMIT 12;

-- 验证3：统计高风险设备（最近3个月平均故障次数>2）
SELECT 'High Risk Devices (Last 3 Months)' AS validation_type,
       e.code AS device_code,
       e.name AS device_name,
       e.importance_level,
       ROUND(AVG(f.fault_count), 2) AS avg_faults,
       ROUND(AVG(f.mtbf), 2) AS avg_mtbf,
       ROUND(AVG(f.availability), 4) AS avg_availability
FROM equipment e
JOIN ai_device_feature f ON e.id = f.device_id
WHERE f.stat_month >= DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 MONTH), '%Y-%m')
GROUP BY e.id
HAVING avg_faults >= 1
ORDER BY avg_faults DESC
LIMIT 10;

SELECT '====== PHM测试数据生成完成 ======' AS final_message;
SELECT '提示：现在可以启动Spring Boot应用，PHM定时任务将自动运行健康评估和故障预测' AS next_step;
