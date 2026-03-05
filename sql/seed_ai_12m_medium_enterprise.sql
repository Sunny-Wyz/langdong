-- =============================================================
-- 12个月中型企业备件数据回填（仅追加）
-- 目标窗口：2025-03 ~ 2026-02
-- 约束：不修改系统管理相关表（user/role/menu/user_role/role_menu）
-- 说明：
--   1) biz_requisition 每月100条，共1200条
--   2) ai_forecast_result 每月100条，共1200条
--   3) 可重复执行，每次会生成新的 run_id（用于回滚）
-- MySQL: 5.7+
-- =============================================================

USE spare_db;
SET NAMES utf8mb4;

-- -----------------------------
-- 0) 系统表基线快照（只读）
-- -----------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_sys_counts_before;
CREATE TEMPORARY TABLE tmp_sys_counts_before (
    table_name VARCHAR(32) PRIMARY KEY,
    cnt BIGINT NOT NULL
) ENGINE=MEMORY;

INSERT INTO tmp_sys_counts_before(table_name, cnt)
SELECT 'user', COUNT(*) FROM `user`
UNION ALL SELECT 'role', COUNT(*) FROM `role`
UNION ALL SELECT 'menu', COUNT(*) FROM `menu`
UNION ALL SELECT 'user_role', COUNT(*) FROM `user_role`
UNION ALL SELECT 'role_menu', COUNT(*) FROM `role_menu`;

