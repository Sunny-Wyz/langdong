package com.langdong.spare.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class PurchaseOrderCreateDTO {
    private Long sparePartId;
    private Long supplierId;
    private Integer orderQty;
    private LocalDate expectedDate;
    private Long reorderSuggestId; // nullable, set when triggered from suggestion
    private String remark;
}
