-- =============================================================
-- 备件智能分类模块 - 分类结果测试数据
-- 说明：
--   1. 为50个备件（SP20001~SP20050）插入 2026-02 分类结果
--   2. 9格矩阵分布（AX:4, AY:4, AZ:2, BX:7, BY:5, BZ:3, CX:9, CY:8, CZ:8）
--   3. 同时插入 2025-12 历史数据，供趋势对比
-- 执行前提：
--   mysql -u root -p spare_db < sql/mock_data.sql  已执行
-- 执行方式：
--   mysql -u root -p spare_db < sql/classify_data.sql
-- =============================================================

USE spare_db;

-- 清空旧分类数据，重新插入
TRUNCATE TABLE biz_part_classify;

-- =============================================================
-- 2026-02 分类结果（最新月，热力矩阵展示此月数据）
-- 字段顺序: part_code, classify_month, abc_class, xyz_class,
--           composite_score, annual_cost, adi, cv2,
--           safety_stock, reorder_point, service_level, strategy_code
-- ABC分类：A类≥60分，B类30~60分，C类<30分
-- XYZ分类：X类CV²<0.5，Y类0.5≤CV²<1.5，Z类CV²≥1.5
-- service_level: A类99.0%, B类95.0%, C类90.0%
-- =============================================================

-- ---- A × X（4件）：高价值 + 需求稳定 ----
-- SP20007 伺服电机（2800元/台，年消耗3台，关键，提前期60天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20007','2026-02','A','X',92.30, 8400.00,0.1800,1,3,99.00,'AX');

-- SP20011 PLC数字量输入模块（1450元，年消耗4个，关键，提前期45天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20011','2026-02','A','X',82.10, 5800.00,0.2400,2,4,99.00,'AX');

-- SP20002 交流变频器（1200元，年消耗4台，关键，提前期45天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20002','2026-02','A','X',85.50, 4800.00,0.1200,2,4,99.00,'AX');

-- SP20019 工业以太网交换机（650元，年消耗5个，关键，提前期40天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20019','2026-02','A','X',75.40, 3250.00,0.3100,2,3,99.00,'AX');

-- ---- A × Y（4件）：高价值 + 需求波动 ----
-- SP20033 光栅尺（850元，年消耗3个，关键，提前期45天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20033','2026-02','A','Y',88.60, 2550.00,1.1200,2,4,99.00,'AY');

-- SP20017 液压齿轮泵（280元，年消耗6个，关键，提前期30天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20017','2026-02','A','Y',80.20, 1680.00,0.7300,3,5,99.00,'AY');

-- SP20015 两位五通电磁阀（210元，年消耗12个，关键，提前期20天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20015','2026-02','A','Y',78.50, 2520.00,0.6200,5,9,99.00,'AY');

-- SP20003 微型光电传感器（280元，年消耗10个，关键，提前期20天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20003','2026-02','A','Y',72.80, 2800.00,0.8500,4,7,99.00,'AY');

-- ---- A × Z（2件）：高价值 + 需求随机 ----
-- SP20028 蜗轮蜗杆减速机（650元，年消耗2个，关键，提前期45天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20028','2026-02','A','Z',77.40, 1300.00,3.8200,2,3,99.00,'AZ');

-- SP20009 直线导轨滑块（110元，年消耗8个，关键，提前期25天）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20009','2026-02','A','Z',71.30,  880.00,2.1500,3,4,99.00,'AZ');

-- ---- B × X（7件）：中等价值 + 需求稳定 ----
-- SP20012 圆柱滚子轴承（180元，年消耗10个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20012','2026-02','B','X',52.40, 1800.00,0.3500,4,8,95.00,'BX');

-- SP20010 接触器（85元，年消耗24个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20010','2026-02','B','X',48.60, 2040.00,0.1800,6,12,95.00,'BX');

-- SP20001 深沟球轴承（45.5元，年消耗30个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20001','2026-02','B','X',55.20, 1365.00,0.2200,8,14,95.00,'BX');

-- SP20006 微型断路器（38.5元，年消耗30个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20006','2026-02','B','X',40.30, 1155.00,0.2800,8,16,95.00,'BX');

-- SP20025 中间继电器（25元，年消耗36个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20025','2026-02','B','X',38.10,  900.00,0.4200,10,18,95.00,'BX');

