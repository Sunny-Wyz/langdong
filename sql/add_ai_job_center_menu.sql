-- Add AI Job Center menu (idempotent)
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
SELECT @ai_parent_id, 'AI任务中心', '/ai/job-center', 'ai/AiJobCenter', 'ai:forecast:list', 2, 'el-icon-s-operation', 2
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM menu WHERE path = '/ai/job-center' AND type = 2
);

SET @job_center_menu_id := (
    SELECT id
    FROM menu
    WHERE path = '/ai/job-center' AND type = 2
    ORDER BY id DESC
    LIMIT 1
);

UPDATE menu
SET
    parent_id = @ai_parent_id,
    name = 'AI任务中心',
    component = 'ai/AiJobCenter',
    permission = 'ai:forecast:list',
    type = 2,
    icon = 'el-icon-s-operation',
    sort = 2
WHERE id = @job_center_menu_id;

INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT 1, id
FROM menu
WHERE path = '/ai' AND type = 1;

INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT 1, @job_center_menu_id
FROM DUAL
WHERE @job_center_menu_id IS NOT NULL;

COMMIT;

SELECT 'AI job center menu migration completed' AS status;
