package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 维护建议视图对象
 * 包含建议详情、关联设备信息、备件需求等
 */
@Data
public class MaintenanceSuggestionVO {
    /** 建议ID */
    private Long id;

    /** 设备ID */
    private Long deviceId;

    /** 设备编码 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 设备型号 */
    private String deviceModel;

    /** 健康记录ID */
    private Long healthRecordId;

    /** 当前健康评分 */
    private BigDecimal healthScore;

    /** 当前风险等级 */
    private String riskLevel;

    /** 故障概率 */
    private BigDecimal failureProbability;

    /** 建议日期 */
    private LocalDate suggestionDate;

    /** 维护类型(PREVENTIVE/PREDICTIVE/EMERGENCY) */
    private String maintenanceType;

    /** 优先级(HIGH/MEDIUM/LOW) */
    private String priorityLevel;

    /** 建议开始日期 */
    private LocalDate suggestedStartDate;

    /** 建议结束日期 */
    private LocalDate suggestedEndDate;

    /** 关联备件列表 */
    private List<Map<String, Object>> relatedSpareParts;

    /** 预估成本 */
    private BigDecimal estimatedCost;

    /** 状态(PENDING/ACCEPTED/REJECTED/COMPLETED) */
    private String status;

    /** 关联工单ID */
    private Long workorderId;

    /** 关联领用单ID */
    private Long requisitionId;

    /** 建议原因 */
    private String reason;

    /** 拒绝原因 */
    private String rejectReason;

    /** 处理人ID */
    private Long handledBy;

    /** 处理人姓名 */
    private String handledByName;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
