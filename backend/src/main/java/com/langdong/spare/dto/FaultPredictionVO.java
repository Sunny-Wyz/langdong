package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * 故障预测结果视图对象
 * 包含设备信息、预测结果、置信区间等
 */
@Data
public class FaultPredictionVO {
    /** 预测记录ID */
    private Long id;

    /** 设备ID */
    private Long deviceId;

    /** 设备编码 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /** 设备型号 */
    private String deviceModel;

    /** 预测生成日期 */
    private LocalDate predictionDate;

    /** 预测目标月份(yyyy-MM) */
    private String targetMonth;

    /** 预测故障次数 */
    private Integer predictedFaultCount;

    /** 故障概率(0-1) */
    private BigDecimal failureProbability;

    /** 故障次数置信区间下限 */
    private Integer faultCountLower;

    /** 故障次数置信区间上限 */
    private Integer faultCountUpper;

    /** 特征重要性 JSON字符串或Map */
    private Map<String, Object> featureImportance;

    /** 模型类型 */
    private String modelType;

    /** 当前健康评分(关联查询) */
    private BigDecimal currentHealthScore;

    /** 当前风险等级(关联查询) */
    private String currentRiskLevel;
}
