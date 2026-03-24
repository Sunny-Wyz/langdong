package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RequisitionItem {
    private Long id;
    private Long reqId;
    private Long sparePartId;
    private Integer applyQty;
    private Integer outQty;
    private String batchInfo;       // FIFO: 批次分配信息摘要（如：IN20240101[10件] + IN20240102[5件]）
    private String installLoc;
    private LocalDateTime installTime;
    private Long installerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Transient display fields for JOINs ---
    private String sparePartCode;
    private String sparePartName;
    private String installerName;
}
