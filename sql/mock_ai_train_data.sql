-- 生成 AI 训练数据看板的随机模拟数据（替换所有现有数据）
-- 兼容 MySQL 5.7+，不使用递归 CTE
-- 数据特征：工作日活跃度高于周末，不同备件有不同使用频率，采购批量到货
USE spare_db;

SET @end_date   := DATE_SUB(CURDATE(), INTERVAL 1 DAY);
SET @start_date := DATE_SUB(@end_date, INTERVAL 729 DAY);

-- 清空旧数据（幂等可重复执行）
DELETE FROM ai_part_daily_train_data
WHERE biz_date BETWEEN @start_date AND @end_date;

-- 第一层：生成日期序列 × 备件笛卡尔积，同时生成随机种子
-- 第二层：基于随机种子计算各业务字段
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
    base.biz_date,
    base.spare_part_id,
    base.part_code,

    /* ── 日出库量：高频备件(id%5=0) 出库概率60%，普通备件35%，周末再减半 ── */
    CASE
        WHEN base.r_out < (
            IF(base.spare_part_id % 5 = 0, 0.60, 0.35)
            * IF(base.is_weekend = 1, 0.50, 1.00)
        )
        THEN GREATEST(1, FLOOR(
                base.r_qty * (4 + (base.spare_part_id % 4) * 2)
             ))
        ELSE 0
    END AS daily_outbound_qty,

    /* ── 领用申请量：比出库略多（申请后不一定全部出库） ── */
    CASE
        WHEN base.r_req < (
            IF(base.spare_part_id % 5 = 0, 0.65, 0.40)
            * IF(base.is_weekend = 1, 0.40, 1.00)
        )
        THEN GREATEST(1, FLOOR(
                base.r_qty * (5 + (base.spare_part_id % 4) * 2)
             ))
        ELSE 0
    END AS daily_requisition_apply_qty,

    /* ── 领用出库量：与日出库量保持一致（复用 r_out、r_qty） ── */
    CASE
        WHEN base.r_out < (
            IF(base.spare_part_id % 5 = 0, 0.60, 0.35)
            * IF(base.is_weekend = 1, 0.50, 1.00)
        )
        THEN GREATEST(1, FLOOR(
                base.r_qty * (3 + (base.spare_part_id % 4) * 2)
             ))
        ELSE 0
    END AS daily_requisition_out_qty,

    /* ── 安装量：出库后约80%完成安装 ── */
    CASE
        WHEN base.r_out < (
            IF(base.spare_part_id % 5 = 0, 0.60, 0.35)
            * IF(base.is_weekend = 1, 0.50, 1.00)
        ) AND base.r_install < 0.80
        THEN GREATEST(1, FLOOR(base.r_qty * (3 + (base.spare_part_id % 3))))
        ELSE 0
    END AS daily_install_qty,

    /* ── 工单数：有出库时 60% 概率关联1~3个工单 ── */
    CASE
        WHEN base.r_out < (
            IF(base.spare_part_id % 5 = 0, 0.60, 0.35)
            * IF(base.is_weekend = 1, 0.50, 1.00)
        ) AND base.r_wo < 0.60
        THEN GREATEST(1, FLOOR(1 + base.r_wo * 3))
        ELSE 0
    END AS daily_work_order_cnt,

    /* ── 采购到货量：约5%的天有到货，批量10~100 ── */
    IF(base.r_pur > 0.95, FLOOR(10 + base.r_pur_qty * 90), 0) AS daily_purchase_arrival_qty,
    IF(base.r_pur > 0.95, 1, 0)                                AS daily_purchase_arrival_orders,

    base.day_of_week,
    base.is_weekend,

    /* ── 来源标记：有出库时随机分配数据来源 ── */
    CASE
        WHEN base.r_out < (
            IF(base.spare_part_id % 5 = 0, 0.60, 0.35)
            * IF(base.is_weekend = 1, 0.50, 1.00)
        )
        THEN CASE
                 WHEN base.r_src < 0.40 THEN 'TRACE'
                 WHEN base.r_src < 0.70 THEN 'REQ_OUT'
                 ELSE 'TRACE_REQ'
             END
        ELSE 'NONE'
    END AS source_level,

    /* ── 插补标记：无出库数据时标记为插补 ── */
    CASE
        WHEN base.r_out < (
            IF(base.spare_part_id % 5 = 0, 0.60, 0.35)
            * IF(base.is_weekend = 1, 0.50, 1.00)
        )
        THEN 0
        ELSE 1
    END AS is_imputed,

    NOW(),
    NOW()

FROM (
    /* ── 内层：日期序列 × 备件，同时生成所有随机种子 ── */
    SELECT
        d.biz_date,
        DAYOFWEEK(d.biz_date)                         AS day_of_week,
        IF(DAYOFWEEK(d.biz_date) IN (1, 7), 1, 0)    AS is_weekend,
        sp.id                                          AS spare_part_id,
        sp.code                                        AS part_code,
        RAND()                                         AS r_out,       -- 出库/领用出库概率
        RAND()                                         AS r_req,       -- 领用申请概率
        RAND()                                         AS r_qty,       -- 数量大小
        RAND()                                         AS r_install,   -- 是否安装
        RAND()                                         AS r_wo,        -- 工单关联
        RAND()                                         AS r_pur,       -- 采购到货概率
        RAND()                                         AS r_pur_qty,   -- 采购到货数量
        RAND()                                         AS r_src        -- 来源类型
    FROM (
        /* ── 日期生成器（兼容 MySQL 5.7，无递归 CTE） ── */
        SELECT DATE_ADD(@start_date, INTERVAL seq.n DAY) AS biz_date
        FROM (
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
) base;

-- 执行结果摘要
SELECT
    @start_date                                          AS start_date,
    @end_date                                            AS end_date,
    COUNT(*)                                             AS total_rows,
    COUNT(DISTINCT spare_part_id)                        AS distinct_parts,
    SUM(daily_outbound_qty)                              AS total_outbound_qty,
    ROUND(AVG(daily_outbound_qty), 2)                   AS avg_outbound_qty,
    SUM(IF(source_level = 'TRACE',     1, 0))            AS cnt_trace,
    SUM(IF(source_level = 'REQ_OUT',   1, 0))            AS cnt_req_out,
    SUM(IF(source_level = 'TRACE_REQ', 1, 0))            AS cnt_trace_req,
    SUM(IF(source_level = 'NONE',      1, 0))            AS cnt_none,
    SUM(IF(is_imputed = 1,             1, 0))            AS cnt_imputed,
    MIN(biz_date)                                        AS min_date,
    MAX(biz_date)                                        AS max_date
FROM ai_part_daily_train_data
WHERE biz_date BETWEEN @start_date AND @end_date;
