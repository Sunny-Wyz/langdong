-- 品类字典与供应商供货品类对齐脚本
-- 执行顺序：Step1 → Step2 → Step3 → Step4 → Step5
SET NAMES utf8mb4;
USE spare_db;
SET FOREIGN_KEY_CHECKS = 0;

-- ================================================================
-- Step 1: 修正 spare_part_category 名称（原地 UPDATE，不改 ID）
-- ================================================================
UPDATE spare_part_category SET name = '包装耗材'     WHERE id = 1;
UPDATE spare_part_category SET name = '电气控制'     WHERE id = 2;
UPDATE spare_part_category SET name = '传动部件'     WHERE id = 3;
UPDATE spare_part_category SET name = '气动配件'     WHERE id = 4;
UPDATE spare_part_category SET name = '管路配件'     WHERE id = 5;
UPDATE spare_part_category SET name = '检验量具'     WHERE id = 6;
UPDATE spare_part_category SET name = '传感检测'     WHERE id = 7;
UPDATE spare_part_category SET name = '不锈钢紧固件' WHERE id = 8;
UPDATE spare_part_category SET name = '密封件'       WHERE id = 9;
UPDATE spare_part_category SET name = '轴承类'       WHERE id = 10;

-- ================================================================
-- Step 2: 清空旧关联（必须在 TRUNCATE supply_category 之前）
-- ================================================================
DELETE FROM supplier_category_relation;

-- ================================================================
-- Step 3: 清空旧 supply_category 脏数据，重置自增
-- ================================================================
TRUNCATE TABLE supply_category;

-- ================================================================
-- Step 4: 插入10条标准供货品类标签（与 spare_part_category 对应）
-- ================================================================
INSERT INTO supply_category (code, name, description) VALUES
('CAT-01', '包装耗材',     '包装线消耗品，包括旋盖头、扎带、胶布、食品级润滑脂等'),
('CAT-02', '电气控制',     '变频器、PLC模块、断路器、按钮开关、指示灯、继电器、开关电源等'),
('CAT-03', '传动部件',     '减速机、同步带、联轴器、直线导轨滑块等机械传动元件'),
('CAT-04', '气动配件',     'SMC气缸、电磁阀、气源处理器、油压缓冲器及空压机配件等'),
('CAT-05', '管路配件',     '不锈钢离心泵、食品级硅胶软管、止回阀等管路元件'),
('CAT-06', '检验量具',     '数显游标卡尺、外径千分尺、数字万用表及灌装阀硬质合金件等'),
('CAT-07', '传感检测',     '光电传感器、接近开关、磁性开关、铂电阻温度传感器、光栅传感器等'),
('CAT-08', '不锈钢紧固件', '304不锈钢内六角螺钉、弹簧垫圈、自攻螺丝、膨胀螺栓等'),
('CAT-09', '密封件',       '食品级硅胶O型圈、骨架油封、紫铜垫圈等密封元件'),
('CAT-10', '轴承类',       'SKF/NSK深沟球轴承、圆柱滚子轴承、推力球轴承等');

-- ================================================================
-- Step 5: 按供应商实际主营业务建立关联（使用子查询引用 code）
-- ================================================================

-- 西门子(中国)有限公司 ID=1 → 电气控制、传感检测
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 1, id FROM supply_category WHERE code IN ('CAT-02', 'CAT-07');

-- ABB电气有限公司 ID=2 → 电气控制、传动部件（变频器/电机）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 2, id FROM supply_category WHERE code IN ('CAT-02', 'CAT-03');

-- 施耐德电气(中国)有限公司 ID=3 → 电气控制
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 3, id FROM supply_category WHERE code IN ('CAT-02');

-- NSK(中国)销售有限公司 ID=4 → 轴承类、密封件
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 4, id FROM supply_category WHERE code IN ('CAT-10', 'CAT-09');

-- SMC(中国)有限公司 ID=5 → 气动配件、密封件
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 5, id FROM supply_category WHERE code IN ('CAT-04', 'CAT-09');

-- 欧姆龙自动化(中国)有限公司 ID=6 → 传感检测
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 6, id FROM supply_category WHERE code IN ('CAT-07');

-- 基恩士(中国)有限公司 ID=7 → 传感检测
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 7, id FROM supply_category WHERE code IN ('CAT-07');

-- 阿特拉斯·科普柯(中国) ID=8 → 气动配件、管路配件（空压机滤芯/阀件）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 8, id FROM supply_category WHERE code IN ('CAT-04', 'CAT-05');

-- 新美星(江苏)有限公司 ID=9 → 包装耗材、密封件、检验量具（灌装设备配件）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 9, id FROM supply_category WHERE code IN ('CAT-01', 'CAT-09', 'CAT-06');

-- 永创智能装备股份有限公司 ID=10 → 包装耗材、传动部件（旋盖机配件）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 10, id FROM supply_category WHERE code IN ('CAT-01', 'CAT-03');

-- SKF(中国)销售有限公司 ID=11 → 轴承类、密封件、包装耗材（润滑脂）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 11, id FROM supply_category WHERE code IN ('CAT-10', 'CAT-09', 'CAT-01');

-- THK(中国)有限公司 ID=12 → 传动部件（直线导轨）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 12, id FROM supply_category WHERE code IN ('CAT-03');

-- SEW传动设备(天津)有限公司 ID=13 → 传动部件（减速机/联轴器）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 13, id FROM supply_category WHERE code IN ('CAT-03');

-- 南方泵业股份有限公司 ID=14 → 管路配件（离心泵/阀件）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 14, id FROM supply_category WHERE code IN ('CAT-05');

-- 浙江正泰电器股份有限公司 ID=15 → 电气控制、不锈钢紧固件（通用五金）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 15, id FROM supply_category WHERE code IN ('CAT-02', 'CAT-08');

-- 明纬(广州)电子有限公司 ID=16 → 电气控制（开关电源）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 16, id FROM supply_category WHERE code IN ('CAT-02');

-- 盖茨优霓塔传动系统 ID=17 → 传动部件（同步带）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 17, id FROM supply_category WHERE code IN ('CAT-03');

-- 福禄克测试仪器(上海) ID=18 → 检验量具（万用表/测试仪器）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 18, id FROM supply_category WHERE code IN ('CAT-06');

-- 横河电机(中国)有限公司 ID=19 → 传感检测（温度传感器）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 19, id FROM supply_category WHERE code IN ('CAT-07');

-- 三丰精密量仪(上海) ID=20 → 检验量具（卡尺/千分尺）
INSERT INTO supplier_category_relation (supplier_id, supply_category_id)
SELECT 20, id FROM supply_category WHERE code IN ('CAT-06');

SET FOREIGN_KEY_CHECKS = 1;
-- 对齐完毕
