package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SupplierCategoryRelation {
    private Long id;
    private Long supplierId;
    private Long supplyCategoryId;
    private LocalDateTime createdAt;
}
