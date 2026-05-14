USE spare_db;
-- Find the "需求预测与辅助决策模块" menu (id 16) and its newly assigned children
INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT 1, id FROM menu WHERE path LIKE '/ai%' OR permission LIKE 'ai:%';
