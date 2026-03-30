-- Generate 2-year daily real training data for AI model
-- MySQL 5.7 compatible (no recursive CTE)
USE spare_db;

CREATE TABLE IF NOT EXISTS ai_part_daily_train_data (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
    biz_date DATE NOT NULL COMMENT 'Business date',
    spare_part_id BIGINT NOT NULL COMMENT 'spare_part.id',
    part_code VARCHAR(20) NOT NULL COMMENT 'spare_part.code',
    daily_outbound_qty INT NOT NULL DEFAULT 0 COMMENT 'Daily outbound quantity (label)',
    daily_requisition_apply_qty INT NOT NULL DEFAULT 0 COMMENT 'Daily requisition apply quantity',
    daily_requisition_out_qty INT NOT NULL DEFAULT 0 COMMENT 'Daily requisition outbound quantity',
    daily_install_qty INT NOT NULL DEFAULT 0 COMMENT 'Daily install quantity',
    daily_work_order_cnt INT NOT NULL DEFAULT 0 COMMENT 'Daily related work order count',
    daily_purchase_arrival_qty INT NOT NULL DEFAULT 0 COMMENT 'Daily purchase arrival quantity',
    daily_purchase_arrival_orders INT NOT NULL DEFAULT 0 COMMENT 'Daily purchase arrival order count',
    day_of_week TINYINT NOT NULL COMMENT '1=Sunday ... 7=Saturday',
    is_weekend TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'Weekend flag',
    source_level VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'TRACE/REQ_OUT/TRACE_REQ/NONE',
    is_imputed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1 means no real outbound event on day',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_part_date (spare_part_id, biz_date),
    KEY idx_biz_date (biz_date),
    KEY idx_part_code_date (part_code, biz_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI daily training dataset for spare parts';

SET @end_date := DATE_SUB(CURDATE(), INTERVAL 1 DAY);
SET @start_date := DATE_SUB(@end_date, INTERVAL 729 DAY);

START TRANSACTION;

-- Idempotent rerun: regenerate target date window
DELETE FROM ai_part_daily_train_data
WHERE biz_date BETWEEN @start_date AND @end_date;

INSERT INTO ai_part_daily_train_data (
    biz_date,
    spare_part_id,
    part_code,
    daily_outbound_qty,
    daily_requisition_apply_qty,
    daily_requisition_out_qty,
    daily_install_qty,
    daily_work_order_cnt,
    daily_purchase_arrival_qty,
    daily_purchase_arrival_orders,
    day_of_week,
    is_weekend,
    source_level,
    is_imputed,
    created_at,
    updated_at
)
SELECT
    d.biz_date,
    sp.id AS spare_part_id,
    sp.code AS part_code,
    GREATEST(0, GREATEST(COALESCE(t_trace.outbound_qty, 0), COALESCE(t_req_out.outbound_qty, 0))) AS daily_outbound_qty,
    GREATEST(0, COALESCE(t_req_apply.apply_qty, 0)) AS daily_requisition_apply_qty,
    GREATEST(0, COALESCE(t_req_out.outbound_qty, 0)) AS daily_requisition_out_qty,
    GREATEST(0, COALESCE(t_install.install_qty, 0)) AS daily_install_qty,
    GREATEST(0, COALESCE(t_workorder.work_order_cnt, 0)) AS daily_work_order_cnt,
    GREATEST(0, COALESCE(t_purchase.arrival_qty, 0)) AS daily_purchase_arrival_qty,
    GREATEST(0, COALESCE(t_purchase.arrival_orders, 0)) AS daily_purchase_arrival_orders,
    DAYOFWEEK(d.biz_date) AS day_of_week,
    CASE WHEN DAYOFWEEK(d.biz_date) IN (1, 7) THEN 1 ELSE 0 END AS is_weekend,
    CASE
        WHEN t_trace.outbound_qty IS NOT NULL AND t_req_out.outbound_qty IS NOT NULL THEN 'TRACE_REQ'
        WHEN t_trace.outbound_qty IS NOT NULL THEN 'TRACE'
        WHEN t_req_out.outbound_qty IS NOT NULL THEN 'REQ_OUT'
        ELSE 'NONE'
    END AS source_level,
    CASE
        WHEN t_trace.outbound_qty IS NULL AND t_req_out.outbound_qty IS NULL THEN 1
        ELSE 0
    END AS is_imputed,
    NOW(),
    NOW()
FROM
(
    SELECT DATE_ADD(@start_date, INTERVAL seq.n DAY) AS biz_date
    FROM
    (
        SELECT ones.n + tens.n * 10 + hundreds.n * 100 + thousands.n * 1000 AS n
        FROM
            (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
             UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
            CROSS JOIN
            (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
             UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
            CROSS JOIN
            (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
             UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
            CROSS JOIN
            (SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) thousands
    ) seq
    WHERE seq.n <= DATEDIFF(@end_date, @start_date)
) d
CROSS JOIN spare_part sp
LEFT JOIN
(
    SELECT
        DATE(outbound_time) AS biz_date,
        spare_part_id,
        SUM(GREATEST(deduct_qty, 0)) AS outbound_qty
    FROM biz_outbound_batch_trace
    WHERE outbound_time >= @start_date
      AND outbound_time < DATE_ADD(@end_date, INTERVAL 1 DAY)
    GROUP BY DATE(outbound_time), spare_part_id
) t_trace
    ON t_trace.biz_date = d.biz_date
   AND t_trace.spare_part_id = sp.id
LEFT JOIN
(
    SELECT
        DATE(COALESCE(r.approve_time, r.apply_time)) AS biz_date,
        ri.spare_part_id,
        SUM(GREATEST(COALESCE(ri.out_qty, 0), 0)) AS outbound_qty
    FROM biz_requisition r
    JOIN biz_requisition_item ri ON ri.req_id = r.id
    WHERE r.req_status IN ('OUTBOUND', 'INSTALLED')
      AND COALESCE(r.approve_time, r.apply_time) >= @start_date
      AND COALESCE(r.approve_time, r.apply_time) < DATE_ADD(@end_date, INTERVAL 1 DAY)
    GROUP BY DATE(COALESCE(r.approve_time, r.apply_time)), ri.spare_part_id
) t_req_out
    ON t_req_out.biz_date = d.biz_date
   AND t_req_out.spare_part_id = sp.id
LEFT JOIN
(
    SELECT
        DATE(r.apply_time) AS biz_date,
        ri.spare_part_id,
        SUM(GREATEST(COALESCE(ri.apply_qty, 0), 0)) AS apply_qty
    FROM biz_requisition r
    JOIN biz_requisition_item ri ON ri.req_id = r.id
    WHERE r.apply_time >= @start_date
      AND r.apply_time < DATE_ADD(@end_date, INTERVAL 1 DAY)
    GROUP BY DATE(r.apply_time), ri.spare_part_id
) t_req_apply
    ON t_req_apply.biz_date = d.biz_date
   AND t_req_apply.spare_part_id = sp.id
LEFT JOIN
(
    SELECT
        DATE(ri.install_time) AS biz_date,
        ri.spare_part_id,
        SUM(GREATEST(COALESCE(ri.out_qty, 0), 0)) AS install_qty
    FROM biz_requisition_item ri
    WHERE ri.install_time >= @start_date
      AND ri.install_time < DATE_ADD(@end_date, INTERVAL 1 DAY)
    GROUP BY DATE(ri.install_time), ri.spare_part_id
) t_install
    ON t_install.biz_date = d.biz_date
   AND t_install.spare_part_id = sp.id
LEFT JOIN
(
    SELECT
        DATE(r.apply_time) AS biz_date,
        ri.spare_part_id,
        COUNT(DISTINCT r.work_order_no) AS work_order_cnt
    FROM biz_requisition r
    JOIN biz_requisition_item ri ON ri.req_id = r.id
    WHERE r.work_order_no IS NOT NULL
      AND r.work_order_no <> ''
      AND r.apply_time >= @start_date
      AND r.apply_time < DATE_ADD(@end_date, INTERVAL 1 DAY)
    GROUP BY DATE(r.apply_time), ri.spare_part_id
) t_workorder
    ON t_workorder.biz_date = d.biz_date
   AND t_workorder.spare_part_id = sp.id
LEFT JOIN
(
    SELECT
        actual_date AS biz_date,
        spare_part_id,
        SUM(GREATEST(COALESCE(order_qty, 0), 0)) AS arrival_qty,
        COUNT(*) AS arrival_orders
    FROM biz_purchase_order
    WHERE actual_date >= @start_date
      AND actual_date <= @end_date
    GROUP BY actual_date, spare_part_id
) t_purchase
    ON t_purchase.biz_date = d.biz_date
   AND t_purchase.spare_part_id = sp.id;

SET @rows_inserted := ROW_COUNT();

COMMIT;

SELECT
    @start_date AS start_date,
    @end_date AS end_date,
    @rows_inserted AS rows_inserted,
    COUNT(*) AS rows_loaded,
    COUNT(DISTINCT spare_part_id) AS distinct_parts,
    MIN(biz_date) AS min_date,
    MAX(biz_date) AS max_date
FROM ai_part_daily_train_data
WHERE biz_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 730 DAY) AND DATE_SUB(CURDATE(), INTERVAL 1 DAY);
