package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Supplier {
    private Long id;
    private String code;
    private String name;
    private String unifiedSocialCreditCode;
    private String bankAccountInfo;
    private String contactPerson;
    private String phone;
    private String address;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非数据库映射字段，用于前端展示关联的品类
    private List<SupplyCategory> categories;
}