-- -----------------------------
-- 1) 预检查（只读）
-- -----------------------------
SELECT 'precheck_spare_part' AS check_item, COUNT(*) AS row_count FROM spare_part;
SELECT 'precheck_equipment'  AS check_item, COUNT(*) AS row_count FROM equipment;
SELECT 'precheck_biz_requisition' AS check_item, COUNT(*) AS row_count FROM biz_requisition;
SELECT 'precheck_ai_forecast_result' AS check_item, COUNT(*) AS row_count FROM ai_forecast_result;

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_seed_ai_12m_medium_enterprise$$
CREATE PROCEDURE sp_seed_ai_12m_medium_enterprise()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_month_start DATE;
    DECLARE v_ym CHAR(7);
    DECLARE v_ym6 CHAR(6);
    DECLARE v_factor DECIMAL(4,2);

    DECLARE v_run_id CHAR(10);
    DECLARE v_model_version VARCHAR(20);

    DECLARE v_seq INT;
    DECLARE v_item_idx INT;
    DECLARE v_item_count INT;
    DECLARE v_status VARCHAR(20);

    DECLARE v_req_id BIGINT;
    DECLARE v_req_no VARCHAR(40);
    DECLARE v_work_order_no VARCHAR(40);

    DECLARE v_applicant_id BIGINT;
    DECLARE v_approver_id BIGINT;
    DECLARE v_device_id BIGINT;

    DECLARE v_day_offset INT;
    DECLARE v_week_day INT;
    DECLARE v_base_date DATE;
    DECLARE v_hh INT;
    DECLARE v_mi INT;
    DECLARE v_ss INT;
    DECLARE v_created_at DATETIME;
    DECLARE v_approve_at DATETIME;

    DECLARE v_pool_rand DECIMAL(10,6);
    DECLARE v_pool_type CHAR(1);
    DECLARE v_part_id BIGINT;

    DECLARE v_month_no INT;
    DECLARE v_base_qty INT;
    DECLARE v_apply_qty INT;
    DECLARE v_out_qty INT;

    DECLARE v_fseq INT;
    DECLARE v_part_code VARCHAR(20);
    DECLARE v_actual_qty DECIMAL(12,2);
    DECLARE v_algo VARCHAR(20);
    DECLARE v_noise DECIMAL(10,6);
    DECLARE v_predict DECIMAL(12,2);
    DECLARE v_lower DECIMAL(12,2);
    DECLARE v_upper DECIMAL(12,2);
    DECLARE v_mase DECIMAL(10,4);
    DECLARE v_anchor_idx INT;

    DECLARE cur_month CURSOR FOR
        SELECT month_start, ym, ym6, factor
        FROM tmp_months
        ORDER BY month_start;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    -- run_id: 10位（yyMMddHH + 两位随机数），保证 req_no 长度安全（<=30）
    SET v_run_id = CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H'), LPAD(FLOOR(RAND() * 100), 2, '0'));
    WHILE EXISTS (
        SELECT 1
        FROM biz_requisition
        WHERE req_no LIKE CONCAT('REQ-SIM-', v_run_id, '-%')
        LIMIT 1
    ) DO
        SET v_run_id = CONCAT(DATE_FORMAT(NOW(), '%y%m%d%H'), LPAD(FLOOR(RAND() * 100), 2, '0'));
    END WHILE;
    SET v_model_version = CONCAT('seed-mid-', v_run_id);

    DROP TEMPORARY TABLE IF EXISTS tmp_seed_meta;
    CREATE TEMPORARY TABLE tmp_seed_meta (
        run_id CHAR(10) NOT NULL,
        model_version VARCHAR(20) NOT NULL
    ) ENGINE=MEMORY;
    INSERT INTO tmp_seed_meta VALUES (v_run_id, v_model_version);

    -- month seed window: fixed 2025-03 ~ 2026-02
    DROP TEMPORARY TABLE IF EXISTS tmp_months;
    CREATE TEMPORARY TABLE tmp_months (
        month_start DATE PRIMARY KEY,
        ym CHAR(7) NOT NULL,
        ym6 CHAR(6) NOT NULL,
        factor DECIMAL(4,2) NOT NULL
    ) ENGINE=MEMORY;

    INSERT INTO tmp_months(month_start, ym, ym6, factor) VALUES
    ('2025-03-01', '2025-03', '202503', 1.05),
    ('2025-04-01', '2025-04', '202504', 1.05),
    ('2025-05-01', '2025-05', '202505', 1.05),
    ('2025-06-01', '2025-06', '202506', 1.15),
    ('2025-07-01', '2025-07', '202507', 1.15),
    ('2025-08-01', '2025-08', '202508', 1.15),
    ('2025-09-01', '2025-09', '202509', 1.10),
    ('2025-10-01', '2025-10', '202510', 1.10),
    ('2025-11-01', '2025-11', '202511', 1.10),
    ('2025-12-01', '2025-12', '202512', 0.95),
    ('2026-01-01', '2026-01', '202601', 0.95),
    ('2026-02-01', '2026-02', '202602', 0.95);

    -- 基础池
    DROP TEMPORARY TABLE IF EXISTS tmp_users;
    CREATE TEMPORARY TABLE tmp_users ENGINE=MEMORY AS
    SELECT id FROM `user` WHERE status = 1 ORDER BY id;

    DROP TEMPORARY TABLE IF EXISTS tmp_equipment;
    CREATE TEMPORARY TABLE tmp_equipment ENGINE=MEMORY AS
    SELECT id FROM equipment ORDER BY id;

    IF (SELECT COUNT(*) FROM tmp_users) = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No active users found in table user.';
    END IF;

    IF (SELECT COUNT(*) FROM tmp_equipment) = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No equipment found in table equipment.';
    END IF;

    IF (SELECT COUNT(*) FROM spare_part) = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No spare parts found in table spare_part.';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_pool_high;
    CREATE TEMPORARY TABLE tmp_pool_high ENGINE=MEMORY AS
    SELECT id
    FROM spare_part
    WHERE COALESCE(is_critical, 0) = 0
      AND COALESCE(price, 0) <= 120;

    IF (SELECT COUNT(*) FROM tmp_pool_high) = 0 THEN
        INSERT INTO tmp_pool_high
        SELECT id FROM spare_part ORDER BY id;
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_pool_medium;
    CREATE TEMPORARY TABLE tmp_pool_medium ENGINE=MEMORY AS
    SELECT id
    FROM spare_part
    WHERE COALESCE(is_critical, 0) = 0
      AND COALESCE(price, 0) > 120
      AND COALESCE(price, 0) <= 600;

    IF (SELECT COUNT(*) FROM tmp_pool_medium) = 0 THEN
        INSERT INTO tmp_pool_medium
        SELECT id FROM spare_part ORDER BY id LIMIT 20;
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_pool_low;
    CREATE TEMPORARY TABLE tmp_pool_low ENGINE=MEMORY AS
    SELECT id
    FROM spare_part
    WHERE COALESCE(is_critical, 0) = 1
       OR COALESCE(price, 0) > 600;

    IF (SELECT COUNT(*) FROM tmp_pool_low) = 0 THEN
        INSERT INTO tmp_pool_low
        SELECT id FROM spare_part ORDER BY id DESC LIMIT 20;
    END IF;

    -- 锚点备件（保证历史趋势按 partCode 可画跨月线）
    DROP TEMPORARY TABLE IF EXISTS tmp_anchor_parts;
    CREATE TEMPORARY TABLE tmp_anchor_parts (
        idx INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
        part_code VARCHAR(20) NOT NULL
    ) ENGINE=MEMORY;

    INSERT INTO tmp_anchor_parts(part_code)
    SELECT code
    FROM spare_part
    ORDER BY COALESCE(is_critical, 0) ASC, COALESCE(price, 0) ASC, id ASC
    LIMIT 5;

    IF (SELECT COUNT(*) FROM tmp_anchor_parts) = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Failed to build anchor part pool.';
    END IF;

    -- ========== 主流程：按月生成 ==========
    OPEN cur_month;

    month_loop: LOOP
        FETCH cur_month INTO v_month_start, v_ym, v_ym6, v_factor;
        IF v_done = 1 THEN
            LEAVE month_loop;
        END IF;

        -- 1) 每月 100 条领用主单
        SET v_seq = 1;
        WHILE v_seq <= 100 DO
            -- 固定状态比例：68/17/8/4/3
            IF v_seq <= 68 THEN
                SET v_status = 'INSTALLED';
            ELSEIF v_seq <= 85 THEN
                SET v_status = 'OUTBOUND';
            ELSEIF v_seq <= 93 THEN
                SET v_status = 'APPROVED';
            ELSEIF v_seq <= 97 THEN
                SET v_status = 'REJECTED';
            ELSE
                SET v_status = 'PENDING';
            END IF;

            -- 月内工作日工作时段
            SET v_day_offset = FLOOR(RAND() * 26);
            SET v_base_date = DATE_ADD(v_month_start, INTERVAL v_day_offset DAY);
            SET v_week_day = DAYOFWEEK(v_base_date);
            IF v_week_day = 1 THEN
                SET v_base_date = DATE_ADD(v_base_date, INTERVAL 1 DAY);
            ELSEIF v_week_day = 7 THEN
                SET v_base_date = DATE_ADD(v_base_date, INTERVAL 2 DAY);
            END IF;

            SET v_hh = 8 + FLOOR(RAND() * 10);
            SET v_mi = FLOOR(RAND() * 60);
            SET v_ss = FLOOR(RAND() * 60);
            SET v_created_at = TIMESTAMP(v_base_date, MAKETIME(v_hh, v_mi, v_ss));

            IF v_status IN ('INSTALLED', 'OUTBOUND') THEN
                SET v_approve_at = DATE_ADD(v_created_at, INTERVAL (2 + FLOOR(RAND() * 8)) HOUR);
                SELECT id INTO v_approver_id FROM tmp_users ORDER BY RAND() LIMIT 1;
            ELSE
                SET v_approve_at = NULL;
                SET v_approver_id = NULL;
            END IF;

            SELECT id INTO v_applicant_id FROM tmp_users ORDER BY RAND() LIMIT 1;
            SELECT id INTO v_device_id FROM tmp_equipment ORDER BY RAND() LIMIT 1;

            SET v_req_no = CONCAT('REQ-SIM-', v_run_id, '-', v_ym6, '-', LPAD(v_seq, 3, '0'));
            SET v_work_order_no = CONCAT('WO-SIM-', v_ym6, '-', LPAD(v_seq, 4, '0'));

            INSERT INTO biz_requisition (
                req_no,
                applicant_id,
                work_order_no,
                device_id,
                req_status,
                is_urgent,
                approve_id,
                approve_time,
                apply_time,
                remark,
                created_at,
                updated_at
            ) VALUES (
                v_req_no,
                v_applicant_id,
                v_work_order_no,
                v_device_id,
                v_status,
                IF(RAND() < 0.18, 1, 0),
                v_approver_id,
                v_approve_at,
                v_created_at,
                CONCAT('Seed data run=', v_run_id, ', month=', v_ym),
                v_created_at,
                v_created_at
            );

            SET v_req_id = LAST_INSERT_ID();

            -- 2) 明细：1~3条（50%/35%/15%）
            IF v_seq <= 50 THEN
                SET v_item_count = 1;
            ELSEIF v_seq <= 85 THEN
                SET v_item_count = 2;
            ELSE
                SET v_item_count = 3;
            END IF;

            SET v_item_idx = 1;
            SET v_month_no = CAST(SUBSTRING(v_ym, 6, 2) AS UNSIGNED);

            WHILE v_item_idx <= v_item_count DO
                SET v_pool_rand = RAND();
                IF v_pool_rand < 0.55 THEN
                    SET v_pool_type = 'H';
                    SELECT id INTO v_part_id FROM tmp_pool_high ORDER BY RAND() LIMIT 1;
                    SET v_base_qty = 3 + FLOOR(RAND() * 10); -- 3~12
                ELSEIF v_pool_rand < 0.85 THEN
                    SET v_pool_type = 'M';
                    SELECT id INTO v_part_id FROM tmp_pool_medium ORDER BY RAND() LIMIT 1;
                    SET v_base_qty = 1 + FLOOR(RAND() * 6); -- 1~6
                ELSE
                    SET v_pool_type = 'L';
                    SELECT id INTO v_part_id FROM tmp_pool_low ORDER BY RAND() LIMIT 1;
                    SET v_base_qty = 1 + FLOOR(RAND() * 3); -- 1~3
                END IF;

                SET v_apply_qty = GREATEST(1, ROUND(v_base_qty * v_factor, 0));

                IF v_status IN ('INSTALLED', 'OUTBOUND') THEN
                    SET v_out_qty = GREATEST(1, LEAST(v_apply_qty, v_apply_qty - FLOOR(RAND() * 2)));
                ELSE
                    SET v_out_qty = NULL;
                END IF;

                INSERT INTO biz_requisition_item (
                    req_id,
                    spare_part_id,
                    apply_qty,
                    out_qty,
                    created_at,
                    updated_at
                ) VALUES (
                    v_req_id,
                    v_part_id,
                    v_apply_qty,
                    v_out_qty,
                    v_created_at,
                    v_created_at
                );

                SET v_item_idx = v_item_idx + 1;
            END WHILE;

            SET v_seq = v_seq + 1;
        END WHILE;

        -- 3) 每月 100 条 AI 预测结果
        SET v_fseq = 1;
        WHILE v_fseq <= 100 DO
            -- 算法占比：RF 65%, SBA 30%, FALLBACK 5%
            IF v_fseq <= 65 THEN
                SET v_algo = 'RF';
            ELSEIF v_fseq <= 95 THEN
                SET v_algo = 'SBA';
            ELSE
                SET v_algo = 'FALLBACK';
            END IF;

            -- 前20条使用锚点备件，确保跨月历史趋势
            IF v_fseq <= 20 THEN
                SET v_anchor_idx = 1 + MOD(v_fseq - 1, 5);
                SELECT part_code INTO v_part_code
                FROM tmp_anchor_parts
                WHERE idx = v_anchor_idx;

                SELECT COALESCE(SUM(ri.out_qty), 0)
                INTO v_actual_qty
                FROM biz_requisition r
                JOIN biz_requisition_item ri ON r.id = ri.req_id
                JOIN spare_part sp ON sp.id = ri.spare_part_id
                WHERE r.req_no LIKE CONCAT('REQ-SIM-', v_run_id, '-%')
                  AND DATE_FORMAT(r.approve_time, '%Y-%m') = v_ym
                  AND r.req_status IN ('OUTBOUND', 'INSTALLED')
                  AND ri.out_qty > 0
                  AND sp.code = v_part_code;
            ELSE
                SELECT
                    (
                        SELECT t.part_code
                        FROM (
                            SELECT
                                sp.code AS part_code,
                                SUM(ri.out_qty) AS actual_qty
                            FROM biz_requisition r
                            JOIN biz_requisition_item ri ON r.id = ri.req_id
                            JOIN spare_part sp ON sp.id = ri.spare_part_id
                            WHERE r.req_no LIKE CONCAT('REQ-SIM-', v_run_id, '-%')
                              AND DATE_FORMAT(r.approve_time, '%Y-%m') = v_ym
                              AND r.req_status IN ('OUTBOUND', 'INSTALLED')
                              AND ri.out_qty > 0
                            GROUP BY sp.code
                            ORDER BY RAND()
                            LIMIT 1
                        ) t
                    ),
                    (
                        SELECT t.actual_qty
                        FROM (
                            SELECT
                                sp.code AS part_code,
                                SUM(ri.out_qty) AS actual_qty
                            FROM biz_requisition r
                            JOIN biz_requisition_item ri ON r.id = ri.req_id
                            JOIN spare_part sp ON sp.id = ri.spare_part_id
                            WHERE r.req_no LIKE CONCAT('REQ-SIM-', v_run_id, '-%')
                              AND DATE_FORMAT(r.approve_time, '%Y-%m') = v_ym
                              AND r.req_status IN ('OUTBOUND', 'INSTALLED')
                              AND ri.out_qty > 0
                            GROUP BY sp.code
                            ORDER BY RAND()
                            LIMIT 1
                        ) t
                    )
                INTO v_part_code, v_actual_qty;
            END IF;

            IF v_part_code IS NULL THEN
                SELECT code INTO v_part_code FROM spare_part ORDER BY RAND() LIMIT 1;
            END IF;

            IF v_actual_qty IS NULL OR v_actual_qty <= 0 THEN
                SET v_actual_qty = 1 + FLOOR(RAND() * 8);
            END IF;

            -- 噪声与区间
            IF v_algo = 'RF' THEN
                SET v_noise = (RAND() * 0.24) - 0.12;        -- ±12%
                SET v_predict = GREATEST(0.10, ROUND(v_actual_qty * (1 + v_noise), 2));
                SET v_lower = GREATEST(0.00, ROUND(v_predict * 0.82, 2)); -- ±18%
                SET v_upper = ROUND(v_predict * 1.18, 2);
                SET v_mase = ROUND(0.55 + RAND() * 0.40, 4);
            ELSEIF v_algo = 'SBA' THEN
                SET v_noise = (RAND() * 0.45) - 0.20;        -- -20%~+25%
                SET v_predict = GREATEST(0.10, ROUND(v_actual_qty * (1 + v_noise), 2));
                SET v_lower = GREATEST(0.00, ROUND(v_predict * 0.75, 2)); -- -25%
                SET v_upper = ROUND(v_predict * 1.35, 2);                -- +35%
                SET v_mase = ROUND(0.75 + RAND() * 0.50, 4);
            ELSE
                SET v_noise = (RAND() * 0.70) - 0.35;        -- ±35%
                SET v_predict = GREATEST(0.10, ROUND(v_actual_qty * (1 + v_noise), 2));
                SET v_lower = GREATEST(0.00, ROUND(v_predict * 0.60, 2)); -- ±40%
                SET v_upper = ROUND(v_predict * 1.40, 2);
                SET v_mase = NULL;
            END IF;

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
            ) VALUES (
                v_part_code,
                v_ym,
                v_predict,
                v_lower,
                v_upper,
                v_algo,
                v_mase,
                v_model_version,
                DATE_ADD(TIMESTAMP(LAST_DAY(v_month_start), '23:00:00'), INTERVAL FLOOR(RAND() * 120) MINUTE)
            );

            SET v_fseq = v_fseq + 1;
        END WHILE;

    END LOOP;

    CLOSE cur_month;

