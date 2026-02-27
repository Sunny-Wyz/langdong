package com.langdong.spare.dto;

import lombok.Data;
import java.util.List;

@Data
public class RequisitionOutboundDTO {
    private List<RequisitionOutboundItemDTO> items;

    @Data
    public static class RequisitionOutboundItemDTO {
        private Long itemId;
        private Integer outQty;
    }
}
