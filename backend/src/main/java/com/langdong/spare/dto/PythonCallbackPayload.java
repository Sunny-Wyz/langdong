package com.langdong.spare.dto;

public record PythonCallbackPayload(
        String task_id,
        String status,
        Object result,
        String error
) {
}
