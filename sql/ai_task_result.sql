-- AI 异步任务结果持久化表
-- 替代 PythonCallbackStoreService 的纯内存存储
USE spare_db;

CREATE TABLE IF NOT EXISTS `ai_task_result` (
    `task_id`     VARCHAR(128)   NOT NULL COMMENT 'Celery 任务ID',
    `status`      VARCHAR(20)    NOT NULL COMMENT '任务状态: PENDING / SUCCESS / FAILURE',
    `payload`     JSON           DEFAULT NULL COMMENT '回调结果 JSON（SUCCESS 时包含预测数据）',
    `error_msg`   VARCHAR(500)   DEFAULT NULL COMMENT '错误信息（FAILURE 时填写）',
    `created_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次写入时间',
    `updated_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`task_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI异步任务回调结果持久化表';
