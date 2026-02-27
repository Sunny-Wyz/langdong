package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Requisition {
    private Long id;
    private String reqNo;
    private Long applicantId;
    private String workOrderNo;
    private Long deviceId;
    private String reqStatus; // PENDING, APPROVED, REJECTED, OUTBOUND, INSTALLED
    private Boolean isUrgent;
    private Long approveId;
    private LocalDateTime approveTime;
    private String approveRemark;
    private LocalDateTime applyTime;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Transient display fields for JOINs ---
    private String applicantName;
    private String approverName;
    private String deviceName;
    private String deviceCode;
}
