package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SparePartCategory {
    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非数据库字段，用于前端树形结构
    private List<SparePartCategory> children;
}