-- SP20026 同步带（35元，年消耗20个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20026','2026-02','B','X',45.80,  700.00,0.3000,5,10,95.00,'BX');

-- SP20013 O型密封圈（8.5元，年消耗80个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20013','2026-02','B','X',42.70,  680.00,0.2500,15,30,95.00,'BX');

-- ---- B × Y（5件）：中等价值 + 需求波动 ----
-- SP20004 气动薄型气缸（135元，年消耗15个，关键）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20004','2026-02','B','Y',56.30, 2025.00,0.7200,5,9,95.00,'BY');

-- SP20021 开关电源（240元，年消耗8个，关键）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20021','2026-02','B','Y',53.20, 1920.00,0.6500,3,6,95.00,'BY');

-- SP20023 气源处理器（180元，年消耗12个，关键）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20023','2026-02','B','Y',51.70, 2160.00,0.9500,4,8,95.00,'BY');

-- SP20018 单向阀（85元，年消耗18个，关键）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20018','2026-02','B','Y',47.90, 1530.00,0.8800,6,10,95.00,'BY');

-- SP20024 磁性开关（45元，年消耗24个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20024','2026-02','B','Y',44.50, 1080.00,0.7800,7,12,95.00,'BY');

-- ---- B × Z（3件）：中等价值 + 需求随机 ----
-- SP20005 接近开关（65元，年消耗18个，关键）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20005','2026-02','B','Z',46.20, 1170.00,1.9500,5,8,95.00,'BZ');

-- SP20020 铂电阻温度传感器（120元，年消耗8个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20020','2026-02','B','Z',41.80,  960.00,2.3200,3,5,95.00,'BZ');

-- SP20027 梅花形弹性联轴器（120元，年消耗5个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20027','2026-02','B','Z',39.50,  600.00,2.6800,2,4,95.00,'BZ');

-- ---- C × X（9件）：低价值 + 需求稳定 ----
-- SP20016 数控车床硬质合金刀片（350元，年消耗24盒）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20016','2026-02','C','X',28.70, 8400.00,0.2200,5,9,90.00,'CX');

-- SP20014 内六角圆柱头螺钉（15元，年消耗48盒）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20014','2026-02','C','X',22.50,  720.00,0.1500,10,20,90.00,'CX');

-- SP20040 膨胀螺栓（45元，年消耗30包）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20040','2026-02','C','X',20.80, 1350.00,0.2400,8,14,90.00,'CX');

-- SP20034 空气滤芯（25元，年消耗36个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20034','2026-02','C','X',18.40,  900.00,0.1800,8,15,90.00,'CX');

-- SP20038 弹簧垫圈（20元，年消耗40盒）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20038','2026-02','C','X',16.30,  800.00,0.1600,10,18,90.00,'CX');

-- SP20039 自攻螺丝（15元，年消耗60盒）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20039','2026-02','C','X',14.20,  900.00,0.1900,12,22,90.00,'CX');

-- SP20036 紫铜垫圈（12元，年消耗60包）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20036','2026-02','C','X',12.60,  720.00,0.2000,12,22,90.00,'CX');

-- SP20044 尼龙扎带（15元，年消耗48包）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20044','2026-02','C','X',10.50,  720.00,0.2100,10,18,90.00,'CX');

-- SP20045 PVC绝缘胶布（5元，年消耗84卷）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20045','2026-02','C','X', 8.30,  420.00,0.1700,15,25,90.00,'CX');

-- ---- C × Y（8件）：低价值 + 需求波动 ----
-- SP20022 推力球轴承（85元，年消耗10个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20022','2026-02','C','Y',28.10,  850.00,0.7800,4,7,90.00,'CY');

-- SP20008 高压软管（45元，年消耗20米）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20008','2026-02','C','Y',25.40,  900.00,0.6500,6,10,90.00,'CY');

-- SP20042 工业润滑脂（85元，年消耗12桶）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20042','2026-02','C','Y',23.50, 1020.00,0.8200,4,7,90.00,'CY');

-- SP20030 急停按钮（45元，年消耗18个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20030','2026-02','C','Y',17.30,  810.00,0.6200,5,10,90.00,'CY');

-- SP20032 行程开关（35元，年消耗20个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20032','2026-02','C','Y',18.60,  700.00,0.5800,6,10,90.00,'CY');

