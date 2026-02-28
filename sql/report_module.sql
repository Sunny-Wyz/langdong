-- 报表与看板模块 M8 数据库迁移脚本
-- MySQL 5.7 兼容
USE spare_db;

-- ================================================================
-- 1. 清理旧占位菜单（path=NULL 的空条目）
-- ================================================================
DELETE FROM role_menu WHERE menu_id IN (SELECT id FROM menu WHERE parent_id = 17 AND path IS NULL);
DELETE FROM menu WHERE parent_id = 17 AND path IS NULL;
DELETE FROM role_menu WHERE menu_id IN (SELECT id FROM menu WHERE parent_id = 17 AND path = '/test-17');
DELETE FROM menu WHERE parent_id = 17 AND path = '/test-17';

-- ================================================================
-- 2. 插入 6 个子菜单（auto-increment，不指定 id）
-- ================================================================
INSERT INTO `menu` (`parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`) VALUES
(17, '管理层看板',     '/home/report-dashboard',    'report/Dashboard',           'report:dashboard:view',    2, 'el-icon-data-analysis',  1),
(17, '库存分析报告',     '/home/report-inventory',    'report/InventoryReport',     'report:inventory:view',    2, 'el-icon-pie-chart',       2),
(17, '备件消耗趋势',     '/home/report-consumption',  'report/ConsumptionReport',   'report:consumption:view',  2, 'el-icon-trend-charts',    3),
(17, '供应商绩效报告',   '/home/report-supplier',     'report/SupplierReport',      'report:supplier:view',     2, 'el-icon-office-building', 4),
(17, '维修费用分析',     '/home/report-maintenance',  'report/MaintenanceReport',   'report:maintenance:view',  2, 'el-icon-money',           5),
(17, '预警任务中心',     '/home/warning-center',      'report/WarningCenter',       'report:warning:view',      2, 'el-icon-warning-outline', 6);

-- 授权给超级管理员（role_id=1）
INSERT INTO `role_menu` (`role_id`, `menu_id`)
SELECT 1, id FROM `menu` WHERE `path` IN (
    '/home/report-dashboard',
    '/home/report-inventory',
    '/home/report-consumption',
    '/home/report-supplier',
    '/home/report-maintenance',
    '/home/warning-center'
);
