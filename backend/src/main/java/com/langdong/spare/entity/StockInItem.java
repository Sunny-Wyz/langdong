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
    private Long locationId;
    private String remark;
}
