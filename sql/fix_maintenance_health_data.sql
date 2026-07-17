-- 修护建议管理 & 健康评分 真实数据修正脚本
SET NAMES utf8mb4;
USE spare_db;

-- =====================================================================
-- 第一步：给维修建议加真实多样性
--   - maintenance_type: EMERGENCY/PREVENTIVE/INSPECTION/REPLACEMENT 四种
--   - priority_level: HIGH/MEDIUM/LOW 三种
--   - status: PENDING/ACCEPTED/COMPLETED/REJECTED 四种（与系统枚举一致，勿用 APPROVED）
--   - estimated_cost: 按设备和类型不同，范围从 300 到 25000
--   - handled_by / handled_at: 非 PENDING 状态的才填
-- =====================================================================

-- 先按 id 区间分批 UPDATE，模拟真实状态分布

-- id 1-40: 较早期记录，大部分已处理完毕
UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'INSPECTION',
  priority_level   = 'LOW',
  status           = 'COMPLETED',
  estimated_cost   = 450.00,
  reason           = '定期巡检，发现灌装阀密封轻微老化，预防性检查',
  handled_by       = 1,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 2 DAY)
WHERE id BETWEEN 1 AND 5;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'PREVENTIVE',
  priority_level   = 'MEDIUM',
  status           = 'COMPLETED',
  estimated_cost   = 1200.00,
  reason           = '预防性维护，更换旋盖机硅胶旋盖头，防止扭矩波动',
  handled_by       = 1,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 3 DAY)
WHERE id BETWEEN 6 AND 12;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'REPLACEMENT',
  priority_level   = 'HIGH',
  status           = 'COMPLETED',
  estimated_cost   = 4800.00,
  reason           = '轴承磨损超标，建议立即更换深沟球轴承及油封',
  handled_by       = 17,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 1 DAY)
WHERE id BETWEEN 13 AND 18;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'INSPECTION',
  priority_level   = 'MEDIUM',
  status           = 'COMPLETED',
  estimated_cost   = 680.00,
  reason           = '空压机排气温度趋势上升，建议检查滤芯及冷却系统',
  handled_by       = 1,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 2 DAY)
WHERE id BETWEEN 19 AND 26;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'EMERGENCY',
  priority_level   = 'HIGH',
  status           = 'COMPLETED',
  estimated_cost   = 9500.00,
  reason           = '蒸馏甑桶冷凝系统压差异常，存在安全隐患，需紧急停机检修',
  handled_by       = 17,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 1 DAY)
WHERE id BETWEEN 27 AND 32;

-- id 33-80: 中期记录，部分已审批执行中
UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'PREVENTIVE',
  priority_level   = 'LOW',
  status           = 'COMPLETED',
  estimated_cost   = 320.00,
  reason           = '贴标机光电传感器感应灵敏度下降，建议清洁或更换',
  handled_by       = 17,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 4 DAY)
WHERE id BETWEEN 33 AND 40;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'REPLACEMENT',
  priority_level   = 'HIGH',
  status           = 'COMPLETED',
  estimated_cost   = 15000.00,
  reason           = 'ABB变频器内部功率模块老化，故障率上升，建议更换整机',
  handled_by       = 1,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 2 DAY)
WHERE id BETWEEN 41 AND 46;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'INSPECTION',
  priority_level   = 'MEDIUM',
  status           = 'ACCEPTED',
  estimated_cost   = 900.00,
  reason           = 'CIP清洗系统管路压力轻微异常，需检查管道密封及止回阀',
  handled_by       = 19,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 1 DAY)
WHERE id BETWEEN 47 AND 58;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'PREVENTIVE',
  priority_level   = 'MEDIUM',
  status           = 'ACCEPTED',
  estimated_cost   = 2400.00,
  reason           = '同步带张力检测偏低，预防性更换同步带及张紧轮',
  handled_by       = 19,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 2 DAY)
WHERE id BETWEEN 59 AND 68;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'EMERGENCY',
  priority_level   = 'HIGH',
  status           = 'REJECTED',
  estimated_cost   = 22000.00,
  reason           = '喷码机激光头模块损耗严重，建议整体更换',
  reject_reason    = '费用较高，当前备用机可周转，暂缓处理',
  handled_by       = 19,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 1 DAY)
