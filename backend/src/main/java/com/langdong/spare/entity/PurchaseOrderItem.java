package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PurchaseOrderItem {
    private Long id;
    private Long purchaseOrderId;
    private Long sparePartId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private Integer receivedQuantity;
    private String remark;

    // 非数据库映射字段，用于返回给前端关联备件的信息
    private String sparePartCode;
    private String sparePartName;
}
