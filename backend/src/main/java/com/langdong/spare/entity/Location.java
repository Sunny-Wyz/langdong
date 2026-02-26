package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Location {
    private Long id;
    private String code;
    private String name;
    private String zone;
    private String capacity;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
