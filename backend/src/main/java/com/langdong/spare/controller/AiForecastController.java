package com.langdong.spare.controller;

import com.langdong.spare.service.ai.AiForecastService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI智能分析模块 Controller
 */
@RestController
@RequestMapping("/api/ai/forecast")
public class AiForecastController {

    @Autowired
    private AiForecastService aiForecastService;

    /**
     * 手动触发全量预测与分析（异步，仅超级管理员可调用）
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('ai:forecast:trigger')")
    public ResponseEntity<Map<String, Object>> triggerForecast() {
        aiForecastService.runFullForecast();
        Map<String, Object> runStatus = aiForecastService.getRunStatus();
        String status = String.valueOf(runStatus.getOrDefault("status", "IDLE"));
        boolean accepted = "RUNNING".equals(status);

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("accepted", accepted);
        resp.put("message", accepted
            ? "AI预测及安全库存储备分析任务已启动"
            : "重算任务未进入运行态，请稍后重试");
        resp.put("runStatus", runStatus);
        return ResponseEntity.ok(resp);
    }

    /**
     * 查询手动重算运行进度
     */
    @GetMapping("/trigger/status")
    @PreAuthorize("hasAnyAuthority('ai:forecast:list', 'ai:forecast:trigger')")
    public ResponseEntity<Map<String, Object>> queryTriggerStatus() {
        return ResponseEntity.ok(aiForecastService.getRunStatus());
    }

    /**
     * 分页查询预测结果列表
     */
    @GetMapping("/result")
    @PreAuthorize("hasAuthority('ai:forecast:list')")
    public ResponseEntity<Map<String, Object>> queryResult(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String partCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String normalizedMonth = (month != null && !month.trim().isEmpty()) ? month.trim() : null;
        String normalizedPartCode = (partCode != null && !partCode.trim().isEmpty()) ? partCode.trim() : null;

        if (normalizedMonth != null && !normalizedMonth.matches("\\d{4}-\\d{2}")) {
            return ResponseEntity.badRequest().build();
        }
        if (page < 1)
            page = 1;
        if (size < 1 || size > 200)
            size = 20;

        Map<String, Object> result = aiForecastService.queryResult(normalizedMonth, normalizedPartCode, page, size);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询指定备件的历史预测趋势
     */
    @GetMapping("/result/{partCode}")
    @PreAuthorize("hasAuthority('ai:forecast:list')")
    public ResponseEntity<Object> getHistory(@PathVariable String partCode) {
        return ResponseEntity.ok(aiForecastService.queryHistory(partCode));
    }
}
