package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ReorderSuggest {
    private Long id;
    private String partCode;
    private String suggestMonth;
    private Integer currentStock;
    private Integer reorderPoint;
    private Integer suggestQty;
    private BigDecimal forecastQty;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;
    private String urgency; // 紧急/正常
    private String status; // 待处理/已采购/已忽略
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- JOIN display fields ---
    private String sparePartName;
}
