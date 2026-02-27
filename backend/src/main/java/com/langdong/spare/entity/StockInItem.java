package com.langdong.spare.entity;

import lombok.Data;

@Data
public class StockInItem {
    private Long id;
    private Long stockInReceiptId;
    private Long purchaseOrderItemId;
    private Long sparePartId;
    private Integer expectedQuantity;
    private Integer actualQuantity;
    private Integer shelvedQuantity;
    private Long locationId;
    private String remark;

    // 瞬态字段（联表查询用）
    private String receiptCode;
    private String sparePartCode;
    private String sparePartName;
}
