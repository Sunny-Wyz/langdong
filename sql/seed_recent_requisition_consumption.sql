-- =============================================================================
-- 生成近 6 个月合理领用出库流水（供训练看板 / 月度特征共用）
-- MySQL 5.7 兼容；可重复执行（按备注标记清理本脚本写入的数据）
-- =============================================================================
USE spare_db;

SET @seed_tag := 'SEED_AI_CONSUMPTION_V1';
SET @end_date := DATE_SUB(CURDATE(), INTERVAL 1 DAY);
SET @start_date := DATE_SUB(@end_date, INTERVAL 179 DAY);

-- 清理本脚本历史写入
DELETE ri FROM biz_requisition_item ri
JOIN biz_requisition r ON r.id = ri.req_id
WHERE r.remark = @seed_tag;

DELETE FROM biz_requisition WHERE remark = @seed_tag;

-- 日期序列（最多 0..999）
DROP TEMPORARY TABLE IF EXISTS tmp_seed_days;
CREATE TEMPORARY TABLE tmp_seed_days (biz_date DATE PRIMARY KEY);

INSERT INTO tmp_seed_days (biz_date)
SELECT DATE_ADD(@start_date, INTERVAL seq.n DAY) AS biz_date
FROM (
    SELECT ones.n + tens.n * 10 + hundreds.n * 100 AS n
    FROM
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
        CROSS JOIN
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
        CROSS JOIN
        (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
) seq
WHERE seq.n <= DATEDIFF(@end_date, @start_date)
  -- 工作日：周一~周五（MySQL DAYOFWEEK: 1=周日 ... 7=周六）
  AND DAYOFWEEK(DATE_ADD(@start_date, INTERVAL seq.n DAY)) BETWEEN 2 AND 6;

-- 备件子集：全部启用备件
DROP TEMPORARY TABLE IF EXISTS tmp_seed_parts;
CREATE TEMPORARY TABLE tmp_seed_parts AS
SELECT id AS spare_part_id, code AS part_code
FROM spare_part
WHERE code IS NOT NULL AND code <> '';

-- 为「备件 × 抽样工作日」生成领用单
-- 抽样规则：hash(part, date) % 5 = 0 → 约 20% 工作日有消耗，qty=1~6
DROP TEMPORARY TABLE IF EXISTS tmp_seed_events;
CREATE TEMPORARY TABLE tmp_seed_events AS
SELECT
    d.biz_date,
    p.spare_part_id,
    p.part_code,
    1 + MOD(CRC32(CONCAT(p.part_code, '-', d.biz_date, '-q')), 6) AS out_qty
FROM tmp_seed_days d
CROSS JOIN tmp_seed_parts p
WHERE MOD(CRC32(CONCAT(p.part_code, '-', d.biz_date)), 5) = 0;

-- 插入领用主表（每事件一单）
INSERT INTO biz_requisition (
    req_no, applicant_id, work_order_no, device_id, req_status, is_urgent,
    approve_id, approve_time, approve_remark, apply_time, remark, created_at, updated_at
)
SELECT
    CONCAT('SEED-', DATE_FORMAT(e.biz_date, '%Y%m%d'), '-', e.spare_part_id, '-',
           LPAD(MOD(CRC32(CONCAT(e.part_code, e.biz_date)), 10000), 4, '0')) AS req_no,
    1 AS applicant_id,
    CONCAT('WO-SEED-', e.spare_part_id) AS work_order_no,
    NULL AS device_id,
    CASE
        WHEN MOD(CRC32(CONCAT(e.part_code, e.biz_date, '-s')), 10) = 0 THEN 'OUTBOUND'
        ELSE 'INSTALLED'
    END AS req_status,
    0 AS is_urgent,
    1 AS approve_id,
    TIMESTAMP(e.biz_date, SEC_TO_TIME(9 * 3600 + MOD(CRC32(CONCAT(e.part_code, e.biz_date)), 28800))) AS approve_time,
    'seed approve' AS approve_remark,
    TIMESTAMP(e.biz_date, SEC_TO_TIME(8 * 3600 + MOD(CRC32(CONCAT(e.part_code, e.biz_date, 'a')), 3600))) AS apply_time,
    @seed_tag AS remark,
    NOW(), NOW()
FROM tmp_seed_events e;

-- 插入明细
INSERT INTO biz_requisition_item (
    req_id, spare_part_id, apply_qty, out_qty, batch_info, install_loc, install_time, installer_id, created_at, updated_at
)
SELECT
    r.id,
    e.spare_part_id,
    e.out_qty AS apply_qty,
    e.out_qty AS out_qty,
    CONCAT('SEED-BATCH-', e.part_code) AS batch_info,
    CASE WHEN r.req_status = 'INSTALLED' THEN '产线A' ELSE NULL END AS install_loc,
    CASE
        WHEN r.req_status = 'INSTALLED'
            THEN TIMESTAMP(e.biz_date, SEC_TO_TIME(14 * 3600 + MOD(CRC32(CONCAT(e.part_code, e.biz_date, 'i')), 7200)))
        ELSE NULL
    END AS install_time,
    CASE WHEN r.req_status = 'INSTALLED' THEN 1 ELSE NULL END AS installer_id,
    NOW(), NOW()
FROM tmp_seed_events e
JOIN biz_requisition r
  ON r.remark = @seed_tag
 AND r.req_no = CONCAT('SEED-', DATE_FORMAT(e.biz_date, '%Y%m%d'), '-', e.spare_part_id, '-',
                       LPAD(MOD(CRC32(CONCAT(e.part_code, e.biz_date)), 10000), 4, '0'));

-- 校验
SELECT
    @start_date AS seed_start,
    @end_date AS seed_end,
    (SELECT COUNT(*) FROM biz_requisition WHERE remark = @seed_tag) AS seeded_reqs,
    (SELECT COUNT(*) FROM biz_requisition_item ri
     JOIN biz_requisition r ON r.id = ri.req_id WHERE r.remark = @seed_tag) AS seeded_items,
    (SELECT COALESCE(SUM(ri.out_qty), 0) FROM biz_requisition_item ri
     JOIN biz_requisition r ON r.id = ri.req_id WHERE r.remark = @seed_tag) AS seeded_out_qty,
    (SELECT MAX(DATE(r.approve_time)) FROM biz_requisition r WHERE r.remark = @seed_tag) AS max_approve_date;