END$$

DELIMITER ;

-- -----------------------------
-- 2) 执行造数
-- -----------------------------
CALL sp_seed_ai_12m_medium_enterprise();

-- -----------------------------
-- 3) 获取本批 run_id
-- -----------------------------
SELECT run_id, model_version FROM tmp_seed_meta;

-- -----------------------------
-- 3.1) 补齐分类结果（确保热力矩阵有数据）
-- -----------------------------
SET @seed_classify_month = '2026-02';

INSERT INTO biz_part_classify (
    part_code, classify_month, abc_class, xyz_class,
    composite_score, annual_cost, adi, cv2,
    safety_stock, reorder_point, service_level, strategy_code, create_time
)
SELECT
    sp.code AS part_code,
    @seed_classify_month AS classify_month,
    CASE
        WHEN COALESCE(sp.is_critical, 0) = 1 OR COALESCE(sp.price, 0) >= 1000 THEN 'A'
        WHEN COALESCE(sp.price, 0) >= 200 THEN 'B'
        ELSE 'C'
    END AS abc_class,
    CASE MOD(CRC32(sp.code), 3)
        WHEN 0 THEN 'X'
        WHEN 1 THEN 'Y'
        ELSE 'Z'
    END AS xyz_class,
    ROUND(
        LEAST(
            100,
            (COALESCE(sp.price, 20) / 30)
            + (CASE WHEN COALESCE(sp.is_critical, 0) = 1 THEN 35 ELSE 10 END)
            + (COALESCE(sp.lead_time, 15) / 2)
        ),
        2
    ) AS composite_score,
    ROUND(COALESCE(sp.price, 20) * (6 + MOD(CRC32(CONCAT('A', sp.code)), 18)), 2) AS annual_cost,
    NULL AS adi,
    CASE MOD(CRC32(sp.code), 3)
        WHEN 0 THEN 0.3200
        WHEN 1 THEN 0.9200
        ELSE 1.8600
    END AS cv2,
    CASE
        WHEN COALESCE(sp.is_critical, 0) = 1 OR COALESCE(sp.price, 0) >= 1000 THEN 8
        WHEN COALESCE(sp.price, 0) >= 200 THEN 5
        ELSE 3
    END AS safety_stock,
    CASE
        WHEN COALESCE(sp.is_critical, 0) = 1 OR COALESCE(sp.price, 0) >= 1000 THEN 20
        WHEN COALESCE(sp.price, 0) >= 200 THEN 12
        ELSE 8
    END AS reorder_point,
    CASE
        WHEN COALESCE(sp.is_critical, 0) = 1 OR COALESCE(sp.price, 0) >= 1000 THEN 99.00
        WHEN COALESCE(sp.price, 0) >= 200 THEN 95.00
        ELSE 90.00
    END AS service_level,
    CONCAT(
        CASE
            WHEN COALESCE(sp.is_critical, 0) = 1 OR COALESCE(sp.price, 0) >= 1000 THEN 'A'
            WHEN COALESCE(sp.price, 0) >= 200 THEN 'B'
            ELSE 'C'
        END,
        CASE MOD(CRC32(sp.code), 3)
            WHEN 0 THEN 'X'
            WHEN 1 THEN 'Y'
            ELSE 'Z'
        END
    ) AS strategy_code,
    NOW() AS create_time
