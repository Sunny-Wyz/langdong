package com.langdong.spare.controller;

import com.langdong.spare.forecast.evaluation.RealExperimentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 真实实验：基于库内消耗数据滚动回测，产出论文同口径指标。
 */
@RestController
@RequestMapping("/api/ai/experiment")
public class RealExperimentController {

    private final RealExperimentService realExperimentService;

    public RealExperimentController(RealExperimentService realExperimentService) {
        this.realExperimentService = realExperimentService;
    }

    /**
     * 启动滚动回测（异步）。
     *
     * @param testMonths 测试月数，默认 6，最大 12
     * @param maxParts   参与备件数上限，默认 50
     */
    @PostMapping("/run")
    @PreAuthorize("hasAnyAuthority('ai:forecast:list', 'ai:forecast:trigger')")
    public ResponseEntity<Map<String, Object>> run(
            @RequestParam(defaultValue = "6") int testMonths,
            @RequestParam(defaultValue = "50") int maxParts) {
        Map<String, Object> body = realExperimentService.start(testMonths, maxParts);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", body);
        resp.put("message", body.getOrDefault("message", "ok"));
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ai:forecast:list')")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", realExperimentService.getStatus());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('ai:forecast:list')")
    public ResponseEntity<Map<String, Object>> latest() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", realExperimentService.getLatestResult());
        return ResponseEntity.ok(resp);
    }
}
