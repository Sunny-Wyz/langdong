package com.langdong.spare.controller;

import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.forecast.scheduler.MonthlyForecastScheduler;
import com.langdong.spare.mapper.AiForecastResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求预测与辅助决策 API（基于两阶段模型）。
 *
 * <p>提供对两阶段需求预测结果、置信区间、以及安全库存与 ROP（六标准字段）的只读查询及手动重算触发。</p>
 */
@RestController
@RequestMapping("/api/v1/forecast")
public class ForecastController {

    private static final Logger log = LoggerFactory.getLogger(ForecastController.class);

    private final AiForecastResultMapper aiForecastResultMapper;
    private final MonthlyForecastScheduler monthlyForecastScheduler;

    public ForecastController(AiForecastResultMapper aiForecastResultMapper,
                              MonthlyForecastScheduler monthlyForecastScheduler) {
        this.aiForecastResultMapper = aiForecastResultMapper;
        this.monthlyForecastScheduler = monthlyForecastScheduler;
    }

    /**
     * 分页查询预测与阈值分析结果。
     * 返回六标准字段：需求发生概率、正需求量预测均值、区间上下界、提前期分位数（即 ROP 的原始分位数）、安全库存、补货点。
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
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;

        int offset = (page - 1) * size;
        List<AiForecastResult> list = aiForecastResultMapper.findByPage(normalizedMonth, normalizedPartCode, offset, size);
        long total = aiForecastResultMapper.countByPage(normalizedMonth, normalizedPartCode);

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("total", total);
        resp.put("list", list);
        return ResponseEntity.ok(resp);
    }

    /**
     * 查询指定备件的历史预测趋势与关联阈值记录。
     */
    @GetMapping("/result/{partCode}")
    @PreAuthorize("hasAuthority('ai:forecast:list')")
    public ResponseEntity<Map<String, Object>> queryHistory(@PathVariable String partCode) {
        if (partCode == null || partCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        List<AiForecastResult> list = aiForecastResultMapper.findByPartCode(partCode.trim());
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("list", list);
        return ResponseEntity.ok(resp);
    }

    /**
     * 手动触发两阶段预测、库存控制重算与补货流水线。
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('ai:forecast:trigger')")
    public ResponseEntity<Map<String, Object>> triggerForecast(@RequestParam(required = false) String month) {
        String targetMonth = (month != null && !month.trim().isEmpty())
                ? month.trim()
                : YearMonth.now().toString();

        log.info("[手动触发] 手动拉起两阶段重算分析，目标月份: {}", targetMonth);

        // 异步执行
        monthlyForecastScheduler.triggerForecastPipeline(targetMonth);

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("accepted", true);
        resp.put("message", "两阶段AI预测重算与安全库存分析任务已启动");
        return ResponseEntity.ok(resp);
    }
}
