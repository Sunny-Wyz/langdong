package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WorkOrderCompleteDTO {
    private LocalDateTime actualFinish; // 必填，实际完成时间
    private BigDecimal laborCost;       // 人工费用
    private BigDecimal outsourceCost;   // 外协费用
}
