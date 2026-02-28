package com.langdong.spare.dto;

import lombok.Data;

/**
 * 预警任务中心单条预警项
 */
@Data
public class WarningItemDTO {
    private String type; // LOW_STOCK / OVERDUE_ORDER / OVERDUE_PO
    private String title; // 预警标题
    private String detail; // 详情描述
    private String severity; // 紧急 / 警告
    private String targetPath; // 跳转路由（前端用）
    private Long refId; // 关联ID（可跳转详情）
}
