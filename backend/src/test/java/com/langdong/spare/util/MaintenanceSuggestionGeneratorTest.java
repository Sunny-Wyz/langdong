package com.langdong.spare.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * MaintenanceSuggestionGenerator 工具类单元测试
 *
 * 测试覆盖率目标：>80%
 * 测试框架：JUnit 5
 */
public class MaintenanceSuggestionGeneratorTest {

    // ================================================================
    // 建议生成判断测试
    // ================================================================

    @Test
    public void testShouldGenerateSuggestion_HealthyDevice() {
        // 健康设备（健康分85，概率0.2）→ 不需要建议
        boolean should = MaintenanceSuggestionGenerator.shouldGenerateSuggestion(85.0, 0.2);
        assertFalse(should, "健康设备不应生成建议");
    }

    @Test
    public void testShouldGenerateSuggestion_HighHealthLowProb() {
        // 健康分80，概率0.29（刚好在阈值内）→ 不需要建议
        boolean should = MaintenanceSuggestionGenerator.shouldGenerateSuggestion(80.0, 0.29);
        assertFalse(should, "健康且低概率不应生成建议");
    }

    @Test
    public void testShouldGenerateSuggestion_LowHealth() {
        // 健康分60，概率0.2 → 需要建议（健康分不足）
        boolean should = MaintenanceSuggestionGenerator.shouldGenerateSuggestion(60.0, 0.2);
        assertTrue(should, "低健康分应生成建议");
    }

    @Test
    public void testShouldGenerateSuggestion_HighProb() {
        // 健康分85，概率0.5 → 需要建议（概率过高）
        boolean should = MaintenanceSuggestionGenerator.shouldGenerateSuggestion(85.0, 0.5);
        assertTrue(should, "高故障概率应生成建议");
    }

    @Test
    public void testShouldGenerateSuggestion_BothBad() {
        // 健康分60，概率0.5 → 需要建议（两者都差）
        boolean should = MaintenanceSuggestionGenerator.shouldGenerateSuggestion(60.0, 0.5);
        assertTrue(should, "健康和概率都差应生成建议");
    }

    // ================================================================
    // 维护类型判定测试
    // ================================================================

    @Test
    public void testDetermineMaintenanceType_EmergencyCritical() {
        // CRITICAL风险 → EMERGENCY
        String type = MaintenanceSuggestionGenerator.determineMaintenanceType("CRITICAL", 0.5);
        assertEquals("EMERGENCY", type, "CRITICAL风险应为EMERGENCY类型");
    }

    @Test
    public void testDetermineMaintenanceType_EmergencyHighProb() {
        // 概率>0.8 → EMERGENCY
        String type = MaintenanceSuggestionGenerator.determineMaintenanceType("MEDIUM", 0.85);
        assertEquals("EMERGENCY", type, "概率>0.8应为EMERGENCY类型");
    }

    @Test
    public void testDetermineMaintenanceType_PredictiveHigh() {
        // HIGH风险 → PREDICTIVE
        String type = MaintenanceSuggestionGenerator.determineMaintenanceType("HIGH", 0.4);
        assertEquals("PREDICTIVE", type, "HIGH风险应为PREDICTIVE类型");
    }

    @Test
    public void testDetermineMaintenanceType_PredictiveMediumProb() {
        // 概率>0.5 → PREDICTIVE
        String type = MaintenanceSuggestionGenerator.determineMaintenanceType("MEDIUM", 0.6);
        assertEquals("PREDICTIVE", type, "概率>0.5应为PREDICTIVE类型");
    }

    @Test
    public void testDetermineMaintenanceType_Preventive() {
        // MEDIUM风险，低概率 → PREVENTIVE
        String type = MaintenanceSuggestionGenerator.determineMaintenanceType("MEDIUM", 0.3);
        assertEquals("PREVENTIVE", type, "MEDIUM风险低概率应为PREVENTIVE类型");
    }

    @Test
    public void testDetermineMaintenanceType_PreventiveLow() {
        // LOW风险 → PREVENTIVE
        String type = MaintenanceSuggestionGenerator.determineMaintenanceType("LOW", 0.4);
        assertEquals("PREVENTIVE", type, "LOW风险应为PREVENTIVE类型");
    }

