package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SupplierQuote {
    private Long id;
    private String orderNo;
    private Long supplierId;
    private Long sparePartId;
    private BigDecimal quotePrice;
    private LocalDateTime quoteTime;
    private Integer deliveryDays;
    private Boolean isSelected;
    private LocalDateTime createdAt;

    // --- JOIN display fields ---
    private String supplierName;
    private String sparePartName;
}
