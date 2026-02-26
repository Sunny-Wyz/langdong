package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SparePartDTO {
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
}