    // ================================================================
    // 优先级判定测试
    // ================================================================

    @Test
    public void testDeterminePriorityLevel_HighCritical() {
        // CRITICAL风险 → HIGH优先级
        String priority = MaintenanceSuggestionGenerator.determinePriorityLevel("CRITICAL", 0.5);
        assertEquals("HIGH", priority, "CRITICAL风险应为HIGH优先级");
    }

    @Test
    public void testDeterminePriorityLevel_HighProb() {
        // 概率>0.8 → HIGH优先级
        String priority = MaintenanceSuggestionGenerator.determinePriorityLevel("MEDIUM", 0.85);
        assertEquals("HIGH", priority, "概率>0.8应为HIGH优先级");
    }

    @Test
    public void testDeterminePriorityLevel_MediumHigh() {
        // HIGH风险 → MEDIUM优先级
        String priority = MaintenanceSuggestionGenerator.determinePriorityLevel("HIGH", 0.4);
        assertEquals("MEDIUM", priority, "HIGH风险应为MEDIUM优先级");
    }

    @Test
    public void testDeterminePriorityLevel_MediumProb() {
        // 概率>0.5 → MEDIUM优先级
        String priority = MaintenanceSuggestionGenerator.determinePriorityLevel("LOW", 0.6);
        assertEquals("MEDIUM", priority, "概率>0.5应为MEDIUM优先级");
    }

    @Test
    public void testDeterminePriorityLevel_Low() {
        // MEDIUM风险，低概率 → LOW优先级
        String priority = MaintenanceSuggestionGenerator.determinePriorityLevel("MEDIUM", 0.3);
        assertEquals("LOW", priority, "MEDIUM风险低概率应为LOW优先级");
    }

    // ================================================================
    // 时间窗口计算测试
    // ================================================================

    @Test
    public void testCalcMaintenanceWindow_HighPriority() {
        LocalDate refDate = LocalDate.of(2026, 3, 23);
        Map<String, LocalDate> window = MaintenanceSuggestionGenerator.calcMaintenanceWindow("HIGH", refDate);

        assertEquals(LocalDate.of(2026, 3, 24), window.get("startDate"),
                "HIGH优先级开始日期应为明天");
        assertEquals(LocalDate.of(2026, 3, 30), window.get("endDate"),
                "HIGH优先级结束日期应为7天后");
    }

    @Test
    public void testCalcMaintenanceWindow_MediumPriority() {
        LocalDate refDate = LocalDate.of(2026, 3, 23);
        Map<String, LocalDate> window = MaintenanceSuggestionGenerator.calcMaintenanceWindow("MEDIUM", refDate);

        assertEquals(LocalDate.of(2026, 3, 30), window.get("startDate"),
                "MEDIUM优先级开始日期应为7天后");
        assertEquals(LocalDate.of(2026, 4, 6), window.get("endDate"),
                "MEDIUM优先级结束日期应为14天后");
    }

    @Test
    public void testCalcMaintenanceWindow_LowPriority() {
        LocalDate refDate = LocalDate.of(2026, 3, 23);
        Map<String, LocalDate> window = MaintenanceSuggestionGenerator.calcMaintenanceWindow("LOW", refDate);

        assertEquals(LocalDate.of(2026, 4, 6), window.get("startDate"),
                "LOW优先级开始日期应为14天后");
        assertEquals(LocalDate.of(2026, 4, 22), window.get("endDate"),
                "LOW优先级结束日期应为30天后");
    }

    @Test
    public void testCalcMaintenanceWindow_NullRefDate() {
        // null参考日期应使用当前日期
        Map<String, LocalDate> window = MaintenanceSuggestionGenerator.calcMaintenanceWindow("HIGH", null);

        assertNotNull(window.get("startDate"), "开始日期不应为null");
        assertNotNull(window.get("endDate"), "结束日期不应为null");
        assertTrue(window.get("endDate").isAfter(window.get("startDate")),
                "结束日期应在开始日期之后");
    }

    // ================================================================
    // 建议原因生成测试
    // ================================================================

