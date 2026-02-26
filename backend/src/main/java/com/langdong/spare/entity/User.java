package com.langdong.spare.entity;

import lombok.Data;

@Data
public class User {
    private Long id;
    private String username;
    private String name;
    private String password;
    private Integer status;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
