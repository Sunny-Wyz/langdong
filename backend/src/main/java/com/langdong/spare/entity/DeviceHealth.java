package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备健康评估记录实体
 * 对应数据库表：ai_device_health
 *
 * 该表记录设备的健康评分历史数据，支持趋势分析和风险监控
 * 每日定时任务会为所有设备生成一条最新的健康评估记录
 */
@Data
public class DeviceHealth {

    /** 主键 */
    private Long id;

    /** 设备ID（关联 equipment.id） */
    private Long deviceId;

    /** 记录日期 */
    private LocalDate recordDate;

    /** 健康评分（0-100分） */
    private BigDecimal healthScore;

    /** 风险等级：LOW/MEDIUM/HIGH/CRITICAL */
    private String riskLevel;

    // ---- 评分维度明细 ----

    /** 运行时长评分（0-100） */
    private BigDecimal runtimeScore;

    /** 故障频次评分（0-100） */
    private BigDecimal faultScore;

    /** 工单数量评分（0-100） */
    private BigDecimal workorderScore;

    /** 换件频次评分（0-100） */
    private BigDecimal replacementScore;

    // ---- 预测结果 ----

    /** 预测剩余健康天数（可选） */
    private Integer predictedFailureDays;

    /** 预测置信度（0-1） */
    private BigDecimal confidenceLevel;

    /** 算法版本标识 */
    private String algorithmVersion;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    // ---- 联查字段（不存储在本表） ----

    /** 设备编码（联查 equipment.code） */
    private String deviceCode;

    /** 设备名称（联查 equipment.name） */
    private String deviceName;

    /** 设备重要性（联查 equipment.importance_level） */
    private String deviceImportance;
}
