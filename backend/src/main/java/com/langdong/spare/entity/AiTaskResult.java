package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * AI异步任务回调结果实体
 * 对应表：ai_task_result
 */
@Data
public class AiTaskResult {
    /** Celery 任务ID */
    private String taskId;

    /** 任务状态: PENDING / SUCCESS / FAILURE */
    private String status;

    /** 回调结果 JSON */
    private String payload;

    /** 错误信息 */
    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