-- SP20029 按钮开关（18元，年消耗30个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20029','2026-02','C','Y',15.70,  540.00,0.5500,8,14,90.00,'CY');

-- SP20041 焊接防飞溅剂（25元，年消耗24瓶）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20041','2026-02','C','Y',13.20,  600.00,0.9500,6,10,90.00,'CY');

-- SP20031 指示灯（8元，年消耗60个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20031','2026-02','C','Y',11.40,  480.00,0.7000,12,20,90.00,'CY');

-- ---- C × Z（8件）：低价值 + 需求随机 ----
-- SP20043 水溶性切削液（350元，年消耗8桶）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20043','2026-02','C','Z',26.30, 2800.00,2.4500,3,5,90.00,'CZ');

-- SP20035 油压缓冲器（65元，年消耗6个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20035','2026-02','C','Z',21.40,  390.00,2.1200,2,3,90.00,'CZ');

-- SP20050 数字万用表（450元，年消耗1台）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20050','2026-02','C','Z',19.80,  450.00,4.0000,1,2,90.00,'CZ');

-- SP20037 骨架油封（15元，年消耗18个）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20037','2026-02','C','Z',12.80,  270.00,1.8500,5,8,90.00,'CZ');

-- SP20049 外径千分尺（150元，年消耗2把）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20049','2026-02','C','Z',13.50,  300.00,3.4500,1,2,90.00,'CZ');

-- SP20048 数显游标卡尺（120元，年消耗3把）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20048','2026-02','C','Z',11.20,  360.00,2.9500,1,2,90.00,'CZ');

-- SP20047 活动扳手（45元，年消耗4把）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20047','2026-02','C','Z', 8.50,  180.00,2.7800,1,2,90.00,'CZ');

-- SP20046 内六角扳手组套（85元，年消耗3套）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20046','2026-02','C','Z', 9.70,  255.00,3.1200,1,2,90.00,'CZ');

-- =============================================================
-- 2025-12 历史分类数据（供趋势对比，分布略有差异）
-- 只插入部分备件的历史记录，以展示变化趋势
-- =============================================================

-- A类备件历史（得分略低，反映去年底评估）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20007','2025-12','A','X',90.10, 7600.00,0.2100,1,2,99.00,'AX');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20002','2025-12','A','X',83.20, 4200.00,0.1500,2,3,99.00,'AX');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20011','2025-12','A','Y',80.50, 4350.00,0.6800,2,4,99.00,'AY');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20017','2025-12','A','Y',77.30, 1400.00,0.8900,3,4,99.00,'AY');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20028','2025-12','A','Z',74.60,  650.00,4.2000,2,3,99.00,'AZ');

-- B类备件历史
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20001','2025-12','B','X',53.80, 1228.50,0.2600,7,12,95.00,'BX');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20010','2025-12','B','X',46.40, 1870.00,0.2200,6,11,95.00,'BX');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20004','2025-12','B','Y',54.10, 1755.00,0.6800,4,8,95.00,'BY');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20005','2025-12','C','Z',28.50, 1040.00,1.7800,4,6,90.00,'CZ');

-- C类备件历史（SP20005去年还是C类，今年升为B类，反映重要性提升）
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20016','2025-12','C','X',26.90, 7700.00,0.2500,4,8,90.00,'CX');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20013','2025-12','B','X',40.10,  612.00,0.2800,12,24,95.00,'BX');
INSERT INTO biz_part_classify (part_code,classify_month,abc_class,xyz_class,composite_score,annual_cost,cv2,safety_stock,reorder_point,service_level,strategy_code)
VALUES ('SP20043','2025-12','C','Z',24.50, 2450.00,2.6800,2,4,90.00,'CZ');

-- =============================================================
-- 验证语句（执行后可检查结果）
-- =============================================================
-- SELECT abc_class, xyz_class, COUNT(*) AS cnt
-- FROM biz_part_classify
-- WHERE classify_month = '2026-02'
-- GROUP BY abc_class, xyz_class
-- ORDER BY abc_class, xyz_class;
--
-- 预期输出：
--   A | X | 4
--   A | Y | 4
--   A | Z | 2
--   B | X | 7
--   B | Y | 5
--   B | Z | 3
--   C | X | 9
--   C | Y | 8
--   C | Z | 8
