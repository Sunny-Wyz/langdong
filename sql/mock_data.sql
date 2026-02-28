-- =============================================================
-- 备件管理系统测试数据
-- 说明：
--   1. 仅清除业务表数据，不修改用户/权限数据
--   2. 本脚本依赖 sql/classify_module.sql 已执行（spare_part 表含
--      is_critical / replace_diff / lead_time 字段）
-- 执行方式：
--   mysql -u root -p spare_db < sql/mock_data.sql
-- =============================================================

USE spare_db;
SET FOREIGN_KEY_CHECKS = 0;

-- ============ 清空业务表（按 FK 依赖顺序） ============
TRUNCATE TABLE biz_requisition_item;
TRUNCATE TABLE biz_requisition;
TRUNCATE TABLE biz_work_order;
TRUNCATE TABLE biz_part_classify;
TRUNCATE TABLE spare_part_stock;
TRUNCATE TABLE equipment_spare_part;
TRUNCATE TABLE spare_part;
TRUNCATE TABLE equipment;
TRUNCATE TABLE location;
TRUNCATE TABLE supplier;
TRUNCATE TABLE spare_part_category;
TRUNCATE TABLE biz_reorder_suggest;
TRUNCATE TABLE biz_purchase_order;
TRUNCATE TABLE biz_supplier_quote;

SET FOREIGN_KEY_CHECKS = 1;

-- ============ 确保 admin 账号存在（不重复插入） ============
INSERT IGNORE INTO `user` (id, username, name, password, status) VALUES (
    1, 'admin', '系统管理员',
    '$2a$10$LaRzdak9/Sl0Y2xLhKTXoel1q2FACT0T1g5XEcjFV4QWqrmIz2Rxa', 1
);
INSERT IGNORE INTO `user_role` (user_id, role_id) VALUES (1, 1);

-- ============ 备件品类（10条） ============
INSERT INTO spare_part_category (code, name) VALUES ('C001', '通用消耗');
INSERT INTO spare_part_category (code, name) VALUES ('C002', '电气组件');
INSERT INTO spare_part_category (code, name) VALUES ('C003', '机械传动');
INSERT INTO spare_part_category (code, name) VALUES ('C004', '气动元件');
INSERT INTO spare_part_category (code, name) VALUES ('C005', '液压配件');
INSERT INTO spare_part_category (code, name) VALUES ('C006', '刀具夹具');
INSERT INTO spare_part_category (code, name) VALUES ('C007', '传感器类');
INSERT INTO spare_part_category (code, name) VALUES ('C008', '紧固件');
INSERT INTO spare_part_category (code, name) VALUES ('C009', '密封件');
INSERT INTO spare_part_category (code, name) VALUES ('C010', '轴承类');

-- ============ 供应商（20条，联系人各不相同） ============
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1000', '西门子(中国)有限公司',   '王建国', '13911506498', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1001', 'ABB电气有限公司',        '李志远', '13977195272', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1002', '施耐德电气',             '陈建华', '13941874444', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1003', '日本NSK精工株式会社',    '刘海峰', '13966124023', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1004', '博世力士乐液压',         '张伟明', '13915920672', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1005', 'SMC(中国)有限公司',      '孙晓春', '13919634264', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1006', '欧姆龙自动化',           '赵国庆', '13989533954', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1007', '基恩士(中国)有限公司',   '周鑫宇', '13964791356', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1008', '三菱电机自动化',         '吴俊杰', '13970799630', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1009', '安川电机',               '郑丽华', '13976721613', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1010', '贺德克液压技术',         '林建成', '13954567844', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1011', 'FESTO费斯托',            '徐雪梅', '13979662657', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1012', 'SKF斯凯孚中国',          '黄志强', '13963293789', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1013', 'THK直线导轨',            '杨晓雨', '13997598114', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1014', '哈尔滨轴承制造',         '曹明亮', '13952930798', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1015', '正泰电器',               '高建军', '13958584061', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1016', '倍福自动化',             '马文博', '13976162372', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1017', '图尔克传感器',           '余思远', '13969058247', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1018', '巴鲁夫传感器',           '谢秀英', '13947213162', '正常');
INSERT INTO supplier (code, name, contact_person, phone, status) VALUES ('SUP-1019', '魏德米勒',               '邓志华', '13959258049', '正常');
-- supplier ID: 西门子=1, ABB=2, 施耐德=3, NSK=4, 博世=5, SMC=6, 欧姆龙=7,
--              基恩士=8, 三菱=9, 安川=10, 贺德克=11, FESTO=12, SKF=13,
--              THK=14, 哈轴=15, 正泰=16, 倍福=17, 图尔克=18, 巴鲁夫=19, 魏德米勒=20

