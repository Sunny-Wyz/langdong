package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 设备健康记录视图对象
 * 用于API响应,包含设备基本信息和健康评估详情
 */
@Data
public class DeviceHealthVO {
    /** 记录ID */
    private Long id;

    /** 设备ID */
    private Long deviceId;

    /** 设备编码 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 设备型号 */
    private String deviceModel;

    /** 设备重要性 */
    private String importanceLevel;

    /** 记录日期 */
    private LocalDate recordDate;

    /** 健康评分(0-100) */
    private BigDecimal healthScore;

    /** 风险等级(LOW/MEDIUM/HIGH/CRITICAL) */
    private String riskLevel;

    /** 运行时长评分 */
    private BigDecimal runtimeScore;

    /** 故障评分 */
    private BigDecimal faultScore;

    /** 工单评分 */
    private BigDecimal workorderScore;

    /** 换件评分 */
    private BigDecimal replacementScore;

    /** 预测剩余天数 */
    private Integer predictedFailureDays;

    /** 预测置信度(0-1) */
    private BigDecimal confidenceLevel;

    /** 算法版本 */
    private String algorithmVersion;
}
