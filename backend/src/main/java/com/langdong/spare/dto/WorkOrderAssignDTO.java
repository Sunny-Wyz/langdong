package com.langdong.spare.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WorkOrderAssignDTO {
    private Long assigneeId;          // 必填，维修人员ID
    private LocalDateTime planFinish; // 必填，计划完成时间
}
