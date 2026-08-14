-- 隐藏 论文实验回测 菜单
USE spare_db;

START TRANSACTION;

UPDATE menu
SET name = CONCAT('[隐藏]', REPLACE(name, '[隐藏]', '')),
    sort = 900 + IF(sort < 900, sort, 0)
WHERE path = '/ai/paper-experiments'
AND name NOT LIKE '[隐藏]%';

DELETE rm FROM role_menu rm
JOIN menu m ON m.id = rm.menu_id
WHERE m.path = '/ai/paper-experiments';

COMMIT;

SELECT id, name, path, permission
FROM menu
WHERE path LIKE '/ai%'
ORDER BY sort, id;
