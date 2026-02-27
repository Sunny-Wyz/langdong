package com.langdong.spare.dto;

import lombok.Data;

@Data
public class StockInItemDTO {
    private Long sparePartId;
    private Integer actualQuantity;
    private Long locationId;
    private String remark;
}
