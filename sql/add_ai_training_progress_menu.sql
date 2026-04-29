-- 新增“AI训练进度”菜单
-- 说明：该页面只服务于周粒度预测训练，复用 ai:weekly:list 权限。
USE spare_db;

SET @ai_parent_id := (
    SELECT id
    FROM menu
    WHERE path = '/ai'
    ORDER BY id ASC
    LIMIT 1
);

INSERT INTO menu (parent_id, name, path, component, permission, type, icon, sort)
SELECT @ai_parent_id, 'AI训练进度', '/ai/training-progress', 'ai/AiTrainingProgress', 'ai:weekly:list', 2, 'el-icon-loading', 4
FROM DUAL
WHERE @ai_parent_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM menu WHERE path = '/ai/training-progress' AND type = 2
  );

SET @training_progress_menu_id := (
    SELECT id
    FROM menu
    WHERE path = '/ai/training-progress'
    ORDER BY id ASC
    LIMIT 1
);

UPDATE menu
SET parent_id = @ai_parent_id,
    name = 'AI训练进度',
    component = 'ai/AiTrainingProgress',
    permission = 'ai:weekly:list',
    type = 2,
    icon = 'el-icon-loading',
    sort = 4
WHERE id = @training_progress_menu_id
  AND @ai_parent_id IS NOT NULL;

-- 授权给所有已拥有“周粒度预测”访问权限的角色。
INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT rm.role_id, @training_progress_menu_id
FROM role_menu rm
JOIN menu weekly_menu ON weekly_menu.id = rm.menu_id
WHERE weekly_menu.path = '/ai/weekly-forecast'
  AND @training_progress_menu_id IS NOT NULL;

-- 兜底：管理员角色可见。
INSERT IGNORE INTO role_menu (role_id, menu_id)
SELECT 1, @training_progress_menu_id
FROM DUAL
WHERE @training_progress_menu_id IS NOT NULL;

SELECT 'AI training progress menu migration completed' AS status;
