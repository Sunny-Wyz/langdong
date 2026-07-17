-- =====================================================================
-- 维护建议管理：真实模拟数据修正
-- 问题：
--   1) 状态误用 APPROVED，系统标准为 ACCEPTED（前端「已采纳」读 ACCEPTED）
--   2) 维护类型混用 INSPECTION/REPLACEMENT，系统标准为 PREVENTIVE/PREDICTIVE/EMERGENCY
--   3) 预估成本按 id 区间整批相同；需按类型+设备+id 拉开差异
--   4) 健康评分/风险等级应与设备工况匹配（非 0 分）
-- 用法：
--   mysql -u admin -p spare_db < sql/fix_maintenance_suggestion_realistic.sql
-- =====================================================================
SET NAMES utf8mb4;
USE spare_db;

-- ---------------------------------------------------------------------
-- 1. 状态与维护类型对齐系统枚举
-- ---------------------------------------------------------------------
UPDATE biz_maintenance_suggestion
SET status = 'ACCEPTED'
WHERE status = 'APPROVED';

UPDATE biz_maintenance_suggestion
SET maintenance_type = 'PREDICTIVE'
WHERE maintenance_type IN ('REPLACEMENT', 'INSPECTION');

-- 若仍有非标准类型，兜底为预防性
UPDATE biz_maintenance_suggestion
SET maintenance_type = 'PREVENTIVE'
WHERE maintenance_type NOT IN ('EMERGENCY', 'PREDICTIVE', 'PREVENTIVE');

-- ---------------------------------------------------------------------
-- 2. 状态分布：约 25% 待处理 / 30% 已采纳 / 35% 已完成 / 10% 已拒绝
--    近期记录更偏待处理，早期更偏已完成
-- ---------------------------------------------------------------------
UPDATE biz_maintenance_suggestion SET
  status     = 'COMPLETED',
  handled_by = IFNULL(handled_by, 1 + (id % 3)),
  handled_at = IFNULL(handled_at, DATE_ADD(suggestion_date, INTERVAL 1 + (id % 5) DAY)),
  reject_reason = NULL
WHERE id % 10 IN (0, 1, 2, 3);

UPDATE biz_maintenance_suggestion SET
  status     = 'ACCEPTED',
  handled_by = IFNULL(handled_by, 1 + (id % 3)),
  handled_at = IFNULL(handled_at, DATE_ADD(suggestion_date, INTERVAL 1 + (id % 3) DAY)),
  reject_reason = NULL
WHERE id % 10 IN (4, 5, 6);

UPDATE biz_maintenance_suggestion SET
  status     = 'PENDING',
  handled_by = NULL,
  handled_at = NULL,
  reject_reason = NULL
WHERE id % 10 IN (7, 8);

UPDATE biz_maintenance_suggestion SET
  status        = 'REJECTED',
  handled_by    = IFNULL(handled_by, 1),
  handled_at    = IFNULL(handled_at, DATE_ADD(suggestion_date, INTERVAL 1 DAY)),
  reject_reason = CASE (id % 4)
    WHEN 0 THEN '备件库存充足且可临时周转，本周期暂缓实施'
    WHEN 1 THEN '费用超预算，待下月采购窗口再评估'
    WHEN 2 THEN '计划停机窗口冲突，改排至大修期统一处理'
    ELSE '设备工况已恢复，现场复核后无需本次维护'
  END
WHERE id % 10 = 9;

-- 最新约 20 条强制保留为待处理（贴近真实运营）
UPDATE biz_maintenance_suggestion s
JOIN (
  SELECT id FROM biz_maintenance_suggestion ORDER BY id DESC LIMIT 20
) t ON s.id = t.id
SET s.status = 'PENDING',
    s.handled_by = NULL,
    s.handled_at = NULL,
    s.reject_reason = NULL;

-- ---------------------------------------------------------------------
-- 3. 维护类型 / 优先级 / 原因 / 成本：按类型拉开差异
-- ---------------------------------------------------------------------

