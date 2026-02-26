package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ClassificationStrategy {
    private Long id;
    private String combinationCode;
    private String abcCategory;
    private String xyzCategory;
    private BigDecimal safetyStockMultiplier;
    private Integer replenishmentCycle;
    private String approvalLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
