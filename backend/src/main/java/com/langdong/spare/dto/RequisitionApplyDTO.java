package com.langdong.spare.dto;

import lombok.Data;
import java.util.List;

@Data
public class RequisitionApplyDTO {
    private String workOrderNo;
    private Long deviceId;
    private Boolean isUrgent;
    private String remark;
    private List<RequisitionApplyItemDTO> items;

    @Data
    public static class RequisitionApplyItemDTO {
        private Long sparePartId;
        private Integer applyQty;
    }
}
