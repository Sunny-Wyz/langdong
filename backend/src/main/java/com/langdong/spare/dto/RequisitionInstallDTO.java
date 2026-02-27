package com.langdong.spare.dto;

import lombok.Data;
import java.util.List;

@Data
public class RequisitionInstallDTO {
    private List<RequisitionInstallItemDTO> items;

    @Data
    public static class RequisitionInstallItemDTO {
        private Long itemId;
        private String installLoc;
    }
}
