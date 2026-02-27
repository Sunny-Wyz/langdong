package com.langdong.spare.dto;

import lombok.Data;

@Data
public class StockInItemDTO {
    private Long poItemId; // 采购单明细ID（用于精确匹配，避免同一备件多条明细混淆）
    private Long sparePartId;
    private Integer actualQuantity;
    private Long locationId;
    private String remark;
}
