-- 隐藏周粒度预测 / AI训练进度 / 训练数据看板菜单
-- 仅保留：需求预测结果、AI任务中心（两阶段 Hurdle-Gamma 主链路）
USE spare_db;

UPDATE menu
SET name = CONCAT('[隐藏]', REPLACE(name, '[隐藏]', '')),
    sort = 900 + IF(sort < 900, sort, 0)
WHERE path IN (
  '/ai/weekly-forecast',
  '/ai/training-progress',
  '/ai/train-data-dashboard'
)
AND name NOT LIKE '[隐藏]%';

DELETE rm FROM role_menu rm
JOIN menu m ON m.id = rm.menu_id
WHERE m.path IN (
  '/ai/weekly-forecast',
  '/ai/training-progress',
  '/ai/train-data-dashboard'
);

SELECT id, name, path, permission
FROM menu
WHERE path LIKE '/ai%'
ORDER BY sort, id;