-- ============ 货位（12条） ============
-- zone 字段存储区域代码（A~F）
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('A-01', 'A区机电库1号架',   'A', 50, '存放伺服、变频器等电气设备');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('A-02', 'A区机电库2号架',   'A', 50, '存放PLC模块、开关电源');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('A-03', 'A区机电库3号架',   'A', 50, '存放传感器、交换机');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('B-01', 'B区液压气动库1号架','B', 40, '存放液压泵、控制阀');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('B-02', 'B区液压气动库2号架','B', 40, '存放气缸、电磁阀、气源处理器');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('C-01', 'C区轴承密封库1号架','C', 60, '存放深沟球轴承、圆柱滚子轴承');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('C-02', 'C区轴承密封库2号架','C', 60, '存放密封圈、骨架油封');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('D-01', 'D区消耗品库1号架',  'D', 80, '存放润滑脂、切削液、清洗剂');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('D-02', 'D区消耗品库2号架',  'D', 80, '存放紧固件、扎带、胶布');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('E-01', 'E区刀具夹具库1号架','E', 30, '存放数控刀片');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('E-02', 'E区刀具夹具库2号架','E', 30, '存放量具、扳手');
INSERT INTO location (code, name, zone, capacity, remark) VALUES ('F-01', 'F区大型暂存区1号位','F', 10, '存放减速机、大型传动件');
-- location ID: A-01=1, A-02=2, A-03=3, B-01=4, B-02=5, C-01=6, C-02=7,
--              D-01=8, D-02=9, E-01=10, E-02=11, F-01=12

-- ============ 设备（15条） ============
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1000', 'FANUC M-20iA焊接机器人',       'M-20iA',        '焊接车间',   '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1001', 'DMG DMU 50五轴加工中心',       'DMU 50',        '机加工车间', '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1002', 'KUKA KR 16弧焊机器人',         'KR 16',         '焊接车间',   '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1003', '海天MA10000注塑机',             'MA10000',       '注塑车间',   '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1004', '阿特拉斯GA37空气压缩机',       'GA37',          '空压站',     '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1005', '马扎克VCN-530C立式加工中心',   'VCN-530C',      '机加工车间', '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1006', '基恩士IM-8000非接触式测量仪',  'IM-8000',       '质检室',     '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1007', '通快TruLaser 3030激光切割机',  'TruLaser 3030', '钣金车间',   '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1008', '博能传动齿轮箱测试台',         'GB-T',          '测试车间',   '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1009', '蔡司CONTURA三坐标测量机',      'CONTURA 7/10/6','质检室',     '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1010', '西门子S7-1500 PLC控制柜',      'S7-1500',       '总装线',     '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1011', '史丹利智能拧紧系统',           'QPM',           '总装线',     '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1012', '康明斯静音柴油发电机',         'C150D5',        '动力中心',   '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1013', '爱普生LS3 SCARA机器人',        'LS3',           '电子装配线', '正常');
INSERT INTO equipment (code, name, model, department, status) VALUES ('EQ-1014', '大族激光打标机',               'F20',           '标识工位',   '正常');

