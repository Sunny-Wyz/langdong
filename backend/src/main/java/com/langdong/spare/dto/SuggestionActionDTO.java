package com.langdong.spare.dto;

import lombok.Data;

/**
 * 维护建议操作DTO
 * 用于采纳或拒绝建议的请求参数
 */
@Data
public class SuggestionActionDTO {
    /** 建议ID */
    private Long suggestionId;

    /** 拒绝原因(拒绝时必填) */
    private String rejectReason;

    /** 操作人ID(通常从JWT token获取) */
    private Long handledBy;
}