-- 紧急维护：高优先级，高成本
UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'EMERGENCY',
  priority_level   = 'HIGH',
  estimated_cost   = ROUND(9500 + (id % 29) * 480 + device_id * 220 + (id % 7) * 150, 2),
  reason = CASE (device_id % 5)
    WHEN 0 THEN CONCAT('设备健康评分偏低，预测故障风险较高，建议立即安排紧急检修，避免产线停机扩大损失（建议#', id, '）')
    WHEN 1 THEN CONCAT('关键部件振动/温升超阈值，存在突发性故障隐患，需紧急停机排查并更换易损件（建议#', id, '）')
    WHEN 2 THEN CONCAT('冷却/润滑系统工况异常，连续告警，建议紧急维护恢复运行稳定性（建议#', id, '）')
    WHEN 3 THEN CONCAT('密封与轴承磨损迹象明显，泄漏/噪音加剧，建议紧急更换关键备件（建议#', id, '）')
    ELSE CONCAT('控制系统通信/驱动模块异常频发，建议紧急诊断并更换故障模块（建议#', id, '）')
  END
WHERE id % 5 = 0;

-- 预测性维护：中高优先级，中等成本
UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'PREDICTIVE',
  priority_level   = IF(id % 3 = 0, 'HIGH', 'MEDIUM'),
  estimated_cost   = ROUND(2800 + (id % 23) * 260 + device_id * 120 + (id % 5) * 90, 2),
  reason = CASE (device_id % 4)
    WHEN 0 THEN CONCAT('基于健康趋势与故障概率模型，建议在计划窗口内开展预测性维护，更换即将到期的易损件（建议#', id, '）')
    WHEN 1 THEN CONCAT('运行小时与历史故障间隔显示劣化加速，建议预测性更换密封/轴承/滤芯组合（建议#', id, '）')
    WHEN 2 THEN CONCAT('传感器与执行机构响应漂移，建议预测性校准与局部更换，降低下周故障概率（建议#', id, '）')
    ELSE CONCAT('MTBF 呈下降趋势，建议安排预测性检修并复核润滑与对中（建议#', id, '）')
  END
WHERE id % 5 IN (1, 2);

-- 预防性维护：中低优先级，较低成本
UPDATE biz_maintenance_suggestion SET
  maintenance_type = 'PREVENTIVE',
  priority_level   = IF(id % 4 = 0, 'MEDIUM', 'LOW'),
  estimated_cost   = ROUND(380 + (id % 19) * 95 + device_id * 55 + (id % 6) * 40, 2),
  reason = CASE (device_id % 4)
    WHEN 0 THEN CONCAT('例行预防性保养：清洁、润滑、紧固与状态点检，维持设备可用率（建议#', id, '）')
    WHEN 1 THEN CONCAT('预防性检查光电/接近开关灵敏度与线缆老化情况，必要时更换（建议#', id, '）')
    WHEN 2 THEN CONCAT('预防性更换耗材（滤芯、密封圈、皮带），避免小故障演变为停机（建议#', id, '）')
    ELSE CONCAT('按维护周期执行预防性巡检，记录运行参数并评估下月备件需求（建议#', id, '）')
  END
WHERE id % 5 IN (3, 4);

-- 保证成本在合理业务区间
UPDATE biz_maintenance_suggestion
SET estimated_cost = GREATEST(280, LEAST(estimated_cost, 35000));

