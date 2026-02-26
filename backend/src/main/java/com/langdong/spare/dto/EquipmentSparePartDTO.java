package com.langdong.spare.dto;

import lombok.Data;

@Data
public class EquipmentSparePartDTO {
    private Long equipmentId;
    private Long sparePartId;
    private Integer quantity;
}
