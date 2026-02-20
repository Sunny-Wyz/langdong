package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SparePart {
    private Long id;
    private String name;
    private String model;
    private Integer quantity;
    private String unit;
    private BigDecimal price;
    private String category;
    private String supplier;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