-- ---------------------------------------------------------------------
-- 4. 健康评分：消除 0 分，按设备工况制造真实波动
-- ---------------------------------------------------------------------
UPDATE ai_device_health SET
  health_score = ROUND(
    LEAST(98, GREATEST(28,
      52
      + (device_id % 9) * 3.2
      - (id % 13) * 1.4
      + (id % 7) * 0.9
      + CASE
          WHEN device_id IN (1, 5, 8, 14) THEN -8
          WHEN device_id IN (7, 13) THEN 12
          ELSE 0
        END
    )), 2),
  risk_level = CASE
    WHEN ROUND(LEAST(98, GREATEST(28, 52 + (device_id % 9) * 3.2 - (id % 13) * 1.4 + (id % 7) * 0.9
      + CASE WHEN device_id IN (1, 5, 8, 14) THEN -8 WHEN device_id IN (7, 13) THEN 12 ELSE 0 END)), 2) < 40 THEN 'CRITICAL'
    WHEN ROUND(LEAST(98, GREATEST(28, 52 + (device_id % 9) * 3.2 - (id % 13) * 1.4 + (id % 7) * 0.9
      + CASE WHEN device_id IN (1, 5, 8, 14) THEN -8 WHEN device_id IN (7, 13) THEN 12 ELSE 0 END)), 2) < 60 THEN 'HIGH'
    WHEN ROUND(LEAST(98, GREATEST(28, 52 + (device_id % 9) * 3.2 - (id % 13) * 1.4 + (id % 7) * 0.9
      + CASE WHEN device_id IN (1, 5, 8, 14) THEN -8 WHEN device_id IN (7, 13) THEN 12 ELSE 0 END)), 2) < 80 THEN 'MEDIUM'
    ELSE 'LOW'
  END
WHERE health_score IS NULL OR health_score <= 0 OR health_score > 100
   OR risk_level IS NULL OR risk_level = '';

-- 全量轻量扰动：确保列表联查分数非 0 且有差异（保留已有合理分数）
UPDATE ai_device_health
SET health_score = ROUND(
      LEAST(98, GREATEST(30,
        IF(health_score IS NULL OR health_score <= 0, 55, health_score)
        + ((id % 5) - 2) * 0.35
      )), 2)
WHERE health_score IS NULL OR health_score <= 0;

-- 同步 risk_level 与分数
UPDATE ai_device_health
SET risk_level = CASE
  WHEN health_score < 40 THEN 'CRITICAL'
  WHEN health_score < 60 THEN 'HIGH'
  WHEN health_score < 80 THEN 'MEDIUM'
  ELSE 'LOW'
END;

-- 故障概率：列表若联查到则不为空
UPDATE ai_fault_prediction
SET failure_probability = ROUND(
  LEAST(0.95, GREATEST(0.05,
    0.18 + (device_id % 7) * 0.06 + (id % 11) * 0.03
  )), 4)
WHERE failure_probability IS NULL OR failure_probability <= 0;

-- ---------------------------------------------------------------------
-- 5. 校验
-- ---------------------------------------------------------------------
SELECT 'status_dist' AS check_item, status, COUNT(*) cnt
FROM biz_maintenance_suggestion GROUP BY status;

SELECT 'type_dist' AS check_item, maintenance_type, COUNT(*) cnt
FROM biz_maintenance_suggestion GROUP BY maintenance_type;

SELECT 'priority_dist' AS check_item, priority_level, COUNT(*) cnt
FROM biz_maintenance_suggestion GROUP BY priority_level;

SELECT 'cost_range' AS check_item,
       MIN(estimated_cost) min_c,
       MAX(estimated_cost) max_c,
       ROUND(AVG(estimated_cost), 2) avg_c,
       COUNT(DISTINCT ROUND(estimated_cost, 0)) distinct_costs
FROM biz_maintenance_suggestion;

SELECT 'health_range' AS check_item,
       MIN(health_score) min_h,
       MAX(health_score) max_h,
       ROUND(AVG(health_score), 2) avg_h,
       SUM(CASE WHEN health_score = 0 THEN 1 ELSE 0 END) zero_cnt
FROM ai_device_health;

SELECT 'sample' AS check_item,
       s.id, s.status, s.maintenance_type, s.priority_level,
       s.estimated_cost, h.health_score, h.risk_level
FROM biz_maintenance_suggestion s
LEFT JOIN ai_device_health h ON s.health_record_id = h.id
ORDER BY s.id DESC
LIMIT 12;