FROM spare_part sp;

-- -----------------------------
-- 3.2) 补齐补货建议（确保建议页和低库存预警有数据）
-- -----------------------------
SET @seed_suggest_month = '2099-12';

INSERT INTO biz_reorder_suggest (
    part_code, suggest_month, current_stock, reorder_point, suggest_qty,
    forecast_qty, lower_bound, upper_bound, urgency, status, created_at, updated_at
)
SELECT
    x.part_code,
    @seed_suggest_month,
    x.current_stock,
    x.reorder_point,
    x.suggest_qty,
    x.forecast_qty,
    ROUND(x.forecast_qty * 0.80, 2) AS lower_bound,
    ROUND(x.forecast_qty * 1.25, 2) AS upper_bound,
    CASE WHEN x.current_stock <= x.reorder_point - 8 THEN '紧急' ELSE '正常' END AS urgency,
    '待处理' AS status,
    NOW(),
    NOW()
FROM (
    SELECT
        sp.code AS part_code,
        COALESCE(ss.quantity, sp.quantity, 0) AS current_stock,
        COALESCE(ss.quantity, sp.quantity, 0) + 5 + MOD(CRC32(CONCAT('R', sp.code)), 12) AS reorder_point,
        8 + MOD(CRC32(CONCAT('Q', sp.code)), 18) AS suggest_qty,
        ROUND(6 + MOD(CRC32(CONCAT('F', sp.code)), 24), 2) AS forecast_qty
    FROM spare_part sp
    LEFT JOIN spare_part_stock ss ON ss.spare_part_id = sp.id
    ORDER BY COALESCE(sp.is_critical, 0) DESC, COALESCE(sp.price, 0) DESC
    LIMIT 30
) x;