-- ============ 备件档案（50条）============
-- 字段顺序: code, name, model, unit, category_id, price, quantity,
--           supplier(文本), supplier_id, location_id,
--           is_critical, replace_diff, lead_time
-- 注意: is_critical/replace_diff/lead_time 需先执行 classify_module.sql

-- 电气组件（伺服/变频/PLC/开关电源）- 关键备件，提前期长
INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20001','深沟球轴承','6205-2RS','个',10,45.5,84,'NSK',4,6,0,2,10);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (1, 84);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20002','交流变频器','FR-D720S-1.5K','台',2,1200.0,8,'三菱电机自动化',9,1,1,4,45);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (2, 8);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20003','微型光电传感器','PZ-G51N','个',7,280.0,18,'欧姆龙自动化',7,3,1,3,20);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (3, 18);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20004','气动薄型气缸','CQ2B32-25D','个',4,135.0,23,'FESTO费斯托',12,5,1,2,15);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (4, 23);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20005','接近开关','PR18-8DN','个',7,65.0,47,'图尔克传感器',18,3,1,2,15);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (5, 47);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20006','微型断路器','iC65N C16A 2P','个',2,38.5,61,'ABB电气有限公司',2,1,0,1,7);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (6, 61);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20007','伺服电机','SGMJV-04AAA61','台',2,2800.0,4,'安川电机',10,1,1,5,60);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (7, 4);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20008','高压软管','2SN-DN10','米',5,45.0,86,'博世力士乐液压',5,4,0,2,14);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (8, 86);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20009','直线导轨滑块','HGH25CA','个',3,110.0,31,'THK直线导轨',14,NULL,1,3,25);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (9, 31);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20010','接触器','LC1D18M7C','个',2,85.0,99,'施耐德电气',3,1,0,1,7);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (10, 99);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20011','PLC数字量输入模块','6ES7521-1BL00-0AB0','个',2,1450.0,5,'西门子(中国)有限公司',1,2,1,4,45);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (11, 5);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20012','圆柱滚子轴承','NU210 ECP','个',10,180.0,21,'SKF斯凯孚中国',13,6,0,2,14);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (12, 21);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20013','O型密封圈','VITON 50x3.1','件',9,8.5,239,'通用消耗',NULL,7,0,1,5);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (13, 239);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20014','内六角圆柱头螺钉','M8x25','盒',8,15.0,174,'紧固件',NULL,9,0,1,3);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (14, 174);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20015','两位五通电磁阀','SY5120-5LZD-01','个',4,210.0,30,'SMC(中国)有限公司',6,5,1,2,20);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (15, 30);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20016','数控车床硬质合金刀片','CNMG120408-PM','盒',6,350.0,52,'通用消耗',NULL,10,0,2,14);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (16, 52);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20017','液压齿轮泵','CB-B10','个',5,280.0,15,'博世力士乐液压',5,4,1,3,30);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (17, 15);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20018','单向阀','CV-02','个',5,85.0,43,'贺德克液压技术',11,4,1,2,20);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (18, 43);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20019','工业以太网交换机','SCALANCE XB005','个',2,650.0,7,'西门子(中国)有限公司',1,2,1,4,40);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (19, 7);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20020','铂电阻温度传感器','PT100','个',7,120.0,23,'巴鲁夫传感器',19,3,0,2,14);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (20, 23);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20021','开关电源','S8VK-C12024','个',2,240.0,19,'欧姆龙自动化',7,2,1,3,30);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (21, 19);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20022','推力球轴承','51110','个',10,85.0,38,'哈尔滨轴承制造',15,6,0,2,10);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (22, 38);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20023','气源处理器','AC4000-04','套',4,180.0,13,'SMC(中国)有限公司',6,5,1,2,20);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (23, 13);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20024','磁性开关','D-A93','个',7,45.0,65,'SMC(中国)有限公司',6,3,0,2,15);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (24, 65);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20025','中间继电器','MY2N-J DC24V','个',2,25.0,99,'欧姆龙自动化',7,1,0,1,7);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (25, 99);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20026','同步带','5M-500-15','条',3,35.0,40,'机械传动',NULL,NULL,0,2,14);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (26, 40);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20027','梅花形弹性联轴器','GR-14/19-82','套',3,120.0,12,'机械传动',NULL,NULL,0,2,14);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (27, 12);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20028','蜗轮蜗杆减速机','RV50-20','台',3,650.0,3,'机械传动',NULL,12,1,4,45);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (28, 3);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20029','按钮开关','XB2-BA31C','个',2,18.0,75,'施耐德电气',3,1,0,1,7);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (29, 75);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20030','急停按钮','ZB5AS844','个',2,45.0,55,'施耐德电气',3,1,0,1,7);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (30, 55);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20031','指示灯','AD16-22D/S','个',2,8.0,130,'正泰电器',16,1,0,1,5);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (31, 130);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20032','行程开关','TZ-8108','个',7,35.0,51,'通用消耗',NULL,3,0,2,10);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (32, 51);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20033','光栅尺','KA-300','把',7,850.0,8,'基恩士(中国)有限公司',8,3,1,4,45);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (33, 8);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20034','空气滤芯','AF2000-02','个',4,25.0,87,'SMC(中国)有限公司',6,5,0,1,5);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (34, 87);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20035','油压缓冲器','AC1412-2','个',4,65.0,28,'SMC(中国)有限公司',6,5,0,2,15);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (35, 28);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20036','紫铜垫圈','M14','包',9,12.0,98,'密封件',NULL,7,0,1,5);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (36, 98);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20037','骨架油封','TC 35x50x8','个',9,15.0,75,'密封件',NULL,7,0,2,10);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (37, 75);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20038','弹簧垫圈','M10 GB93','盒',8,20.0,119,'紧固件',NULL,9,0,1,3);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (38, 119);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20039','自攻螺丝','M4x16','盒',8,15.0,131,'紧固件',NULL,9,0,1,3);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (39, 131);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20040','膨胀螺栓','M10x100','包',8,45.0,89,'紧固件',NULL,9,0,1,5);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (40, 89);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20041','焊接防飞溅剂','450ml','瓶',1,25.0,38,'通用消耗',NULL,8,0,1,3);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (41, 38);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20042','工业润滑脂','美孚 XHP222','桶',1,85.0,22,'通用消耗',NULL,8,0,1,5);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (42, 22);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20043','水溶性切削液','半合成微乳化','桶',1,350.0,14,'通用消耗',NULL,8,0,1,7);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (43, 14);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20044','尼龙扎带','4x200mm','包',1,15.0,70,'通用消耗',NULL,9,0,1,3);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (44, 70);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20045','PVC绝缘胶布','18mm宽','卷',1,5.0,138,'魏德米勒',20,9,0,1,3);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (45, 138);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20046','内六角扳手组套','9件套','套',6,85.0,20,'刀具夹具',NULL,11,0,1,5);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (46, 20);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20047','活动扳手','10寸','把',6,45.0,29,'刀具夹具',NULL,11,0,1,5);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (47, 29);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20048','数显游标卡尺','0-150mm 0.01mm','把',6,120.0,13,'刀具夹具',NULL,11,0,2,10);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (48, 13);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20049','外径千分尺','0-25mm','把',6,150.0,11,'刀具夹具',NULL,11,0,2,10);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (49, 11);

