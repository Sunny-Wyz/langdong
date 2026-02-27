package com.langdong.spare.dto;

import lombok.Data;
import java.util.List;

@Data
public class RequisitionCreateDTO {
    private String workOrderNo;
    private Long deviceId;
    private Boolean isUrgent;
    private String remark;
    private List<RequisitionItemDTO> items;

    @Data
    public static class RequisitionItemDTO {
        private Long sparePartId;
        private Integer applyQty;
    }
}
