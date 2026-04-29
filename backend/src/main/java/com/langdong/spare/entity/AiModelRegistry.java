package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 模型注册表实体
 * 对应表：ai_model_registry
 */
@Data
public class AiModelRegistry {
    private Long id;
    private String modelName;
    private String modelVersion;
    private String algoType;
    private String mlflowRunId;
    private String artifactPath;
    private BigDecimal mae;
    private BigDecimal rmse;
    private BigDecimal mase;
    private BigDecimal crps;
    private Integer trainParts;
    private Integer trainWeeks;
    private String status;
    private LocalDateTime deployTime;
    private LocalDateTime createTime;
}