-- -----------------------------
-- 3.3) 补齐逾期工单/采购（确保预警中心有数据）
-- -----------------------------
SET @seed_user_id = (SELECT id FROM `user` ORDER BY id LIMIT 1);
SET @seed_device_id = (SELECT id FROM equipment ORDER BY id LIMIT 1);
SET @seed_supplier_id = (SELECT id FROM supplier ORDER BY id LIMIT 1);
SET @seed_spare_part_id = (SELECT id FROM spare_part ORDER BY id LIMIT 1);

INSERT INTO biz_work_order (
    work_order_no, device_id, reporter_id, fault_desc, fault_level, order_status,
    assignee_id, plan_finish, report_time, created_at, updated_at
)
SELECT CONCAT('WO-ALERT-', DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), '-', t.n),
       @seed_device_id, @seed_user_id,
       CONCAT('预警演示工单-', t.n), '一般', '维修中',
       @seed_user_id, DATE_SUB(NOW(), INTERVAL (3 + t.n) DAY), DATE_SUB(NOW(), INTERVAL (8 + t.n) DAY), NOW(), NOW()
FROM (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3
) t
WHERE @seed_device_id IS NOT NULL AND @seed_user_id IS NOT NULL;

INSERT INTO biz_purchase_order (
    order_no, spare_part_id, supplier_id, order_qty, unit_price, total_amount, order_status,
    expected_date, actual_date, reorder_suggest_id, purchaser_id, remark, created_at, updated_at
)
SELECT CONCAT('PO-ALERT-', DATE_FORMAT(NOW(), '%y%m%d%H%i%s'), '-', t.n),
       @seed_spare_part_id, @seed_supplier_id,
       5 + t.n, 100.00 + t.n, (5 + t.n) * (100.00 + t.n), '已下单',
       DATE_SUB(CURDATE(), INTERVAL (5 + t.n) DAY), NULL, NULL, @seed_user_id, '预警演示采购单', NOW(), NOW()