INSERT INTO spare_part (code,name,model,unit,category_id,price,quantity,supplier,supplier_id,location_id,is_critical,replace_diff,lead_time)
VALUES ('SP20050','数字万用表','Fluke 15B+','台',6,450.0,3,'电气组件',NULL,1,0,2,14);
INSERT INTO spare_part_stock (spare_part_id, quantity) VALUES (50, 3);

-- ============ 维修工单（30条，reporter_id=1 即 admin） ============
INSERT INTO biz_work_order (work_order_no,device_id,reporter_id,fault_desc,fault_level,order_status,report_time,part_cost,labor_cost) VALUES
('WO2026020001',13,1,'伺服驱动器报警E-09，设备急停','计划','待派工',   '2026-01-09 08:12:00',588.55,400.12),
('WO2026020002', 9,1,'接触器触点烧结，电机无法启动','一般','待验收',   '2026-02-11 09:30:00',515.53,204.94),
('WO2026020003', 4,1,'导轨润滑不良，运行时伴有干摩擦异响','紧急','已完成','2026-02-20 10:05:00',448.58,275.34),
('WO2026020004', 5,1,'PLC通讯故障，总线红色报错灯亮','计划','待验收',  '2026-01-20 14:22:00',651.57,336.95),
('WO2026020005',11,1,'主轴传动皮带异响，疑似松动','紧急','待派工',      '2026-01-23 07:45:00',435.68,132.70),
('WO2026020006', 5,1,'气缸漏气，动作缓慢无力','一般','待验收',          '2026-01-13 11:30:00',616.32,132.76),
('WO2026020007',11,1,'PLC通讯故障，总线红色报错灯亮','一般','待派工',   '2026-02-15 15:18:00',769.34,372.53),
('WO2026020008',14,1,'轴承过度磨损导致主轴振动超标','计划','已完成',    '2026-02-08 08:55:00',876.30,353.11),
('WO2026020009',13,1,'气缸漏气，动作缓慢无力','紧急','维修中',          '2026-01-28 13:40:00',977.33,212.38),
('WO2026020010', 7,1,'液压站油温过高报警，冷却系统不工作','紧急','维修中','2026-02-20 09:10:00',443.45,351.26),
('WO2026020011', 9,1,'液压站油温过高报警，冷却系统不工作','一般','已完成','2026-02-14 10:22:00',407.83,309.46),
('WO2026020012',15,1,'气源滤芯堵塞，气压不足导致设备停机','一般','维修中','2026-02-16 08:30:00',778.24,441.19),
('WO2026020013',11,1,'气缸漏气，动作缓慢无力','一般','已完成',          '2026-02-14 14:05:00',631.43,202.98),
('WO2026020014',15,1,'气缸漏气，动作缓慢无力','紧急','待派工',          '2026-02-16 11:15:00',976.53,207.47),
('WO2026020015',14,1,'轴承过度磨损导致主轴振动超标','一般','待派工',    '2026-02-15 09:00:00',995.18,277.68),
('WO2026020016',11,1,'气缸漏气，动作缓慢无力','计划','待验收',          '2026-01-19 16:20:00',231.13,382.61),
('WO2026020017',12,1,'PLC通讯故障，总线红色报错灯亮','计划','已完成',   '2026-01-20 08:45:00',261.45,169.14),
('WO2026020018',10,1,'气缸漏气，动作缓慢无力','紧急','维修中',          '2026-02-17 10:30:00',951.49,238.71),
('WO2026020019',15,1,'导轨润滑不良，运行时伴有干摩擦异响','一般','待验收','2026-02-03 13:15:00',296.19,489.34),
('WO2026020020',12,1,'气缸漏气，动作缓慢无力','一般','待派工',          '2026-01-24 09:40:00',947.78,158.43),
('WO2026020021', 2,1,'接触器触点烧结，电机无法启动','一般','维修中',    '2026-01-14 11:00:00',165.50,210.49),
('WO2026020022', 4,1,'主轴传动皮带异响，疑似松动','计划','待派工',      '2026-01-13 08:20:00',522.00,467.00),
('WO2026020023',14,1,'气源滤芯堵塞，气压不足导致设备停机','一般','维修中','2026-02-03 14:30:00',357.68,133.06),
('WO2026020024', 2,1,'伺服驱动器报警E-09，设备急停','紧急','待派工',    '2026-01-04 07:55:00',950.69,135.97),
('WO2026020025', 4,1,'主轴传动皮带异响，疑似松动','一般','维修中',      '2026-02-15 15:40:00', 97.06,333.22),
('WO2026020026',11,1,'气缸漏气，动作缓慢无力','计划','已完成',          '2026-02-13 10:10:00',397.79,289.99),
('WO2026020027',10,1,'液压站油温过高报警，冷却系统不工作','一般','待派工','2026-01-06 13:50:00',147.78,263.08),
('WO2026020028', 3,1,'主轴传动皮带异响，疑似松动','计划','已完成',      '2026-02-03 09:25:00',836.68,144.15),
('WO2026020029', 5,1,'主轴传动皮带异响，疑似松动','计划','维修中',      '2026-01-27 11:35:00',551.67,319.36),
('WO2026020030',11,1,'主轴传动皮带异响，疑似松动','计划','待派工',      '2026-01-04 08:00:00',876.85,301.82);

