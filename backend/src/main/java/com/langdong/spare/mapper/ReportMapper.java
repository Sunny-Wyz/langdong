package com.langdong.spare.mapper;

import com.langdong.spare.dto.KpiSummaryDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {

    // ---- Dashboard KPI ----
    KpiSummaryDTO getKpiSummary(@Param("yearMonth") String yearMonth);

    // ---- Inventory Report ----
    List<Map<String, Object>> getAbcDistribution();

    List<Map<String, Object>> getStagnantParts(@Param("thresholdDays") int thresholdDays);

    List<Map<String, Object>> getInventoryTurnoverByCategory();

    // ---- Consumption Report ----
    List<Map<String, Object>> getConsumptionTrend(@Param("months") int months);

    List<Map<String, Object>> getTop10ConsumptionParts(@Param("yearMonth") String yearMonth);

    // ---- Supplier Report ----
    List<Map<String, Object>> getSupplierPerformance();

    // ---- Maintenance Report ----
    List<Map<String, Object>> getMaintenanceCostByMonth(@Param("months") int months);

    List<Map<String, Object>> getMaintenanceCostByDevice();
}