FROM (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3
) t
WHERE @seed_spare_part_id IS NOT NULL AND @seed_supplier_id IS NOT NULL;

-- -----------------------------
-- 4) 校验SQL
-- -----------------------------
-- 4.1 月度计数：领用主表（本批次应每月=100）
SELECT DATE_FORMAT(created_at, '%Y-%m') AS month_key, COUNT(*) AS req_cnt
FROM biz_requisition
WHERE req_no LIKE CONCAT('REQ-SIM-', (SELECT run_id FROM tmp_seed_meta LIMIT 1), '-%')
GROUP BY DATE_FORMAT(created_at, '%Y-%m')
ORDER BY month_key;

-- 4.2 月度计数：预测表（本批次应每月=100）
SELECT forecast_month AS month_key, COUNT(*) AS forecast_cnt
FROM ai_forecast_result
WHERE model_version = (SELECT model_version FROM tmp_seed_meta LIMIT 1)
GROUP BY forecast_month
ORDER BY month_key;

-- 4.3 状态分布（本批总计应接近：68/17/8/4/3 每月固定）
SELECT req_status, COUNT(*) AS cnt
FROM biz_requisition
WHERE req_no LIKE CONCAT('REQ-SIM-', (SELECT run_id FROM tmp_seed_meta LIMIT 1), '-%')
GROUP BY req_status
ORDER BY cnt DESC;

