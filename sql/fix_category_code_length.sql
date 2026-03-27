-- 修复分类编码长度约束
-- 背景：spare_part_category.code 定义为 VARCHAR(4)，但未强制恰好4位，
--       导致3位前缀生成7位备件编码（如 SP20001），破坏8位编码体系。
-- 修复内容：
--   1. 将 spare_part 中50条7位 SP2xxxx 编码重编为正确的8位编码（分类前缀+4位序列号）
--   2. 同步更新 biz_part_classify、ai_forecast_result、biz_reorder_suggest 中的关联编码
--   3. 在 spare_part_category 上添加 CHECK 约束防止未来脏数据

USE spare_db;

-- ================================================================
-- 第一步：生成编码映射表（旧7位 → 新8位）
-- ================================================================

DROP TABLE IF EXISTS _code_migration_map;

CREATE TABLE _code_migration_map (
    old_code VARCHAR(8) NOT NULL,
    new_code VARCHAR(8) NOT NULL,
    PRIMARY KEY (old_code),
    UNIQUE KEY uk_new (new_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用存储过程按分类分组、依序生成新编码
DELIMITER //
CREATE PROCEDURE _migrate_codes()
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE v_part_id BIGINT;
    DECLARE v_old_code VARCHAR(8);
    DECLARE v_cat_code VARCHAR(4);
    DECLARE v_max_code VARCHAR(8);
    DECLARE v_next_num INT;

    DECLARE v_last_cat VARCHAR(4) DEFAULT '';

    DECLARE cur CURSOR FOR
        SELECT sp.id, sp.code, c.code
        FROM spare_part sp
        JOIN spare_part_category c ON sp.category_id = c.id
        WHERE CHAR_LENGTH(sp.code) = 7
        ORDER BY c.code, sp.id;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_part_id, v_old_code, v_cat_code;
        IF done THEN
            LEAVE read_loop;
        END IF;

        -- 每个新分类：从该分类已有最大8位编码之后开始
        IF v_cat_code != v_last_cat THEN
            SET v_last_cat = v_cat_code;

            -- 优先看映射表中该分类已分配的最大编码
            SELECT MAX(new_code) INTO v_max_code
            FROM _code_migration_map
            WHERE new_code LIKE CONCAT(v_cat_code, '%');

            IF v_max_code IS NULL THEN
                -- 再看 spare_part 中已有的8位编码
                SELECT MAX(code) INTO v_max_code
                FROM spare_part
                WHERE code LIKE CONCAT(v_cat_code, '%') AND CHAR_LENGTH(code) = 8;
            END IF;

            IF v_max_code IS NOT NULL AND CHAR_LENGTH(v_max_code) = 8 THEN
                SET v_next_num = CAST(SUBSTRING(v_max_code, 5) AS UNSIGNED) + 1;
            ELSE
                SET v_next_num = 1;
            END IF;
        END IF;

        INSERT INTO _code_migration_map (old_code, new_code)
        VALUES (v_old_code, CONCAT(v_cat_code, LPAD(v_next_num, 4, '0')));

        SET v_next_num = v_next_num + 1;
    END LOOP;

    CLOSE cur;
END //
DELIMITER ;

CALL _migrate_codes();
DROP PROCEDURE IF EXISTS _migrate_codes;

-- 确认映射结果
SELECT m.old_code, m.new_code, sp.name
FROM _code_migration_map m
JOIN spare_part sp ON sp.code = m.old_code
ORDER BY m.new_code;

-- ================================================================
-- 第二步：在事务中统一更新所有表
-- ================================================================
START TRANSACTION;

-- 2a. 更新 ai_forecast_result
UPDATE ai_forecast_result f
INNER JOIN _code_migration_map m ON f.part_code = m.old_code
SET f.part_code = m.new_code;

-- 2b. 更新 biz_part_classify
UPDATE biz_part_classify pc
INNER JOIN _code_migration_map m ON pc.part_code = m.old_code
SET pc.part_code = m.new_code;

-- 2c. 更新 biz_reorder_suggest
UPDATE biz_reorder_suggest rs
INNER JOIN _code_migration_map m ON rs.part_code = m.old_code
SET rs.part_code = m.new_code;

-- 2d. 最后更新 spare_part 主表
UPDATE spare_part sp
INNER JOIN _code_migration_map m ON sp.code = m.old_code
SET sp.code = m.new_code;

COMMIT;

-- 清理临时表
DROP TABLE IF EXISTS _code_migration_map;

-- ================================================================
-- 第三步：验证
-- ================================================================

-- 确认不再有非8位编码
SELECT COUNT(*) AS remaining_bad_codes
FROM spare_part
WHERE CHAR_LENGTH(code) != 8;

-- ================================================================
-- 第四步：添加 CHECK 约束（MySQL 8.0.16+ 才支持生效的 CHECK）
-- MySQL 5.7 会解析但不强制执行 CHECK，仍通过后端代码保障
-- ================================================================
ALTER TABLE spare_part_category
    ADD CONSTRAINT chk_category_code_len CHECK (CHAR_LENGTH(code) = 4);
