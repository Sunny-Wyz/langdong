-- =============================================================
-- FIFO 出库功能数据库迁移脚本 V1
-- 说明：
--   1. 为 stock_in_item 表添加 FIFO 必需字段（in_time, remaining_qty）
--   2. 创建出库-批次追溯表（biz_outbound_batch_trace）
--   3. 为 biz_requisition_item 表添加批次信息摘要字段
--   4. 迁移旧数据，确保 FIFO 功能正常启用
-- 执行前提：
--   系统已完成基础模块初始化（init.sql, requisition_module.sql 等）
-- 执行方式：
--   mysql -u root -p spare_db < sql/fifo_migration_v1.sql
-- =============================================================

USE spare_db;

-- =============================================================
-- 第一部分：扩展 stock_in_item 表（FIFO 核心字段）
-- =============================================================

-- 1.1 添加 FIFO 必需字段
ALTER TABLE `stock_in_item`
ADD COLUMN `in_time` DATETIME DEFAULT NULL COMMENT '入库时间（FIFO排序依据，按此字段升序取批次）' AFTER `location_id`,
ADD COLUMN `remaining_qty` INT NOT NULL DEFAULT 0 COMMENT '批次剩余可用数量（每次出库后递减）' AFTER `actual_quantity`;

-- 1.2 添加 FIFO 查询索引（提升按备件ID+入库时间查询性能）
ALTER TABLE `stock_in_item`
ADD INDEX `idx_spare_part_fifo` (`spare_part_id`, `in_time`, `remaining_qty`);

-- 1.3 数据迁移：为已有记录填充默认值
-- 说明：
--   - in_time 使用对应 stock_in_receipt 的 receipt_date（入库单时间）
--   - remaining_qty 初始值等于 actual_quantity（实际收货数量）
--   - 如果找不到 receipt_date，使用当前时间作为兜底
UPDATE `stock_in_item` si
LEFT JOIN `stock_in_receipt` sr ON si.stock_in_receipt_id = sr.id
SET
    si.in_time = COALESCE(sr.receipt_date, sr.created_at, NOW()),
    si.remaining_qty = si.actual_quantity
WHERE si.in_time IS NULL;

-- =============================================================
-- 第二部分：创建出库-批次追溯表（biz_outbound_batch_trace）
-- =============================================================

-- 2.1 创建追溯表
-- 说明：记录每次出库从哪些入库批次扣减了多少数量，实现完整的批次追溯能力
CREATE TABLE IF NOT EXISTS `biz_outbound_batch_trace` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `req_item_id`       BIGINT       NOT NULL COMMENT '领用明细ID（关联 biz_requisition_item.id）',
    `stock_in_item_id`  BIGINT       NOT NULL COMMENT '入库批次ID（关联 stock_in_item.id）',
    `spare_part_id`     BIGINT       NOT NULL COMMENT '备件ID（冗余字段，便于按备件维度查询追溯）',
    `deduct_qty`        INT          NOT NULL COMMENT '从该批次扣减的数量',
    `outbound_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '出库时间',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_req_item`       (`req_item_id`),
    KEY `idx_stock_in_batch` (`stock_in_item_id`),
    KEY `idx_spare_part`     (`spare_part_id`),
    KEY `idx_outbound_time`  (`outbound_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库-批次追溯表（FIFO 记录表）';

-- =============================================================
-- 第三部分：扩展 biz_requisition_item 表（批次信息摘要）
-- =============================================================

-- 3.1 添加批次信息摘要字段
-- 说明：用于在领用明细中快速显示批次分配情况（如：IN20240101[10件] + IN20240102[5件]）
ALTER TABLE `biz_requisition_item`
ADD COLUMN `batch_info` VARCHAR(500) DEFAULT NULL COMMENT '批次分配信息摘要（格式：入库单号[数量] + 入库单号[数量]）' AFTER `out_qty`;

-- =============================================================
-- 第四部分：数据一致性校验（可选，用于迁移后验证）
-- =============================================================

-- 校验脚本1：检查批次剩余量总和是否等于总库存
-- 说明：理论上，SUM(stock_in_item.remaining_qty) 应等于 spare_part_stock.quantity
-- 执行方式：
--   SELECT sp.code, sp.name,
--          sps.quantity as total_stock,
--          COALESCE(SUM(si.remaining_qty), 0) as batch_sum,
--          sps.quantity - COALESCE(SUM(si.remaining_qty), 0) as diff
--   FROM spare_part sp
--   LEFT JOIN spare_part_stock sps ON sp.id = sps.spare_part_id
--   LEFT JOIN stock_in_item si ON sp.id = si.spare_part_id
--   GROUP BY sp.id
--   HAVING diff != 0;
-- 预期结果：返回空（无差异）

-- 校验脚本2：检查是否所有 stock_in_item 记录都有 in_time 和 remaining_qty
-- 执行方式：
--   SELECT COUNT(*) as invalid_count
--   FROM stock_in_item
--   WHERE in_time IS NULL OR remaining_qty IS NULL;
-- 预期结果：invalid_count = 0

-- =============================================================
-- 第五部分：索引优化建议（可选，根据实际数据量决定）
-- =============================================================

-- 如果 stock_in_item 表数据量超过 10 万条，建议添加以下索引：
-- ALTER TABLE `stock_in_item` ADD INDEX `idx_in_time` (`in_time`);
-- ALTER TABLE `biz_outbound_batch_trace` ADD INDEX `idx_spare_outbound` (`spare_part_id`, `outbound_time`);

-- =============================================================
-- 迁移完成提示
-- =============================================================
SELECT '=== FIFO 出库功能数据库迁移完成 ===' as status;
SELECT 'stock_in_item 表已扩展：in_time, remaining_qty' as step1;
SELECT 'biz_outbound_batch_trace 表已创建' as step2;
SELECT 'biz_requisition_item 表已扩展：batch_info' as step3;
SELECT '旧数据已迁移完成' as step4;
SELECT '请执行校验脚本确认数据一致性' as step5;
