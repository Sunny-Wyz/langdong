package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockInItem {
    private Long id;
    private Long stockInReceiptId;
    private Long purchaseOrderItemId;
    private Long sparePartId;
    private Integer expectedQuantity;
    private Integer actualQuantity;
    private Integer remainingQty;      // FIFO: 批次剩余可用数量
    private Integer shelvedQuantity;
    private Long locationId;
    private LocalDateTime inTime;       // FIFO: 入库时间（排序依据）
    private String remark;

    // 瞬态字段（联表查询用）
    private String receiptCode;
    private String sparePartCode;
    private String sparePartName;
}
