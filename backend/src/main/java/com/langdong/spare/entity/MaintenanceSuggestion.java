package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预防性维护建议实体
 * 对应数据库表：biz_maintenance_suggestion
 *
 * 该表存储系统自动生成的维护建议，包括建议类型、优先级、时间窗口和关联备件
 * 用户可以采纳（自动创建工单和领用单）或拒绝建议
 */
@Data
public class MaintenanceSuggestion {

    /** 主键 */
    private Long id;

    /** 设备ID（关联 equipment.id） */
    private Long deviceId;

    /** 健康记录ID（关联 ai_device_health.id） */
    private Long healthRecordId;

    /** 建议生成日期 */
    private LocalDate suggestionDate;

    /** 维护类型：PREVENTIVE（预防性）/ PREDICTIVE（预测性）/ EMERGENCY（紧急） */
    private String maintenanceType;

    /** 优先级：HIGH / MEDIUM / LOW */
    private String priorityLevel;

    /** 建议维护开始日期 */
    private LocalDate suggestedStartDate;

    /** 建议维护结束日期 */
    private LocalDate suggestedEndDate;

    /** 关联备件需求列表（JSON格式：[{partId: 1, qty: 2}, {partId: 5, qty: 1}]） */
    private String relatedSpareParts;

    /** 预估维护成本（元） */
    private BigDecimal estimatedCost;

    /** 建议状态：PENDING（待处理）/ ACCEPTED（已采纳）/ REJECTED（已拒绝）/ COMPLETED（已完成） */
    private String status;

    /** 关联工单ID（采纳后自动创建） */
    private Long workorderId;

    /** 关联领用单ID（采纳后自动创建） */
    private Long requisitionId;

    /** 建议原因说明 */
    private String reason;

    /** 拒绝原因（状态为REJECTED时填写） */
    private String rejectReason;

    /** 处理人ID */
    private Long handledBy;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录更新时间 */
    private LocalDateTime updatedAt;

    // ---- 联查字段（不存储在本表） ----

    /** 设备编码（联查 equipment.code） */
    private String deviceCode;

    /** 设备名称（联查 equipment.name） */
    private String deviceName;

    /** 设备重要性（联查 equipment.importance_level） */
    private String deviceImportance;

    /** 当前健康评分（联查 ai_device_health.health_score） */
    private BigDecimal currentHealthScore;

    /** 当前风险等级（联查 ai_device_health.risk_level） */
    private String currentRiskLevel;

    /** 故障概率（联查 ai_fault_prediction.failure_probability） */
    private BigDecimal failureProbability;

    /** 处理人姓名（联查 user.username） */
    private String handlerName;
}
