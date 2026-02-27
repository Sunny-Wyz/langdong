package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WorkOrder {
    private Long id;
    private String workOrderNo;
    private Long deviceId;
    private Long reporterId;
    private String faultDesc;
    private String faultLevel;   // 紧急 / 一般 / 计划
    private String orderStatus;  // 报修 / 已派工 / 维修中 / 完工
    private Long assigneeId;
    private LocalDateTime planFinish;
    private LocalDateTime actualFinish;
    private String faultCause;
    private String repairMethod;
    private Integer mttrMinutes;
    private BigDecimal partCost;
    private BigDecimal laborCost;
    private BigDecimal outsourceCost;
    private LocalDateTime reportTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Transient display fields for JOINs ---
    private String reporterName;
    private String assigneeName;
    private String deviceName;
    private String deviceCode;
}
