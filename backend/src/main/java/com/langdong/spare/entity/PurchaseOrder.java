package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PurchaseOrder {
    private Long id;
    private String orderNo;
    private Long sparePartId;
    private Long supplierId;
    private Integer orderQty;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String orderStatus; // 已下单/已发货/到货/验收通过/验收失败
    private LocalDate expectedDate;
    private LocalDate actualDate;
    private Long reorderSuggestId;
    private Long purchaserId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- JOIN display fields ---
    private String sparePartCode;
    private String sparePartName;
    private String supplierName;
    private String purchaserName;
}
