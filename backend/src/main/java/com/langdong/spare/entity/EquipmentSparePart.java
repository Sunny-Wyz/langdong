package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EquipmentSparePart {
    private Long id;
    private Long equipmentId;
    private Long sparePartId;
    private Integer quantity;
    private LocalDateTime createdAt;
}