-- ============ 历史领用单（用于 ABC/XYZ 分类计算）============
-- 2026-01 ~ 2026-02 当期数据（25条）
-- 说明：OUTBOUND/INSTALLED 的记录含 approve_time，用于月度消耗统计
INSERT INTO biz_requisition (req_no,applicant_id,work_order_no,remark,req_status,created_at,approve_time) VALUES
('REQ-2026-0001',1,'WO2026020005','设备维修更换消耗','OUTBOUND',  '2026-02-06 10:00:00','2026-02-06 14:00:00'),
('REQ-2026-0002',1,'WO2026020006','设备维修更换消耗','REJECTED',  '2026-01-03 09:00:00',NULL),
('REQ-2026-0003',1,'WO2026020017','设备维修更换消耗','OUTBOUND',  '2026-01-22 11:00:00','2026-01-22 15:00:00'),
('REQ-2026-0004',1,'WO2026020023','设备维修更换消耗','INSTALLED', '2026-02-11 09:00:00','2026-02-11 16:00:00'),
('REQ-2026-0005',1,'WO2026020011','设备维修更换消耗','APPROVED',  '2026-01-29 10:00:00',NULL),
('REQ-2026-0006',1,'WO2026020003','设备维修更换消耗','INSTALLED', '2026-01-11 08:00:00','2026-01-11 17:00:00'),
('REQ-2026-0007',1,'WO2026020007','设备维修更换消耗','PENDING',   '2026-02-10 14:00:00',NULL),
('REQ-2026-0008',1,'WO2026020020','设备维修更换消耗','INSTALLED', '2026-01-14 09:00:00','2026-01-14 15:00:00'),
('REQ-2026-0009',1,'WO2026020028','设备维修更换消耗','INSTALLED', '2026-01-21 10:00:00','2026-01-21 16:30:00'),
('REQ-2026-0010',1,'WO2026020026','设备维修更换消耗','PENDING',   '2026-02-19 11:00:00',NULL),
('REQ-2026-0011',1,'WO2026020017','设备维修更换消耗','INSTALLED', '2026-02-19 08:00:00','2026-02-19 17:00:00'),
('REQ-2026-0012',1,'WO2026020009','设备维修更换消耗','APPROVED',  '2026-01-24 09:30:00',NULL),
('REQ-2026-0013',1,'WO2026020028','设备维修更换消耗','PENDING',   '2026-01-04 14:00:00',NULL),
('REQ-2026-0014',1,'WO2026020027','设备维修更换消耗','REJECTED',  '2026-02-04 10:00:00',NULL),
('REQ-2026-0015',1,'WO2026020002','设备维修更换消耗','INSTALLED', '2026-02-11 08:30:00','2026-02-11 17:30:00'),
('REQ-2026-0016',1,'WO2026020010','设备维修更换消耗','APPROVED',  '2026-02-15 09:00:00',NULL),
('REQ-2026-0017',1,'WO2026020013','设备维修更换消耗','PENDING',   '2026-01-01 10:00:00',NULL),
('REQ-2026-0018',1,'WO2026020007','设备维修更换消耗','INSTALLED', '2026-02-02 09:00:00','2026-02-02 16:00:00'),
('REQ-2026-0019',1,'WO2026020028','设备维修更换消耗','PENDING',   '2026-01-08 11:00:00',NULL),
('REQ-2026-0020',1,'WO2026020009','设备维修更换消耗','APPROVED',  '2026-02-14 14:00:00',NULL),
('REQ-2026-0021',1,'WO2026020019','设备维修更换消耗','APPROVED',  '2026-01-27 10:00:00',NULL),
('REQ-2026-0022',1,'WO2026020021','设备维修更换消耗','APPROVED',  '2026-01-29 09:00:00',NULL),
('REQ-2026-0023',1,'WO2026020023','设备维修更换消耗','INSTALLED', '2026-01-20 08:00:00','2026-01-20 17:00:00'),
('REQ-2026-0024',1,'WO2026020029','设备维修更换消耗','PENDING',   '2026-01-19 15:00:00',NULL),
('REQ-2026-0025',1,'WO2026020014','设备维修更换消耗','APPROVED',  '2026-02-09 10:00:00',NULL);

