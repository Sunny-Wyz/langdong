package com.langdong.spare.dto;

import lombok.Data;
import java.util.List;

@Data
public class StockInRequestDTO {
    private String purchaseOrderCode;
    private String remark;
    private Boolean allowOverReceive; // 是否允许超收
    private List<StockInItemDTO> items;
}
