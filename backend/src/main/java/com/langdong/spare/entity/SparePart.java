package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SparePart {
    private Long id;
    private String code;
    private String name;
    private String model;
    private Integer quantity;
    private String unit;
    private BigDecimal price;
    private Long categoryId;
    private String supplier;
    private String remark;
    private Long locationId;
    private Long supplierId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ---- 分类模块新增字段 ----

    /** 是否关键备件（1=关键，0=非关键），用于ABC分类计算 */
    private Integer isCritical;

    /** 供应替代难度（1~5分，5=极难），用于ABC分类计算 */
    private Integer replaceDiff;

    /** 采购提前期（天），用于ABC分类计算及SS/ROP计算 */
    private Integer leadTime;

    // ---- 兼容本地 windows 分支 SmartClassificationService 调用的字段 ----
    private String criticality;
    private String substitutionDifficulty;
}
