package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 健康监控仪表盘视图对象
 * 汇总统计数据供前端展示
 */
@Data
public class HealthDashboardVO {
    /** 设备总数 */
    private Long totalDevices;

    /** 健康设备数(LOW风险) */
    private Long healthyDevices;

    /** 预警设备数(MEDIUM风险) */
    private Long warningDevices;

    /** 高风险设备数(HIGH风险) */
    private Long highRiskDevices;

    /** 严重风险设备数(CRITICAL风险) */
    private Long criticalDevices;

    /** 平均健康评分 */
    private BigDecimal avgHealthScore;

    /** 风险分布 Map<RiskLevel, Count> */
    private Map<String, Long> riskDistribution;

    /** 最近评估时间 */
    private String lastEvaluationTime;

    /** 今日新增预警数量 */
    private Long todayNewAlerts;
}
