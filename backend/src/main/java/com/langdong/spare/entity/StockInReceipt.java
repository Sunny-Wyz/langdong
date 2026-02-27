package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockInReceipt {
    private Long id;
    private String receiptCode;
    private Long purchaseOrderId;
    private LocalDateTime receiptDate;
    private String status;
    private Long handlerId;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非数据库映射字段
    private String handlerName;
    private String purchaseOrderCode;
}
