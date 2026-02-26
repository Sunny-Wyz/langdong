package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Role {
    private Long id;
    private String code;
    private String name;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