WHERE id BETWEEN 69 AND 76;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'REPLACEMENT',
  priority_level   = 'HIGH',
  status           = 'ACCEPTED',
  estimated_cost   = 6800.00,
  reason           = '制冷机组压缩机振动值超标，需更换压缩机轴承组件',
  handled_by       = 19,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 2 DAY)
WHERE id BETWEEN 77 AND 84;

-- id 85-130: 近期记录，已审批或待处理中
UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'INSPECTION',
  priority_level   = 'LOW',
  status           = 'COMPLETED',
  estimated_cost   = 560.00,
  reason           = '锅炉水位传感器信号轻微漂移，建议校准或更换',
  handled_by       = 17,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 3 DAY)
WHERE id BETWEEN 85 AND 96;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'PREVENTIVE',
  priority_level   = 'MEDIUM',
  status           = 'COMPLETED',
  estimated_cost   = 1800.00,
  reason           = 'PLC数字量输入模块通道信号不稳，建议预防性更换模块',
  handled_by       = 17,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 2 DAY)
WHERE id BETWEEN 97 AND 108;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'EMERGENCY',
  priority_level   = 'HIGH',
  status           = 'ACCEPTED',
  estimated_cost   = 12000.00,
  reason           = '灌装阀阀芯磨损导致灌装量波动超过±2%，影响产品合规性',
  handled_by       = 1,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 1 DAY)
WHERE id BETWEEN 109 AND 118;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'REPLACEMENT',
  priority_level   = 'MEDIUM',
  status           = 'COMPLETED',
  estimated_cost   = 3200.00,
  reason           = '码垛机械臂直线导轨润滑不足，建议注脂并检查导轨磨损',
  handled_by       = 17,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 3 DAY)
WHERE id BETWEEN 119 AND 128;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'INSPECTION',
  priority_level   = 'MEDIUM',
  status           = 'ACCEPTED',
  estimated_cost   = 750.00,
  reason           = '开山螺杆空压机运行噪音偏大，建议全面检查减震元件及皮带',
  handled_by       = 19,
  handled_at       = DATE_ADD(suggestion_date, INTERVAL 2 DAY)
WHERE id BETWEEN 129 AND 138;

-- id 139-185: 最新记录，少量待处理
UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'PREVENTIVE',
  priority_level   = 'LOW',
  status           = 'PENDING',
  estimated_cost   = 480.00,
  reason           = '套标收缩机加热管温控传感器响应偏慢，建议巡检维护'
WHERE id BETWEEN 139 AND 148;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'REPLACEMENT',
  priority_level   = 'HIGH',
  status           = 'PENDING',
  estimated_cost   = 8500.00,
  reason           = 'SKF深沟球轴承使用时长超过额定寿命2000小时，建议更换'
WHERE id BETWEEN 149 AND 158;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'INSPECTION',
  priority_level   = 'LOW',
  status           = 'PENDING',
  estimated_cost   = 350.00,
  reason           = '发电机组蓄电池内阻升高，建议测试放电能力并评估更换'
WHERE id BETWEEN 159 AND 168;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'PREVENTIVE',
  priority_level   = 'MEDIUM',
  status           = 'PENDING',
  estimated_cost   = 2100.00,
  reason           = '阿法拉伐CIP清洗站碱液泵密封圈出现轻微渗漏，建议在停机时更换'
WHERE id BETWEEN 169 AND 178;

UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'EMERGENCY',
  priority_level   = 'HIGH',
  status           = 'PENDING',
  estimated_cost   = 18000.00,
  reason           = '君科制冷机组压缩机出现异常高温保护，频繁跳停影响酒体温控，需紧急检修'
WHERE id BETWEEN 179 AND 185;

-- =====================================================================
-- 第二步：修正 ai_device_health 健康评分
--   让每台设备在不同日期的评分有真实波动，而不是全部固定值
--   策略：按设备性质和日期给予随机偏移，呈现健康度的起伏趋势
-- =====================================================================

