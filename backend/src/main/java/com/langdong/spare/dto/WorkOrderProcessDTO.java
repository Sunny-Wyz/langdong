package com.langdong.spare.dto;

import lombok.Data;

@Data
public class WorkOrderProcessDTO {
    private String faultCause;    // 故障根因分析
    private String repairMethod;  // 维修方案描述
}
