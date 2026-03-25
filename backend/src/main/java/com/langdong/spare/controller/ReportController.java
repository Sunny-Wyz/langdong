package com.langdong.spare.controller;

import com.langdong.spare.dto.KpiSummaryDTO;
import com.langdong.spare.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    // --- Dashboard ---
    @GetMapping("/kpi")
    @PreAuthorize("hasAuthority('report:dashboard:view')")
    public ResponseEntity<KpiSummaryDTO> getKpi(
            @RequestParam(required = false) String yearMonth) {
        if (yearMonth != null && !yearMonth.matches("\\d{4}-\\d{2}")) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(reportService.getKpiSummary(yearMonth));
    }

    // --- Inventory Report ---
    @GetMapping("/inventory/abc")
    @PreAuthorize("hasAuthority('report:inventory:view')")
    public ResponseEntity<List<Map<String, Object>>> getAbcDistribution() {
        return ResponseEntity.ok(reportService.getAbcDistribution());
    }

    @GetMapping("/inventory/stagnant")
    @PreAuthorize("hasAuthority('report:inventory:view')")
    public ResponseEntity<List<Map<String, Object>>> getStagnantParts(
            @RequestParam(required = false, defaultValue = "90") int thresholdDays) {
        if (thresholdDays < 1 || thresholdDays > 365) {
            thresholdDays = 90;
        }
        return ResponseEntity.ok(reportService.getStagnantParts(thresholdDays));
    }

    @GetMapping("/inventory/turnover")
    @PreAuthorize("hasAuthority('report:inventory:view')")
    public ResponseEntity<List<Map<String, Object>>> getInventoryTurnover() {
        return ResponseEntity.ok(reportService.getInventoryTurnoverByCategory());
    }

    // --- Consumption Report ---
    @GetMapping("/consumption/trend")
    @PreAuthorize("hasAuthority('report:consumption:view')")
    public ResponseEntity<List<Map<String, Object>>> getConsumptionTrend(
            @RequestParam(required = false, defaultValue = "6") int months) {
        if (months < 1 || months > 24) {
            months = 6;
        }
        return ResponseEntity.ok(reportService.getConsumptionTrend(months));
    }

    @GetMapping("/consumption/top10")
    @PreAuthorize("hasAuthority('report:consumption:view')")
    public ResponseEntity<List<Map<String, Object>>> getTop10(
            @RequestParam(required = false) String yearMonth) {
        if (yearMonth != null && !yearMonth.matches("\\d{4}-\\d{2}")) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(reportService.getTop10ConsumptionParts(yearMonth));
    }

    // --- Supplier Report ---
    @GetMapping("/supplier/performance")
    @PreAuthorize("hasAuthority('report:supplier:view')")
    public ResponseEntity<List<Map<String, Object>>> getSupplierPerformance() {
        return ResponseEntity.ok(reportService.getSupplierPerformance());
    }

    // --- Maintenance Report ---
    @GetMapping("/maintenance/cost-by-month")
    @PreAuthorize("hasAuthority('report:maintenance:view')")
    public ResponseEntity<List<Map<String, Object>>> getMaintenanceCostByMonth(
            @RequestParam(required = false, defaultValue = "6") int months) {
        if (months < 1 || months > 24) {
            months = 6;
        }
        return ResponseEntity.ok(reportService.getMaintenanceCostByMonth(months));
    }

    @GetMapping("/maintenance/cost-by-device")
    @PreAuthorize("hasAuthority('report:maintenance:view')")
    public ResponseEntity<List<Map<String, Object>>> getMaintenanceCostByDevice() {
        return ResponseEntity.ok(reportService.getMaintenanceCostByDevice());
    }
}
