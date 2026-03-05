USE spare_db;

-- 删除“备件智能分类模块”下历史遗留的测试占位菜单
DELETE FROM role_menu
WHERE menu_id IN (
    SELECT id FROM menu
    WHERE parent_id = 11
      AND (
          path IS NULL
          OR path = '/test-11'
          OR name LIKE '%测试模块%'
      )
);

DELETE FROM menu
WHERE parent_id = 11
  AND (
      path IS NULL
      OR path = '/test-11'
      OR name LIKE '%测试模块%'
  );

-- 确保正式菜单存在并授权给管理员
INSERT IGNORE INTO menu
    (id, parent_id, name, path, component, permission, type, icon, sort)
VALUES
    (50, 11, '分类结果查询', '/smart/classify-result', 'classify/ClassifyResult', 'classify:result:list', 2, 'el-icon-data-analysis', 1),
    (51, 50, '手动触发重算(按钮)', NULL, NULL, 'classify:trigger:run', 3, NULL, 1);

INSERT IGNORE INTO role_menu (role_id, menu_id)
VALUES (1, 50), (1, 51);

-- 去重：仅保留 id=50（分类结果查询）及其按钮 id=51
CREATE TEMPORARY TABLE tmp_classify_menu_cleanup_ids (
    id BIGINT PRIMARY KEY
);

INSERT IGNORE INTO tmp_classify_menu_cleanup_ids (id)
SELECT id
FROM menu
WHERE parent_id = 11
  AND path = '/smart/classify-result'
  AND id <> 50;

INSERT IGNORE INTO tmp_classify_menu_cleanup_ids (id)
SELECT id
FROM menu
WHERE parent_id IN (
    SELECT id FROM (
        SELECT id
        FROM menu
        WHERE parent_id = 11
          AND path = '/smart/classify-result'
          AND id <> 50
    ) t
);

DELETE FROM role_menu
WHERE menu_id IN (SELECT id FROM tmp_classify_menu_cleanup_ids);

DELETE FROM menu
WHERE id IN (SELECT id FROM tmp_classify_menu_cleanup_ids);

DROP TEMPORARY TABLE tmp_classify_menu_cleanup_ids;
