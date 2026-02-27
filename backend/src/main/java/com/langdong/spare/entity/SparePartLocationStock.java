package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SparePartLocationStock {
    private Long id;
    private Long locationId;
    private Long sparePartId;
    private Integer quantity;
    private LocalDateTime updatedAt;

    // 瞬态字段（联表查询用）
    private String sparePartCode;
    private String sparePartName;
    private String locationName;
    private String locationCode;
    private String locationZone;
}
