USE spare_db;
-- Clear old dummy menus for parent 13, and any previous failed inserts of 51-55
DELETE FROM `role_menu` WHERE menu_id IN (SELECT id FROM `menu` WHERE parent_id = 13);
DELETE FROM `menu` WHERE parent_id = 13;

INSERT INTO `menu` (`id`, `parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`) VALUES
(51, 13, '发起领用申请', '/home/requisition-apply',    'requisition/RequisitionApply',    'req:apply:add',      2, 'el-icon-plus',               1),
(52, 13, '审批领用申请', '/home/requisition-approval',  'requisition/RequisitionApproval', 'req:approve:list',   2, 'el-icon-s-check',            2),
(53, 13, '出库确认',     '/home/requisition-outbound',  'requisition/RequisitionOutbound', 'req:outbound:confirm', 2, 'el-icon-sold-out',         3),
(54, 13, '安装登记',     '/home/requisition-install',   'requisition/RequisitionInstall',  'req:install:edit',   2, 'el-icon-s-opportunity',      4),
(55, 13, '查询领用记录', '/home/requisition-query',     'requisition/RequisitionQuery',    'req:record:list',    2, 'el-icon-search',             5);

INSERT INTO `role_menu` (`role_id`, `menu_id`) VALUES
(1, 51), (1, 52), (1, 53), (1, 54), (1, 55);
