package com.langdong.spare.service;

import com.langdong.spare.dto.KpiSummaryDTO;
import com.langdong.spare.mapper.ReportMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    @Autowired
    private ReportMapper reportMapper;

    private String currentYearMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    public KpiSummaryDTO getKpiSummary(String yearMonth) {
        return reportMapper.getKpiSummary(yearMonth != null ? yearMonth : currentYearMonth());
    }

    public List<Map<String, Object>> getAbcDistribution() {
        return reportMapper.getAbcDistribution();
    }

    public List<Map<String, Object>> getStagnantParts(int thresholdDays) {
        return reportMapper.getStagnantParts(thresholdDays);
    }

    public List<Map<String, Object>> getInventoryTurnoverByCategory() {
        return reportMapper.getInventoryTurnoverByCategory();
    }

    public List<Map<String, Object>> getConsumptionTrend(int months) {
        return reportMapper.getConsumptionTrend(months);
    }

    public List<Map<String, Object>> getTop10ConsumptionParts(String yearMonth) {
        return reportMapper.getTop10ConsumptionParts(yearMonth != null ? yearMonth : "");
    }

    public List<Map<String, Object>> getSupplierPerformance() {
        return reportMapper.getSupplierPerformance();
    }

    public List<Map<String, Object>> getMaintenanceCostByMonth(int months) {
        return reportMapper.getMaintenanceCostByMonth(months);
    }

    public List<Map<String, Object>> getMaintenanceCostByDevice() {
        return reportMapper.getMaintenanceCostByDevice();
    }
}
