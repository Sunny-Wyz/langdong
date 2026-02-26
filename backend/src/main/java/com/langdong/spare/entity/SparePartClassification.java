package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SparePartClassification {
    private Long id;
    private Long sparePartId;
    private String abcCategory;
    private String xyzCategory;
    private String combinationCode;
    private BigDecimal abcScore;
    private BigDecimal xyzCv;
    private BigDecimal predictedDemand;
    private BigDecimal costScore;
    private BigDecimal criticalScore;
    private BigDecimal leadTimeScore;
    private BigDecimal difficultyScore;
    private Integer isManualAdjusted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
