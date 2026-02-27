package com.langdong.spare.dto;

import lombok.Data;

@Data
public class WorkOrderReportDTO {
    private Long deviceId;       // 必填，故障设备
    private String faultDesc;    // 必填，故障描述
    private String faultLevel;   // 必填：紧急 / 一般 / 计划
}
