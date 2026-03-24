package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Equipment {
    private Long id;
    private String code;
    private String name;
    private String model;
    private String department;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // PHM模块扩展字段
    /** 设备重要性：CRITICAL（关键）/ IMPORTANT（重要）/ NORMAL（一般） */
    private String importanceLevel;

    /** 安装日期 */
    private LocalDate installDate;

    /** 质保结束日期 */
    private LocalDate warrantyEndDate;

    /** 最近维护日期 */
    private LocalDate lastMaintenanceDate;
}
