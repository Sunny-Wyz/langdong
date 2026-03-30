-- Add AI train-data dashboard menu (idempotent)
USE spare_db;

START TRANSACTION;

SET @ai_parent_id := (
    SELECT id
    FROM menu
    WHERE path = '/ai' AND type = 1
    ORDER BY id DESC
    LIMIT 1
);

INSERT INTO menu (parent_id, name, path, component, permission, type, icon, sort)
SELECT NULL, 'AI智能分析模块', '/ai', 'Layout', NULL, 1, 'el-icon-cpu', 7
FROM DUAL
WHERE @ai_parent_id IS NULL;

SET @ai_parent_id := (
    SELECT id
    FROM menu
    WHERE path = '/ai' AND type = 1
    ORDER BY id DESC
    LIMIT 1
);

INSERT INTO menu (parent_id, name, path, component, permission, type, icon, sort)
SELECT @ai_parent_id, '训练数据看板', '/ai/train-data-dashboard', 'ai/AiTrainDataDashboard', 'ai:train-data:list', 2, 'el-icon-data-analysis', 3
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM menu WHERE path = '/ai/train-data-dashboard' AND type = 2
);

SET @train_data_menu_id := (
    SELECT id
    FROM menu
    WHERE path = '/ai/train-data-dashboard' AND type = 2
    ORDER BY id DESC
    LIMIT 1
);

UPDATE menu
SET
    parent_id = @ai_parent_id,
    name = '训练数据看板',
    component = 'ai/AiTrainDataDashboard',
    permission = 'ai:train-data:list',
    type = 2,
    icon = 'el-icon-data-analysis',
    sort = 3
WHERE id = @train_data_menu_id;

INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT 1, @train_data_menu_id
FROM DUAL
WHERE @train_data_menu_id IS NOT NULL;

COMMIT;

SELECT 'AI train-data dashboard menu migration completed' AS status;
