package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SparePartStock {
    private Long id;
    private Long sparePartId;
    private Integer quantity;
    private LocalDateTime updatedAt;
    // 联查字段
    private String sparePartCode;
    private String sparePartName;
}
