-- 论文实验回测菜单（幂等）
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
SELECT NULL, '需求预测与辅助决策模块', '/ai', 'Layout', NULL, 1, 'el-icon-cpu', 7
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
SELECT @ai_parent_id, '论文实验回测', '/ai/paper-experiments', 'ai/PaperExperimentReport', 'ai:forecast:list', 2, 'el-icon-document', 3
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM menu WHERE path = '/ai/paper-experiments' AND type = 2
);

SET @paper_menu_id := (
    SELECT id
    FROM menu
    WHERE path = '/ai/paper-experiments' AND type = 2
    ORDER BY id DESC
    LIMIT 1
);

UPDATE menu
SET
    parent_id = @ai_parent_id,
    name = '论文实验回测',
    component = 'ai/PaperExperimentReport',
    permission = 'ai:forecast:list',
    type = 2,
    icon = 'el-icon-document',
    sort = 3
WHERE id = @paper_menu_id;

INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT 1, id
FROM menu
WHERE path = '/ai' AND type = 1;

INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT 1, @paper_menu_id
FROM DUAL
WHERE @paper_menu_id IS NOT NULL;

COMMIT;

SELECT 'paper experiment menu migration completed' AS status, @paper_menu_id AS menu_id;
