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
    /** 可选：关联设备，若填写则校验备件属于该设备配套 */
    private Long equipmentId;
}
