-- 备件智能分类模块 - 数据库迁移脚本
USE spare_db;

-- ================================================================
-- 1. 扩展备件档案表：新增分类计算所需字段
-- ================================================================
ALTER TABLE `spare_part`
    ADD COLUMN IF NOT EXISTS `is_critical`  tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否关键备件(1=关键，0=非关键)',
    ADD COLUMN IF NOT EXISTS `replace_diff` int        NOT NULL DEFAULT 3 COMMENT '供应替代难度(1=容易~5=极难)',
    ADD COLUMN IF NOT EXISTS `lead_time`    int        NOT NULL DEFAULT 30 COMMENT '采购提前期（天）';

-- ================================================================
-- 2. 新建备件分类结果表
-- ================================================================
CREATE TABLE IF NOT EXISTS `biz_part_classify` (
    `id`              bigint         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `part_code`       varchar(20)    NOT NULL COMMENT '备件编码',
    `classify_month`  varchar(7)     NOT NULL COMMENT '分类所属月份，格式yyyy-MM',
    `abc_class`       varchar(2)     NOT NULL COMMENT 'ABC分类结果(A/B/C)',
    `xyz_class`       varchar(2)     NOT NULL COMMENT 'XYZ分类结果(X/Y/Z)',
    `composite_score` decimal(5, 2)  NOT NULL DEFAULT 0 COMMENT 'ABC综合加权得分(0~100)',
    `annual_cost`     decimal(10, 2) NOT NULL DEFAULT 0 COMMENT '年消耗金额（元）',
    `adi`             decimal(8, 4)  DEFAULT NULL COMMENT '平均需求间隔ADI（暂存，扩展用）',
    `cv2`             decimal(8, 4)  NOT NULL DEFAULT 0 COMMENT '需求变异系数CV²',
    `safety_stock`    int            NOT NULL DEFAULT 0 COMMENT '安全库存SS（件）',
    `reorder_point`   int            NOT NULL DEFAULT 0 COMMENT '补货触发点ROP（件）',
    `service_level`   decimal(5, 2)  DEFAULT NULL COMMENT '目标服务水平（%）',
    `strategy_code`   varchar(10)    DEFAULT NULL COMMENT 'ABC×XYZ策略编码，如AX/BZ',
    `create_time`     datetime       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_part_code`      (`part_code`),
    KEY `idx_classify_month` (`classify_month`),
    KEY `idx_abc_xyz`        (`abc_class`, `xyz_class`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '备件ABC/XYZ分类结果表（每次重算插入新记录，保留历史）';

-- ================================================================
-- 3. 新增菜单项（父节点 id=11，即备件智能分类模块目录）
-- ================================================================
INSERT IGNORE INTO `menu` (`id`, `parent_id`, `name`, `path`, `component`, `permission`, `type`, `icon`, `sort`)
VALUES
    -- 分类结果列表页（菜单页面）
    (50, 11, '分类结果查询', '/smart/classify-result', 'classify/ClassifyResult', 'classify:result:list', 2, 'el-icon-data-analysis', 1),
    -- 手动触发重算（按钮权限，仅ADMIN可用）
    (51, 50, '手动触发重算(按钮)', NULL, NULL, 'classify:trigger:run', 3, NULL, 1);

-- ================================================================
-- 4. 将新菜单授权给超级管理员角色 (role_id=1)
-- ================================================================
INSERT IGNORE INTO `role_menu` (`role_id`, `menu_id`)
VALUES (1, 50), (1, 51);
