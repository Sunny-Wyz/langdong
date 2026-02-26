package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ClassificationAdjustmentRecord {
    private Long id;
    private Long sparePartId;
    private String originalCombination;
    private String newCombination;
    private String reason;
    private Long applicantId;
    private Long approverId;
    private String status;
    private String approvalRemark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
