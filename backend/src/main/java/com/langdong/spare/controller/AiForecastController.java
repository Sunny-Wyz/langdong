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

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "AI预测及安全库存储备分析任务已启动");
        return ResponseEntity.ok(resp);
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
        if (month != null && !month.matches("\\d{4}-\\d{2}")) {
            return ResponseEntity.badRequest().build();
        }
        if (page < 1)
            page = 1;
        if (size < 1 || size > 200)
            size = 20;

        Map<String, Object> result = aiForecastService.queryResult(month, partCode, page, size);
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