-- 4.4 消耗有效性：OUTBOUND/INSTALLED 对应明细 out_qty 必须 >0
SELECT COUNT(*) AS invalid_out_qty_rows
FROM biz_requisition r
JOIN biz_requisition_item ri ON r.id = ri.req_id
WHERE r.req_no LIKE CONCAT('REQ-SIM-', (SELECT run_id FROM tmp_seed_meta LIMIT 1), '-%')
  AND r.req_status IN ('OUTBOUND', 'INSTALLED')
  AND (ri.out_qty IS NULL OR ri.out_qty <= 0);

-- 4.5 12个月消耗覆盖
SELECT COUNT(DISTINCT DATE_FORMAT(r.approve_time, '%Y-%m')) AS months_with_outbound
FROM biz_requisition r
JOIN biz_requisition_item ri ON r.id = ri.req_id
WHERE r.req_no LIKE CONCAT('REQ-SIM-', (SELECT run_id FROM tmp_seed_meta LIMIT 1), '-%')
  AND r.req_status IN ('OUTBOUND', 'INSTALLED')
  AND ri.out_qty > 0
  AND r.approve_time IS NOT NULL;

-- 4.6 AI趋势可视化友好性：按 part_code 跨月点数（Top10）
SELECT part_code, COUNT(DISTINCT forecast_month) AS month_points, COUNT(*) AS row_cnt
FROM ai_forecast_result
WHERE model_version = (SELECT model_version FROM tmp_seed_meta LIMIT 1)
GROUP BY part_code
ORDER BY month_points DESC, row_cnt DESC
LIMIT 10;