    @Test
    public void testGenerateSuggestionReason_Critical() {
        String reason = MaintenanceSuggestionGenerator.generateSuggestionReason(
                "CRITICAL", 35.0, 0.85, 8
        );

        assertTrue(reason.contains("35.00"), "应包含健康评分");
        assertTrue(reason.contains("严重"), "应包含严重风险说明");
        assertTrue(reason.contains("85.0%"), "应包含故障概率");
        assertTrue(reason.contains("8次"), "应包含故障次数");
        assertTrue(reason.contains("立即安排维护"), "CRITICAL应包含立即维护建议");
    }

    @Test
    public void testGenerateSuggestionReason_High() {
        String reason = MaintenanceSuggestionGenerator.generateSuggestionReason(
                "HIGH", 55.0, 0.65, 5
        );

        assertTrue(reason.contains("高风险"), "应包含高风险说明");
        assertTrue(reason.contains("尽快安排"), "HIGH应包含尽快维护建议");
    }

    @Test
    public void testGenerateSuggestionReason_Medium() {
        String reason = MaintenanceSuggestionGenerator.generateSuggestionReason(
                "MEDIUM", 70.0, 0.45, 3
        );

        assertTrue(reason.contains("中风险"), "应包含中风险说明");
        assertTrue(reason.contains("2周"), "MEDIUM应包含2周维护建议");
    }

    @Test
    public void testGenerateSuggestionReason_Low() {
        String reason = MaintenanceSuggestionGenerator.generateSuggestionReason(
                "LOW", 85.0, 0.25, 1
        );

        assertTrue(reason.contains("低风险"), "应包含低风险说明");
        assertTrue(reason.contains("常规维护"), "LOW应包含常规维护建议");
    }

    // ================================================================
    // 关联备件类型建议测试
    // ================================================================

    @Test
    public void testSuggestSparePartCategories_WearParts() {
        // 运行时长评分低（<50）→ 易损件
        List<String> categories = MaintenanceSuggestionGenerator.suggestSparePartCategories(
                40.0, 70.0, 1
        );

        assertTrue(categories.contains("WEAR_PARTS"), "运行时长评分低应建议易损件");
    }

    @Test
    public void testSuggestSparePartCategories_CriticalParts() {
        // 故障评分低（<60）→ 关键部件
        List<String> categories = MaintenanceSuggestionGenerator.suggestSparePartCategories(
                85.0, 50.0, 1
        );

        assertTrue(categories.contains("CRITICAL_PARTS"), "故障评分低应建议关键部件");
    }

    @Test
    public void testSuggestSparePartCategories_CommonParts() {
        // 预测故障次数高（≥2）→ 常用备件
        List<String> categories = MaintenanceSuggestionGenerator.suggestSparePartCategories(
                85.0, 70.0, 3
        );

        assertTrue(categories.contains("COMMON_PARTS"), "故障次数高应建议常用备件");
    }

    @Test
    public void testSuggestSparePartCategories_Multiple() {
        // 多个条件满足 → 多种备件类型
        List<String> categories = MaintenanceSuggestionGenerator.suggestSparePartCategories(
                40.0, 50.0, 3
        );

        assertTrue(categories.contains("WEAR_PARTS"), "应包含易损件");
        assertTrue(categories.contains("CRITICAL_PARTS"), "应包含关键部件");
        assertTrue(categories.contains("COMMON_PARTS"), "应包含常用备件");
        assertTrue(categories.size() >= 3, "应包含至少3种备件类型");
    }

    @Test
    public void testSuggestSparePartCategories_RoutineOnly() {
        // 无特殊需求 → 常规维护备件
        List<String> categories = MaintenanceSuggestionGenerator.suggestSparePartCategories(
                85.0, 90.0, 0
        );

        assertTrue(categories.contains("ROUTINE_PARTS"), "无特殊需求应建议常规备件");
        assertEquals(1, categories.size(), "应只有1种备件类型");
    }

    // ================================================================
    // 成本估算测试
    // ================================================================

    @Test
    public void testEstimateMaintenanceCost_EmergencyCritical() {
        // 紧急维护 + 关键设备
        double cost = MaintenanceSuggestionGenerator.estimateMaintenanceCost(
                5000.0,      // 备件成本
                "EMERGENCY", // 维护类型
                "CRITICAL",  // 设备重要性
                8.0          // 停机时长
        );

        // 预期：5000 + 2000（人工） + 5000*8（停机损失） = 47000
        assertEquals(47000.0, cost, 0.01, "紧急维护关键设备成本计算应正确");
    }

