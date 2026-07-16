-- 仓储管理模块子菜单补齐
-- 父菜单 id=12 path=/warehouse（init.sql 已存在）
-- 子菜单 path 统一走 /home/*，与现有 Vue Router 子路由一致

USE spare_db;

-- 清理可能存在的同 path 脏数据（幂等）
DELETE FROM role_menu
WHERE menu_id IN (
  SELECT id FROM (
    SELECT id FROM menu
    WHERE path IN ('/home/stock-in', '/home/stock-ledger', '/home/shelving')
       OR (parent_id = 12 AND type = 2)
  ) t
);

DELETE FROM menu
WHERE path IN ('/home/stock-in', '/home/stock-ledger', '/home/shelving')
   OR (parent_id = 12 AND type = 2);

INSERT INTO `menu` (`parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`) VALUES
(12, '收货入库', '/home/stock-in',      'warehouse/StockInManage',    'wh:stockin:list',    2, 'el-icon-download', 1),
(12, '货位上架', '/home/shelving',      'warehouse/LocationShelving', 'wh:shelving:list',   2, 'el-icon-place',    2),
(12, '库存台账', '/home/stock-ledger',  'warehouse/StockLedger',      'wh:ledger:list',     2, 'el-icon-notebook-2', 3);

INSERT INTO `role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `menu`
WHERE `path` IN ('/home/stock-in', '/home/shelving', '/home/stock-ledger');
