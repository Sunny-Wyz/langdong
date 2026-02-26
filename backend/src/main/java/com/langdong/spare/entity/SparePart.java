package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SparePart {
    private Long id;
    private String code;
    private String name;
    private String model;
    private Integer quantity;
    private String unit;
    private BigDecimal price;
    private Long categoryId;
    private String supplier;
    private String remark;
    private Long locationId;
    private Long supplierId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer leadTime;
    private String criticality;
    private String substitutionDifficulty;
}