-- 灌装机(1)：近期维修多，评分偏低且波动大（范围35-72）
UPDATE ai_device_health SET
  health_score = ROUND(55 + (id % 13) * 1.3 - (id % 7) * 0.9, 2),
  risk_level   = CASE
    WHEN ROUND(55 + (id % 13) * 1.3 - (id % 7) * 0.9, 2) < 40 THEN 'CRITICAL'
    WHEN ROUND(55 + (id % 13) * 1.3 - (id % 7) * 0.9, 2) < 60 THEN 'HIGH'
    WHEN ROUND(55 + (id % 13) * 1.3 - (id % 7) * 0.9, 2) < 75 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 1;

-- 旋盖机(2)：评分中等偏上，较稳定（范围55-82）
UPDATE ai_device_health SET
  health_score = ROUND(68 + (id % 11) * 1.2 - (id % 5) * 0.7, 2),
  risk_level   = CASE
    WHEN ROUND(68 + (id % 11) * 1.2 - (id % 5) * 0.7, 2) < 60 THEN 'HIGH'
    WHEN ROUND(68 + (id % 11) * 1.2 - (id % 5) * 0.7, 2) < 75 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 2;

-- 套标机(3)：评分较高，偶有轻微异常（范围70-90）
UPDATE ai_device_health SET
  health_score = ROUND(79 + (id % 9) * 1.1 - (id % 4) * 0.5, 2),
  risk_level   = CASE
    WHEN ROUND(79 + (id % 9) * 1.1 - (id % 4) * 0.5, 2) < 75 THEN 'MEDIUM'
    WHEN ROUND(79 + (id % 9) * 1.1 - (id % 4) * 0.5, 2) < 85 THEN 'LOW'
    ELSE 'NORMAL'
  END
WHERE device_id = 3;

-- 贴标机(4)：评分中等（范围60-80）
UPDATE ai_device_health SET
  health_score = ROUND(70 + (id % 8) * 1.0 - (id % 6) * 0.8, 2),
  risk_level   = CASE
    WHEN ROUND(70 + (id % 8) * 1.0 - (id % 6) * 0.8, 2) < 65 THEN 'HIGH'
    WHEN ROUND(70 + (id % 8) * 1.0 - (id % 6) * 0.8, 2) < 75 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 4;

-- 阿特拉斯空压机(5)：评分偏低，历史故障多（范围42-68）
UPDATE ai_device_health SET
  health_score = ROUND(55 + (id % 12) * 1.1 - (id % 8) * 1.2, 2),
  risk_level   = CASE
    WHEN ROUND(55 + (id % 12) * 1.1 - (id % 8) * 1.2, 2) < 45 THEN 'CRITICAL'
    WHEN ROUND(55 + (id % 12) * 1.1 - (id % 8) * 1.2, 2) < 60 THEN 'HIGH'
    ELSE 'MEDIUM'
  END
WHERE device_id = 5;

-- PLC控制柜(6)：评分稳定偏高（范围72-88）
UPDATE ai_device_health SET
  health_score = ROUND(80 + (id % 7) * 1.1 - (id % 3) * 0.4, 2),
  risk_level   = CASE
    WHEN ROUND(80 + (id % 7) * 1.1 - (id % 3) * 0.4, 2) < 75 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 6;

-- 视觉检测系统(7)：评分高且稳定（范围78-92）
UPDATE ai_device_health SET
  health_score = ROUND(85 + (id % 6) * 1.2 - (id % 4) * 0.6, 2),
  risk_level   = CASE
    WHEN ROUND(85 + (id % 6) * 1.2 - (id % 4) * 0.6, 2) < 80 THEN 'LOW'
    ELSE 'NORMAL'
  END
WHERE device_id = 7;

-- 方快锅炉(8)：评分较低，定期保养但老化明显（范围45-70）
UPDATE ai_device_health SET
  health_score = ROUND(58 + (id % 10) * 1.2 - (id % 7) * 1.0, 2),
  risk_level   = CASE
    WHEN ROUND(58 + (id % 10) * 1.2 - (id % 7) * 1.0, 2) < 50 THEN 'CRITICAL'
    WHEN ROUND(58 + (id % 10) * 1.2 - (id % 7) * 1.0, 2) < 63 THEN 'HIGH'
    ELSE 'MEDIUM'
  END
