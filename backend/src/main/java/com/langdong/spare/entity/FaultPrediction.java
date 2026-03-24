package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备故障预测结果实体
 * 对应数据库表：ai_fault_prediction
 *
 * 该表存储基于历史数据预测的设备故障概率和预期故障次数
 * 定时任务会为高风险设备生成未来90天的故障预测记录
 */
@Data
public class FaultPrediction {

    /** 主键 */
    private Long id;

    /** 设备ID（关联 equipment.id） */
    private Long deviceId;

    /** 预测生成日期 */
    private LocalDate predictionDate;

    /** 预测目标月份（格式：yyyy-MM） */
    private String targetMonth;

    /** 预测故障次数（期望值） */
    private Integer predictedFaultCount;

    /** 故障概率（0-1） */
    private BigDecimal failureProbability;

    /** 故障次数置信区间下限（90%置信度） */
    private Integer faultCountLower;

    /** 故障次数置信区间上限（90%置信度） */
    private Integer faultCountUpper;

    /** 特征重要性（JSON格式：{runHours: 0.3, faultCount: 0.4, mtbf: 0.2, deterioration: 0.1}） */
    private String featureImportance;

    /** 预测模型类型（LOGISTIC_REGRESSION / RANDOM_FOREST） */
    private String modelType;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    // ---- 联查字段（不存储在本表） ----

    /** 设备编码（联查 equipment.code） */
    private String deviceCode;

    /** 设备名称（联查 equipment.name） */
    private String deviceName;

    /** 当前健康评分（联查 ai_device_health.health_score） */
    private BigDecimal currentHealthScore;

    /** 当前风险等级（联查 ai_device_health.risk_level） */
    private String currentRiskLevel;
}
