package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Menu {
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String permission;
    private Integer type; // 1目录 2菜单 3按钮
    private String icon;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非数据库字段
    private java.util.List<Menu> children;
}
