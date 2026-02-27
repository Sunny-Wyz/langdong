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
