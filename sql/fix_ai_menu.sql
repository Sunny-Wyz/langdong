USE spare_db;
-- Move children of the duplicate AI menu (73) to the original AI menu (16)
UPDATE menu SET parent_id = 16 WHERE parent_id = 73;
-- Delete the duplicate AI menu
DELETE FROM menu WHERE id = 73;
-- Delete the placeholder "测试模块" under AI menu
DELETE FROM menu WHERE id = 39;
-- Ensure Admin role has access to the AI menu items
INSERT IGNORE INTO role_menu (role_id, menu_id) 
SELECT 1, id FROM menu WHERE path = '/ai/forecast-result' OR parent_id IN (SELECT id FROM menu WHERE path = '/ai/forecast-result');
