package com.langdong.spare.dto;

import lombok.Data;

@Data
public class RequisitionApproveDTO {
    private String action; // "APPROVE" or "REJECT"
    private String remark;
}
