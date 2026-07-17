-- 采购订单增加累计已入库数量，支撑分批收货与收货门禁
USE spare_db;

SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'biz_purchase_order'
    AND COLUMN_NAME = 'received_qty'
);
SET @sql := IF(@col = 0,
  'ALTER TABLE biz_purchase_order ADD COLUMN received_qty INT NOT NULL DEFAULT 0 COMMENT ''累计已入库数量'' AFTER order_qty',
  'SELECT ''received_qty already exists'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
