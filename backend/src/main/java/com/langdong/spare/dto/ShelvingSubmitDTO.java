package com.langdong.spare.dto;

import lombok.Data;
import java.util.List;

@Data
public class ShelvingSubmitDTO {
    private Long stockInItemId;
    private List<ShelvingDistribution> distributions;

    @Data
    public static class ShelvingDistribution {
        private Long locationId;
        private Integer putQty;
    }
}
