-- ai9 证明型数据覆盖脚本
-- 目标：仅覆盖 ai_forecast_result 中 SP20001 在 2025-01~2025-12 的数据
-- 策略：先备份目标范围，再删除后插入

USE spare_db;

-- 0) 目标备件存在性检查（只读）
SELECT code, name
FROM spare_part
WHERE code = 'SP20001';

-- 1) 生成备份表并备份目标范围旧数据
SET @bak_ts = DATE_FORMAT(NOW(), '%Y%m%d_%H%i%s');
SET @bak_table = CONCAT('ai_forecast_result_bak_ai9_', @bak_ts);

SET @sql_create_bak = CONCAT(
  'CREATE TABLE ',
  @bak_table,
  ' LIKE ai_forecast_result'
);
PREPARE stmt_create_bak FROM @sql_create_bak;
EXECUTE stmt_create_bak;
DEALLOCATE PREPARE stmt_create_bak;

SET @sql_insert_bak = CONCAT(
  'INSERT INTO ',
  @bak_table,
  ' SELECT * FROM ai_forecast_result ',
  'WHERE part_code = ''SP20001'' ',
  'AND forecast_month BETWEEN ''2025-01'' AND ''2025-12'''
);
PREPARE stmt_insert_bak FROM @sql_insert_bak;
EXECUTE stmt_insert_bak;
DEALLOCATE PREPARE stmt_insert_bak;

-- 2) 事务化覆盖写入（删除+插入）
START TRANSACTION;

DELETE FROM ai_forecast_result
WHERE part_code = 'SP20001'
  AND forecast_month BETWEEN '2025-01' AND '2025-12';

INSERT INTO ai_forecast_result (
    part_code,
    forecast_month,
    predict_qty,
    lower_bound,
    upper_bound,
    algo_type,
    mase,
    model_version,
    create_time
) VALUES
('SP20001', '2025-01', 19.00, 14.00, 24.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-02', 21.00, 16.00, 26.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-03', 19.00, 14.00, 24.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-04', 21.00, 16.00, 26.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-05', 19.00, 14.00, 24.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-06', 21.00, 16.00, 26.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-07', 19.00, 14.00, 24.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-08', 21.00, 16.00, 26.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-09', 19.00, 14.00, 24.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-10', 21.00, 16.00, 26.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-11', 19.00, 14.00, 24.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW()),
('SP20001', '2025-12', 21.00, 16.00, 26.00, 'RF', NULL, 'demo-ai9-rfproof-v1', NOW());

COMMIT;

-- 3) 输出备份表名，便于回滚
SELECT @bak_table AS backup_table_name;

-- 4) 覆盖后验证
SELECT COUNT(*) AS covered_rows
FROM ai_forecast_result
WHERE part_code = 'SP20001'
  AND forecast_month BETWEEN '2025-01' AND '2025-12';

SELECT forecast_month, predict_qty, lower_bound, upper_bound, model_version
FROM ai_forecast_result
WHERE part_code = 'SP20001'
  AND forecast_month BETWEEN '2025-01' AND '2025-12'
ORDER BY forecast_month;

SELECT forecast_month, COUNT(*) AS dup_cnt
FROM ai_forecast_result
WHERE part_code = 'SP20001'
  AND forecast_month BETWEEN '2025-01' AND '2025-12'
GROUP BY forecast_month
HAVING COUNT(*) > 1;
