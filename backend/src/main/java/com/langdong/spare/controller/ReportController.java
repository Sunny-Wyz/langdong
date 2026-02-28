package com.langdong.spare.controller;

import com.langdong.spare.dto.KpiSummaryDTO;
import com.langdong.spare.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
@CrossOrigin(origins = "*")
public class ReportController {

    @Autowired
    private ReportService reportService;

    // --- Dashboard ---
    @GetMapping("/kpi")
    public ResponseEntity<KpiSummaryDTO> getKpi(
            @RequestParam(required = false) String yearMonth) {
        return ResponseEntity.ok(reportService.getKpiSummary(yearMonth));
    }

    // --- Inventory Report ---
    @GetMapping("/inventory/abc")
    public ResponseEntity<List<Map<String, Object>>> getAbcDistribution() {
        return ResponseEntity.ok(reportService.getAbcDistribution());
    }

    @GetMapping("/inventory/stagnant")
    public ResponseEntity<List<Map<String, Object>>> getStagnantParts(
            @RequestParam(required = false, defaultValue = "90") int thresholdDays) {
        return ResponseEntity.ok(reportService.getStagnantParts(thresholdDays));
    }

    @GetMapping("/inventory/turnover")
    public ResponseEntity<List<Map<String, Object>>> getInventoryTurnover() {
        return ResponseEntity.ok(reportService.getInventoryTurnoverByCategory());
    }

    // --- Consumption Report ---
    @GetMapping("/consumption/trend")
    public ResponseEntity<List<Map<String, Object>>> getConsumptionTrend(
            @RequestParam(required = false, defaultValue = "6") int months) {
        return ResponseEntity.ok(reportService.getConsumptionTrend(months));
    }

    @GetMapping("/consumption/top10")
    public ResponseEntity<List<Map<String, Object>>> getTop10(
            @RequestParam(required = false) String yearMonth) {
        return ResponseEntity.ok(reportService.getTop10ConsumptionParts(yearMonth));
    }

    // --- Supplier Report ---
    @GetMapping("/supplier/performance")
    public ResponseEntity<List<Map<String, Object>>> getSupplierPerformance() {
        return ResponseEntity.ok(reportService.getSupplierPerformance());
    }

    // --- Maintenance Report ---
    @GetMapping("/maintenance/cost-by-month")
    public ResponseEntity<List<Map<String, Object>>> getMaintenanceCostByMonth(
            @RequestParam(required = false, defaultValue = "6") int months) {
        return ResponseEntity.ok(reportService.getMaintenanceCostByMonth(months));
    }

    @GetMapping("/maintenance/cost-by-device")
    public ResponseEntity<List<Map<String, Object>>> getMaintenanceCostByDevice() {
        return ResponseEntity.ok(reportService.getMaintenanceCostByDevice());
    }
}