-- 2025-10 ~ 2025-12 历史数据（补充消耗历史，供 ABC/XYZ 计算用）
INSERT INTO biz_requisition (req_no,applicant_id,work_order_no,remark,req_status,created_at,approve_time) VALUES
('REQ-2025-0001',1,'WO2026020001','日常消耗更换','INSTALLED','2025-10-05 09:00:00','2025-10-05 16:00:00'),
('REQ-2025-0002',1,'WO2026020002','日常消耗更换','INSTALLED','2025-10-18 10:00:00','2025-10-18 17:00:00'),
('REQ-2025-0003',1,'WO2026020003','日常消耗更换','INSTALLED','2025-11-03 08:30:00','2025-11-03 16:30:00'),
('REQ-2025-0004',1,'WO2026020004','日常消耗更换','INSTALLED','2025-11-15 09:00:00','2025-11-15 17:00:00'),
('REQ-2025-0005',1,'WO2026020005','日常消耗更换','INSTALLED','2025-11-28 10:00:00','2025-11-28 16:00:00'),
('REQ-2025-0006',1,'WO2026020006','日常消耗更换','INSTALLED','2025-12-08 09:00:00','2025-12-08 17:00:00'),
('REQ-2025-0007',1,'WO2026020007','日常消耗更换','INSTALLED','2025-12-20 10:00:00','2025-12-20 16:30:00'),
('REQ-2025-0008',1,'WO2026020008','日常消耗更换','INSTALLED','2025-12-26 09:00:00','2025-12-26 17:00:00');
-- REQ-2026-0001 → ID=1, ..., REQ-2026-0025 → ID=25
-- REQ-2025-0001 → ID=26, ..., REQ-2025-0008 → ID=33

