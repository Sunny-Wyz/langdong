package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

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
}
