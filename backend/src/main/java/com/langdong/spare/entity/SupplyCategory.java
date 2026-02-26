package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SupplyCategory {
    private Long id;
    private String code;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