WHERE device_id = 8;

-- 开山空压机(9)：评分中等（范围58-76）
UPDATE ai_device_health SET
  health_score = ROUND(67 + (id % 9) * 1.0 - (id % 5) * 0.8, 2),
  risk_level   = CASE
    WHEN ROUND(67 + (id % 9) * 1.0 - (id % 5) * 0.8, 2) < 62 THEN 'HIGH'
    WHEN ROUND(67 + (id % 9) * 1.0 - (id % 5) * 0.8, 2) < 72 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 9;

-- CIP清洗系统(10)：评分偏高（范围68-85）
UPDATE ai_device_health SET
  health_score = ROUND(76 + (id % 8) * 1.1 - (id % 4) * 0.5, 2),
  risk_level   = CASE
    WHEN ROUND(76 + (id % 8) * 1.1 - (id % 4) * 0.5, 2) < 72 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 10;

-- 蒸馏机(11)：评分中低，关键设备（范围50-72）
UPDATE ai_device_health SET
  health_score = ROUND(61 + (id % 11) * 1.0 - (id % 7) * 0.9, 2),
  risk_level   = CASE
    WHEN ROUND(61 + (id % 11) * 1.0 - (id % 7) * 0.9, 2) < 55 THEN 'HIGH'
    WHEN ROUND(61 + (id % 11) * 1.0 - (id % 7) * 0.9, 2) < 68 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 11;

-- 码垛线(12)：评分中等偏高（范围65-82）
UPDATE ai_device_health SET
  health_score = ROUND(73 + (id % 8) * 1.1 - (id % 5) * 0.7, 2),
  risk_level   = CASE
    WHEN ROUND(73 + (id % 8) * 1.1 - (id % 5) * 0.7, 2) < 68 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 12;

-- 发电机(13)：评分偏高（备用设备，使用率低）（范围74-90）
UPDATE ai_device_health SET
  health_score = ROUND(82 + (id % 7) * 1.0 - (id % 3) * 0.4, 2),
  risk_level   = CASE
    WHEN ROUND(82 + (id % 7) * 1.0 - (id % 3) * 0.4, 2) < 78 THEN 'LOW'
    ELSE 'NORMAL'
  END
WHERE device_id = 13;

-- 制冷机组(14)：评分较低，近期频繁跳停（范围38-62）
UPDATE ai_device_health SET
  health_score = ROUND(50 + (id % 13) * 0.9 - (id % 9) * 1.1, 2),
  risk_level   = CASE
    WHEN ROUND(50 + (id % 13) * 0.9 - (id % 9) * 1.1, 2) < 42 THEN 'CRITICAL'
    WHEN ROUND(50 + (id % 13) * 0.9 - (id % 9) * 1.1, 2) < 55 THEN 'HIGH'
    ELSE 'MEDIUM'
  END
WHERE device_id = 14;

-- 喷码机(15)：评分中等（范围62-80）
UPDATE ai_device_health SET
  health_score = ROUND(71 + (id % 8) * 1.1 - (id % 5) * 0.7, 2),
  risk_level   = CASE
    WHEN ROUND(71 + (id % 8) * 1.1 - (id % 5) * 0.7, 2) < 65 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE device_id = 15;

-- =====================================================================
-- 验证
-- =====================================================================
SELECT status, COUNT(*) cnt FROM biz_maintenance_suggestion GROUP BY status;
SELECT maintenance_type, COUNT(*) cnt FROM biz_maintenance_suggestion GROUP BY maintenance_type;
SELECT priority_level, COUNT(*) cnt FROM biz_maintenance_suggestion GROUP BY priority_level;
SELECT MIN(estimated_cost), MAX(estimated_cost), AVG(estimated_cost) FROM biz_maintenance_suggestion;
SELECT MIN(health_score), MAX(health_score), AVG(health_score) FROM ai_device_health;
SELECT device_id, MIN(health_score), MAX(health_score), AVG(health_score) FROM ai_device_health GROUP BY device_id ORDER BY device_id;