-- ============ 领用明细（含 out_qty 用于消耗统计） ============
-- OUTBOUND/INSTALLED 的记录需设置 out_qty（其余为 NULL）

-- REQ-2026-0001 OUTBOUND: 膨胀螺栓(40) x1
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (1,40,1,1);
-- REQ-2026-0002 REJECTED: 千分尺(49) x5（out_qty=NULL）
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (2,49,5,NULL);
-- REQ-2026-0003 OUTBOUND: 同步带(26) x3, 交换机(19) x1
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (3,26,3,3);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (3,19,1,1);
-- REQ-2026-0004 INSTALLED: 刀片(16) x5
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (4,16,5,5);
-- REQ-2026-0005 APPROVED
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (5,14,4,NULL);
-- REQ-2026-0006 INSTALLED: 弹簧垫圈(38) x2, 行程开关(32) x5
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (6,38,2,2);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (6,32,5,5);
-- REQ-2026-0007 PENDING
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (7,37,4,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (7,45,5,NULL);
-- REQ-2026-0008 INSTALLED: 润滑脂(42) x3, 高压软管(8) x1
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (8,42,3,3);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (8,8,1,1);
-- REQ-2026-0009 INSTALLED: 单向阀(18) x2
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (9,18,2,2);
-- REQ-2026-0010 PENDING
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (10,13,1,NULL);
-- REQ-2026-0011 INSTALLED: 气源处理器(23) x5, 继电器(25) x4, 密封圈(13) x1
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (11,23,5,5);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (11,25,4,4);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (11,13,1,1);
-- REQ-2026-0012 APPROVED
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (12,16,2,NULL);
-- REQ-2026-0013 PENDING
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (13,50,4,NULL);
-- REQ-2026-0014 REJECTED
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (14,33,2,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (14,24,3,NULL);
-- REQ-2026-0015 INSTALLED: 螺钉(14) x5, 气源处理器(23) x3
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (15,14,5,5);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (15,23,3,3);
-- REQ-2026-0016 APPROVED
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (16,37,1,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (16,46,1,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (16,40,2,NULL);
-- REQ-2026-0017 PENDING
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (17,32,4,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (17,35,1,NULL);
-- REQ-2026-0018 INSTALLED: 同步带(26) x4, 联轴器(27) x4, 密封圈(13) x2
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (18,26,4,4);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (18,27,4,4);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (18,13,2,2);
-- REQ-2026-0019 PENDING
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (19,25,2,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (19,28,4,NULL);
-- REQ-2026-0020 APPROVED
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (20,19,4,NULL);
-- REQ-2026-0021 APPROVED
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (21,14,5,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (21,25,1,NULL);
-- REQ-2026-0022 APPROVED
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (22,37,3,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (22,47,3,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (22,36,3,NULL);
-- REQ-2026-0023 INSTALLED: 磁性开关(24) x3, 急停按钮(30) x4, 切削液(43) x3
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (23,24,3,3);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (23,30,4,4);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (23,43,3,3);
-- REQ-2026-0024 PENDING
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (24,44,4,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (24,40,5,NULL);
-- REQ-2026-0025 APPROVED
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (25,16,5,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (25,28,1,NULL);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (25,18,1,NULL);

-- 历史数据领用明细（REQ-2025-0001~0008，用于消耗历史统计）
-- REQ-2025-0001 (ID=26) INSTALLED: 密封圈(13) x3, 接触器(10) x2
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (26,13,3,3);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (26,10,2,2);
-- REQ-2025-0002 (ID=27) INSTALLED: 滤芯(34) x4, 扎带(44) x2, 螺钉(14) x3
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (27,34,4,4);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (27,44,2,2);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (27,14,3,3);
-- REQ-2025-0003 (ID=28) INSTALLED: 油封(37) x2, 垫圈(36) x4, 密封圈(13) x2
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (28,37,2,2);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (28,36,4,4);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (28,13,2,2);
-- REQ-2025-0004 (ID=29) INSTALLED: 继电器(25) x5, 断路器(6) x3, 接触器(10) x2
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (29,25,5,5);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (29,6,3,3);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (29,10,2,2);
-- REQ-2025-0005 (ID=30) INSTALLED: 润滑脂(42) x2, 滤芯(34) x3, 切削液(43) x2
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (30,42,2,2);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (30,34,3,3);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (30,43,2,2);
-- REQ-2025-0006 (ID=31) INSTALLED: 电磁阀(15) x2, 密封圈(13) x4, 螺钉(14) x2
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (31,15,2,2);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (31,13,4,4);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (31,14,2,2);
-- REQ-2025-0007 (ID=32) INSTALLED: 轴承(1) x2, 油封(37) x3, 垫片(38) x2
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (32,1,2,2);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (32,37,3,3);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (32,38,2,2);
-- REQ-2025-0008 (ID=33) INSTALLED: 气缸(4) x1, 电磁阀(15) x2, 气源处理器(23) x1
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (33,4,1,1);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (33,15,2,2);
INSERT INTO biz_requisition_item (req_id,spare_part_id,apply_qty,out_qty) VALUES (33,23,1,1);
