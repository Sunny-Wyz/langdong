package com.langdong.spare.dto;

import lombok.Data;

@Data
public class AcceptanceDTO {
    private Integer receivedQty; // 实际到货数量
    private Boolean qualified; // 质量是否合格
    private String remark; // 验收意见/退换货原因
}