-- 4.7 热力矩阵覆盖校验（应返回 A/B/C 或含未分类）
SELECT classLevel, partCount, totalAmount
FROM (
    SELECT
        COALESCE(c.abc_class, '未分类') AS classLevel,
        COUNT(sp.id)                    AS partCount,
        COALESCE(SUM(sp.quantity * sp.price), 0) AS totalAmount
    FROM spare_part sp
    LEFT JOIN (
        SELECT c1.part_code, c1.abc_class
        FROM biz_part_classify c1
        INNER JOIN (
            SELECT part_code, MAX(id) max_id
            FROM biz_part_classify
            WHERE classify_month = (SELECT MAX(classify_month) FROM biz_part_classify)
            GROUP BY part_code
        ) m ON c1.id = m.max_id
    ) c ON sp.code = c.part_code
    GROUP BY COALESCE(c.abc_class, '未分类')
) t
ORDER BY totalAmount DESC;

-- 4.8 补货建议校验（应 >= 1）
SELECT COUNT(*) AS pending_suggest_count
FROM biz_reorder_suggest
WHERE status = '待处理';

-- 4.9 低库存预警命中校验（应 >= 1）
SELECT COUNT(*) AS low_stock_warning_count
FROM spare_part sp
JOIN (
    SELECT part_code, MIN(reorder_point) AS reorder_point
    FROM biz_reorder_suggest
    WHERE status = '待处理'
    GROUP BY part_code
) rs ON sp.code = rs.part_code
WHERE sp.quantity < rs.reorder_point;

-- 4.10 系统管理表零变更校验（应 diff=0）
SELECT
    b.table_name,
    b.cnt AS before_cnt,
    a.cnt AS after_cnt,
    a.cnt - b.cnt AS diff
FROM tmp_sys_counts_before b
JOIN (
    SELECT 'user' AS table_name, COUNT(*) AS cnt FROM `user`
    UNION ALL SELECT 'role', COUNT(*) FROM `role`
    UNION ALL SELECT 'menu', COUNT(*) FROM `menu`
    UNION ALL SELECT 'user_role', COUNT(*) FROM `user_role`
    UNION ALL SELECT 'role_menu', COUNT(*) FROM `role_menu`
) a ON a.table_name = b.table_name
ORDER BY b.table_name;

-- -----------------------------
-- 5) 回滚模板（仅回滚本批次）
-- -----------------------------
-- SET @rollback_run_id = '<替换为run_id，例如2603050951>';
-- SET @rollback_model_version = CONCAT('seed-mid-', @rollback_run_id);
--
-- DELETE ri
-- FROM biz_requisition_item ri
-- JOIN biz_requisition r ON r.id = ri.req_id
-- WHERE r.req_no LIKE CONCAT('REQ-SIM-', @rollback_run_id, '-%');
--
-- DELETE FROM biz_requisition
-- WHERE req_no LIKE CONCAT('REQ-SIM-', @rollback_run_id, '-%');
--
-- DELETE FROM ai_forecast_result
-- WHERE model_version = @rollback_model_version;

-- DELETE FROM biz_reorder_suggest
-- WHERE suggest_month = '2099-12';

-- DELETE FROM biz_work_order
-- WHERE work_order_no LIKE 'WO-ALERT-%';

-- DELETE FROM biz_purchase_order
-- WHERE order_no LIKE 'PO-ALERT-%';

DROP PROCEDURE IF EXISTS sp_seed_ai_12m_medium_enterprise;