    @Test
    public void testEstimateMaintenanceCost_PredictiveImportant() {
        // 预测性维护 + 重要设备
        double cost = MaintenanceSuggestionGenerator.estimateMaintenanceCost(
                3000.0,       // 备件成本
                "PREDICTIVE", // 维护类型
                "IMPORTANT",  // 设备重要性
                4.0           // 停机时长
        );

        // 预期：3000 + 1500（人工） + 2000*4（停机损失） = 12500
        assertEquals(12500.0, cost, 0.01, "预测性维护重要设备成本计算应正确");
    }

    @Test
    public void testEstimateMaintenanceCost_PreventiveNormal() {
        // 预防性维护 + 一般设备
        double cost = MaintenanceSuggestionGenerator.estimateMaintenanceCost(
                1000.0,       // 备件成本
                "PREVENTIVE", // 维护类型
                "NORMAL",     // 设备重要性
                2.0           // 停机时长
        );

        // 预期：1000 + 1000（人工） + 0（一般设备无停机损失） = 2000
        assertEquals(2000.0, cost, 0.01, "预防性维护一般设备成本计算应正确");
    }

    @Test
    public void testEstimateMaintenanceCost_ZeroSparePartCost() {
        // 零备件成本
        double cost = MaintenanceSuggestionGenerator.estimateMaintenanceCost(
                0.0, "PREVENTIVE", "NORMAL", 2.0
        );

        assertEquals(1000.0, cost, 0.01, "零备件成本时应只有人工成本");
    }

    @Test
    public void testEstimateDowntimeHours_Emergency() {
        double hours = MaintenanceSuggestionGenerator.estimateDowntimeHours("EMERGENCY");
        assertEquals(8.0, hours, 0.01, "紧急维护预计停机8小时");
    }

    @Test
    public void testEstimateDowntimeHours_Predictive() {
        double hours = MaintenanceSuggestionGenerator.estimateDowntimeHours("PREDICTIVE");
        assertEquals(4.0, hours, 0.01, "预测性维护预计停机4小时");
    }

    @Test
    public void testEstimateDowntimeHours_Preventive() {
        double hours = MaintenanceSuggestionGenerator.estimateDowntimeHours("PREVENTIVE");
        assertEquals(2.0, hours, 0.01, "预防性维护预计停机2小时");
    }

    @Test
    public void testEstimateDowntimeHours_Unknown() {
        double hours = MaintenanceSuggestionGenerator.estimateDowntimeHours("UNKNOWN");
        assertEquals(2.0, hours, 0.01, "未知类型应使用默认值2小时");
    }

    // ================================================================
    // 工具方法测试
    // ================================================================

    @Test
    public void testRound_TwoDecimals() {
        double result = MaintenanceSuggestionGenerator.round(1234.567, 2);
        assertEquals(1234.57, result, 0.001, "四舍五入到2位小数应正确");
    }

    @Test
    public void testRound_Zero() {
        double result = MaintenanceSuggestionGenerator.round(1234.5, 0);
        assertEquals(1235.0, result, 0.01, "四舍五入到整数应正确");
    }

    // ================================================================
    // 综合场景测试
    // ================================================================

