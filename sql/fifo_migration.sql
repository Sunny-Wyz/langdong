-- FIFO库存管理迁移脚本
-- 在 stock_in_item 表中增加 remaining_qty 字段，用于追踪每批次的FIFO剩余数量
-- 执行时间：2026-03-17

ALTER TABLE stock_in_item
    ADD COLUMN remaining_qty INT NOT NULL DEFAULT 0 COMMENT 'FIFO剩余数量（出库时按批次消耗）'
    AFTER actual_quantity;

-- 初始化存量数据：将现有批次的剩余量设为实收数量
UPDATE stock_in_item SET remaining_qty = actual_quantity WHERE remaining_qty = 0;
