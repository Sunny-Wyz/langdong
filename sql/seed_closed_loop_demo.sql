-- =====================================================================
-- 采购—仓储—领用—工单 业务闭环演示种子
-- 可重复执行：清理 DEMO 标记数据后重建
-- 用法：mysql -u admin -p spare_db < sql/seed_closed_loop_demo.sql
-- 依赖：spare_part(50)、equipment(15)、location(12)、supplier 已存在
-- =====================================================================
SET NAMES utf8mb4;
USE spare_db;

-- received_qty 列
SET @col := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_purchase_order' AND COLUMN_NAME = 'received_qty'
);
SET @sql := IF(@col = 0,
  'ALTER TABLE biz_purchase_order ADD COLUMN received_qty INT NOT NULL DEFAULT 0 COMMENT ''累计已入库数量'' AFTER order_qty',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------
-- 1. 设备-备件配套（清空后重建，覆盖全部 15 台）
-- ---------------------------------------------------------------------
DELETE FROM equipment_spare_part;

-- 设备1 灌装机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(1,1,2),(1,3,2),(1,4,1),(1,13,4),(1,16,2),(1,37,2),(1,42,1);
-- 设备2 旋盖机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(2,41,4),(2,1,2),(2,5,2),(2,9,2),(2,26,1),(2,42,1);
-- 设备3 套标机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(3,3,2),(3,5,2),(3,9,1),(3,26,2),(3,32,1);
-- 设备4 贴标机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(4,3,3),(4,5,2),(4,24,2),(4,32,1),(4,33,1);
-- 设备5 阿特拉斯空压机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(5,34,2),(5,15,2),(5,23,1),(5,4,1),(5,37,2);
-- 设备6 PLC控制柜
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(6,11,2),(6,6,4),(6,10,2),(6,2,1),(6,21,1),(6,25,4);
-- 设备7 视觉检测
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(7,33,1),(7,3,2),(7,21,1),(7,19,1);
-- 设备8 锅炉
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(8,20,2),(8,18,1),(8,13,4),(8,17,1);
-- 设备9 开山空压机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(9,34,2),(9,15,1),(9,23,1),(9,12,2);
-- 设备10 CIP
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(10,43,2),(10,17,1),(10,18,2),(10,8,4),(10,13,4);
-- 设备11 蒸馏机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(11,20,2),(11,17,1),(11,18,1),(11,12,2),(11,37,2);
-- 设备12 码垛线
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(12,9,2),(12,7,1),(12,26,2),(12,27,1),(12,5,2);
-- 设备13 发电机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(13,6,2),(13,10,2),(13,21,1),(13,2,1);
-- 设备14 制冷机组
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(14,12,2),(14,22,2),(14,20,2),(14,37,2),(14,18,1);
-- 设备15 喷码机
INSERT INTO equipment_spare_part (equipment_id, spare_part_id, quantity) VALUES
(15,3,1),(15,21,1),(15,11,1),(15,29,2);

SELECT CONCAT('equipment_spare_part rows=', COUNT(*)) AS step1 FROM equipment_spare_part;

-- ---------------------------------------------------------------------
-- 2. 库存台账：保证每个备件有合理库存；货位分布
-- ---------------------------------------------------------------------
INSERT INTO spare_part_stock (spare_part_id, quantity)
SELECT sp.id, 20 + (sp.id % 30)
FROM spare_part sp
LEFT JOIN spare_part_stock s ON s.spare_part_id = sp.id
WHERE s.id IS NULL;

-- 校正明显为 0 的库存
UPDATE spare_part_stock SET quantity = GREATEST(quantity, 10 + (spare_part_id % 20))
WHERE quantity IS NULL OR quantity < 5;

-- 货位明细：清空后按备件映射到 12 个货位
DELETE FROM spare_part_location_stock;
INSERT INTO spare_part_location_stock (location_id, spare_part_id, quantity)
SELECT
  1 + ((sp.id - 1) % 12) AS location_id,
  sp.id,
  GREATEST(5, FLOOR(IFNULL(s.quantity, 20) * 0.6))
FROM spare_part sp
LEFT JOIN spare_part_stock s ON s.spare_part_id = sp.id;

SELECT CONCAT('location_stock rows=', COUNT(*)) AS step2 FROM spare_part_location_stock;

-- ---------------------------------------------------------------------
-- 3. 补一批 FIFO 可用入库批次（与库存大致一致，便于领用出库）
-- ---------------------------------------------------------------------
-- 清理旧 DEMO 入库
DELETE FROM stock_in_item WHERE stock_in_receipt_id IN (
  SELECT id FROM (SELECT id FROM stock_in_receipt WHERE receipt_code LIKE 'IN-DEMO-%') t
);
DELETE FROM stock_in_receipt WHERE receipt_code LIKE 'IN-DEMO-%';

INSERT INTO stock_in_receipt (receipt_code, purchase_order_id, receipt_date, status, handler_id, remark)
VALUES
('IN-DEMO-001', NULL, DATE_SUB(NOW(), INTERVAL 20 DAY), 'COMPLETED', 1, '闭环演示期初批次'),
('IN-DEMO-002', NULL, DATE_SUB(NOW(), INTERVAL 10 DAY), 'COMPLETED', 1, '闭环演示补充批次');

SET @r1 := (SELECT id FROM stock_in_receipt WHERE receipt_code='IN-DEMO-001' LIMIT 1);
SET @r2 := (SELECT id FROM stock_in_receipt WHERE receipt_code='IN-DEMO-002' LIMIT 1);

-- 为常用备件写入剩余批次
INSERT INTO stock_in_item (
  stock_in_receipt_id, purchase_order_item_id, spare_part_id,
  expected_quantity, actual_quantity, remaining_qty, shelved_quantity, location_id, in_time, remark
)
SELECT @r1, NULL, sp.id,
       15, 15, 15, 15,
       1 + ((sp.id - 1) % 12),
       DATE_SUB(NOW(), INTERVAL 20 DAY),
       'DEMO-FIFO'
FROM spare_part sp WHERE sp.id <= 30;

INSERT INTO stock_in_item (
  stock_in_receipt_id, purchase_order_item_id, spare_part_id,
  expected_quantity, actual_quantity, remaining_qty, shelved_quantity, location_id, in_time, remark
)
SELECT @r2, NULL, sp.id,
       10, 10, 10, 10,
       1 + ((sp.id - 1) % 12),
       DATE_SUB(NOW(), INTERVAL 10 DAY),
       'DEMO-FIFO'
FROM spare_part sp WHERE sp.id <= 30;

-- ---------------------------------------------------------------------
-- 4. 可收货采购单（到货/验收通过 未收满）+ 清理旧 DEMO 单
-- ---------------------------------------------------------------------
DELETE FROM biz_purchase_order WHERE order_no LIKE 'PO-DEMO-%';

INSERT INTO biz_purchase_order (
  order_no, spare_part_id, supplier_id, order_qty, received_qty,
  unit_price, total_amount, order_status, expected_date, actual_date, purchaser_id, remark
) VALUES
('PO-DEMO-ARRIVE-01', 1, 1, 40, 0, 45.50, 1820.00, '到货', DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL, 1, '演示-灌装轴承到货待收 [设备ID:1]'),
('PO-DEMO-ARRIVE-02', 34, 1, 20, 0, 280.00, 5600.00, '到货', DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL, 1, '演示-空压滤芯到货待收 [设备ID:5]'),
('PO-DEMO-ARRIVE-03', 11, 1, 8, 0, 1200.00, 9600.00, '到货', DATE_ADD(CURDATE(), INTERVAL 5 DAY), NULL, 1, '演示-PLC模块到货待收 [设备ID:6]'),
('PO-DEMO-PARTIAL-01', 16, 1, 30, 10, 860.00, 25800.00, '验收通过', CURDATE(), CURDATE(), 1, '演示-阀芯部分已收 [设备ID:1]'),
('PO-DEMO-SHIP-01', 41, 1, 50, 0, 35.00, 1750.00, '已发货', DATE_ADD(CURDATE(), INTERVAL 7 DAY), NULL, 1, '演示-旋盖头在途（不可收）'),
('PO-DEMO-ORDER-01', 3, 1, 25, 0, 180.00, 4500.00, '已下单', DATE_ADD(CURDATE(), INTERVAL 10 DAY), NULL, 1, '演示-光电传感器已下单');

-- 将部分历史「验收通过」且 received_qty=0 的订单标记为可演示收货（最多 5 条）
UPDATE biz_purchase_order
SET received_qty = 0,
    order_status = '到货',
    remark = CONCAT(IFNULL(remark, ''), ' [可收货演示]')
WHERE order_status = '验收通过'
  AND IFNULL(received_qty, 0) = 0
  AND order_no NOT LIKE 'PO-DEMO-%'
  AND spare_part_id IS NOT NULL
ORDER BY id DESC
LIMIT 5;

SELECT order_no, order_status, order_qty, received_qty, spare_part_id
FROM biz_purchase_order
WHERE order_status IN ('到货','验收通过') AND IFNULL(received_qty,0) < order_qty
ORDER BY id DESC LIMIT 15;

-- ---------------------------------------------------------------------
-- 5. 工单 + 领用闭环样例
-- ---------------------------------------------------------------------
DELETE FROM biz_requisition_item WHERE req_id IN (
  SELECT id FROM (SELECT id FROM biz_requisition WHERE req_no LIKE 'REQ-DEMO-%') t
);
DELETE FROM biz_requisition WHERE req_no LIKE 'REQ-DEMO-%';
DELETE FROM biz_work_order WHERE work_order_no LIKE 'WO-DEMO-%';

INSERT INTO biz_work_order (
  work_order_no, device_id, reporter_id, fault_desc, fault_level,
  order_status, report_time, assignee_id
) VALUES
('WO-DEMO-001', 1, 1, '灌装阀密封渗漏，需更换阀芯与密封圈', '紧急', '维修中', DATE_SUB(NOW(), INTERVAL 2 DAY), 1),
('WO-DEMO-002', 5, 1, '空压机排气温度偏高，更换滤芯并检查电磁阀', '一般', '已派工', DATE_SUB(NOW(), INTERVAL 1 DAY), 1),
('WO-DEMO-003', 6, 1, 'PLC 输入通道异常，计划更换数字量模块', '计划', '报修', NOW(), NULL);

INSERT INTO biz_requisition (
  req_no, applicant_id, work_order_no, device_id, req_status, is_urgent, remark, apply_time
) VALUES
('REQ-DEMO-001', 1, 'WO-DEMO-001', 1, 'OUTBOUND', 1, '闭环演示-灌装机领用已出库', DATE_SUB(NOW(), INTERVAL 2 DAY)),
('REQ-DEMO-002', 1, 'WO-DEMO-002', 5, 'APPROVED', 0, '闭环演示-空压机领用已审批待出库', DATE_SUB(NOW(), INTERVAL 1 DAY)),
('REQ-DEMO-003', 1, 'WO-DEMO-003', 6, 'PENDING', 0, '闭环演示-PLC领用待审批', NOW());

SET @rq1 := (SELECT id FROM biz_requisition WHERE req_no='REQ-DEMO-001' LIMIT 1);
SET @rq2 := (SELECT id FROM biz_requisition WHERE req_no='REQ-DEMO-002' LIMIT 1);
SET @rq3 := (SELECT id FROM biz_requisition WHERE req_no='REQ-DEMO-003' LIMIT 1);

INSERT INTO biz_requisition_item (req_id, spare_part_id, apply_qty, out_qty, batch_info) VALUES
(@rq1, 16, 2, 2, 'IN-DEMO-001[2]'),
(@rq1, 13, 4, 4, 'IN-DEMO-001[4]'),
(@rq2, 34, 2, 0, NULL),
(@rq2, 15, 1, 0, NULL),
(@rq3, 11, 1, 0, NULL),
(@rq3, 6, 2, 0, NULL);

-- 扣减已出库演示数量（REQ-DEMO-001）
UPDATE spare_part_stock SET quantity = GREATEST(0, quantity - 2) WHERE spare_part_id = 16;
UPDATE spare_part_stock SET quantity = GREATEST(0, quantity - 4) WHERE spare_part_id = 13;

-- ---------------------------------------------------------------------
-- 6. 补货建议对齐真实备件编码
-- ---------------------------------------------------------------------
DELETE FROM biz_reorder_suggest WHERE part_code LIKE 'DEMO-%' OR suggest_month = DATE_FORMAT(CURDATE(), '%Y-%m');
INSERT INTO biz_reorder_suggest (
  part_code, suggest_month, current_stock, reorder_point, suggest_qty,
  forecast_qty, lower_bound, upper_bound, urgency, status
)
SELECT sp.code, DATE_FORMAT(CURDATE(), '%Y-%m'),
       IFNULL(s.quantity, 0), 15, 20,
       12.5, 8.0, 18.0,
       IF(IFNULL(s.quantity,0) < 15, '紧急', '正常'),
       '待处理'
FROM spare_part sp
LEFT JOIN spare_part_stock s ON s.spare_part_id = sp.id
WHERE sp.id IN (1, 11, 16, 34, 41)
ORDER BY sp.id;

-- ---------------------------------------------------------------------
-- 校验
-- ---------------------------------------------------------------------
SELECT 'esp' AS k, COUNT(*) c FROM equipment_spare_part
UNION ALL SELECT 'stock', COUNT(*) FROM spare_part_stock
UNION ALL SELECT 'loc_stock', COUNT(*) FROM spare_part_location_stock
UNION ALL SELECT 'recv_po', COUNT(*) FROM biz_purchase_order
  WHERE order_status IN ('到货','验收通过') AND IFNULL(received_qty,0) < order_qty
UNION ALL SELECT 'demo_wo', COUNT(*) FROM biz_work_order WHERE work_order_no LIKE 'WO-DEMO-%'
UNION ALL SELECT 'demo_req', COUNT(*) FROM biz_requisition WHERE req_no LIKE 'REQ-DEMO-%';

SELECT '====== 闭环演示种子完成 ======' AS done;
SELECT '收货页可选：PO-DEMO-ARRIVE-01/02/03 或 PARTIAL-01' AS tip1;
SELECT '领用/工单：WO-DEMO-001~003 / REQ-DEMO-001~003' AS tip2;