    @Test
    public void testFullSuggestionWorkflow_CriticalDevice() {
        // 场景：严重风险设备完整建议生成流程
        double healthScore = 35.0;
        double failureProbability = 0.85;
        String riskLevel = "CRITICAL";
        int predictedFaults = 8;

        // 1. 判断是否需要建议
        boolean shouldGenerate = MaintenanceSuggestionGenerator.shouldGenerateSuggestion(
                healthScore, failureProbability
        );
        assertTrue(shouldGenerate, "严重风险设备应生成建议");

        // 2. 确定维护类型和优先级
        String maintenanceType = MaintenanceSuggestionGenerator.determineMaintenanceType(
                riskLevel, failureProbability
        );
        String priorityLevel = MaintenanceSuggestionGenerator.determinePriorityLevel(
                riskLevel, failureProbability
        );
        assertEquals("EMERGENCY", maintenanceType);
        assertEquals("HIGH", priorityLevel);

        // 3. 计算时间窗口
        LocalDate today = LocalDate.of(2026, 3, 23);
        Map<String, LocalDate> window = MaintenanceSuggestionGenerator.calcMaintenanceWindow(
                priorityLevel, today
        );
        assertEquals(LocalDate.of(2026, 3, 24), window.get("startDate"));
        assertEquals(LocalDate.of(2026, 3, 30), window.get("endDate"));

        // 4. 生成建议原因
        String reason = MaintenanceSuggestionGenerator.generateSuggestionReason(
                riskLevel, healthScore, failureProbability, predictedFaults
        );
        assertTrue(reason.contains("严重"));
        assertTrue(reason.contains("立即安排维护"));

        // 5. 建议备件类型（假设运行时长评分40，故障评分30）
        List<String> sparePartCategories = MaintenanceSuggestionGenerator.suggestSparePartCategories(
                40.0, 30.0, predictedFaults
        );
        assertTrue(sparePartCategories.contains("WEAR_PARTS"));
        assertTrue(sparePartCategories.contains("CRITICAL_PARTS"));
        assertTrue(sparePartCategories.contains("COMMON_PARTS"));

        // 6. 估算成本
        double estimatedCost = MaintenanceSuggestionGenerator.estimateMaintenanceCost(
                8000.0, maintenanceType, "CRITICAL", 8.0
        );
        assertTrue(estimatedCost > 40000, "严重风险设备维护成本应>40000");
    }

    @Test
    public void testFullSuggestionWorkflow_HealthyDevice() {
        // 场景：健康设备不生成建议
        double healthScore = 88.0;
        double failureProbability = 0.15;
        String riskLevel = "LOW";

        // 1. 判断是否需要建议
        boolean shouldGenerate = MaintenanceSuggestionGenerator.shouldGenerateSuggestion(
                healthScore, failureProbability
        );
        assertFalse(shouldGenerate, "健康设备不应生成建议");

        // 2. 如果强制生成，应为预防性维护
        String maintenanceType = MaintenanceSuggestionGenerator.determineMaintenanceType(
                riskLevel, failureProbability
        );
        String priorityLevel = MaintenanceSuggestionGenerator.determinePriorityLevel(
                riskLevel, failureProbability
        );
        assertEquals("PREVENTIVE", maintenanceType);
        assertEquals("LOW", priorityLevel);

        // 3. 成本应较低
        double estimatedCost = MaintenanceSuggestionGenerator.estimateMaintenanceCost(
                500.0, maintenanceType, "NORMAL", 2.0
        );
        assertEquals(1500.0, estimatedCost, 0.01);
    }

    @Test
    public void testFullSuggestionWorkflow_MediumRisk() {
        // 场景：中风险设备预测性维护
        double healthScore = 65.0;
        double failureProbability = 0.55;
        String riskLevel = "HIGH";
        int predictedFaults = 4;

        boolean shouldGenerate = MaintenanceSuggestionGenerator.shouldGenerateSuggestion(
                healthScore, failureProbability
        );
        assertTrue(shouldGenerate);

        String maintenanceType = MaintenanceSuggestionGenerator.determineMaintenanceType(
                riskLevel, failureProbability
        );
        String priorityLevel = MaintenanceSuggestionGenerator.determinePriorityLevel(
                riskLevel, failureProbability
        );
        assertEquals("PREDICTIVE", maintenanceType);
        assertEquals("MEDIUM", priorityLevel);

        LocalDate today = LocalDate.of(2026, 3, 23);
        Map<String, LocalDate> window = MaintenanceSuggestionGenerator.calcMaintenanceWindow(
                priorityLevel, today
        );
        assertEquals(LocalDate.of(2026, 3, 30), window.get("startDate"));
        assertEquals(LocalDate.of(2026, 4, 6), window.get("endDate"));

        double estimatedCost = MaintenanceSuggestionGenerator.estimateMaintenanceCost(
                3000.0, maintenanceType, "IMPORTANT", 4.0
        );
        // 3000 + 1500 + 2000*4 = 12500
        assertEquals(12500.0, estimatedCost, 0.01);
    }
}
