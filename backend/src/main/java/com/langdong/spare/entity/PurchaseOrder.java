package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PurchaseOrder {
    private Long id;
    private String poCode;
    private Long supplierId;
    private String status;
    private BigDecimal totalAmount;
    private LocalDate expectedDeliveryDate;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
}
