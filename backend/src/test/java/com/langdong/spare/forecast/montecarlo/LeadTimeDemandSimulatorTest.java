package com.langdong.spare.forecast.montecarlo;

import com.langdong.spare.forecast.config.ForecastProperties;
import com.langdong.spare.forecast.model.SafetyStockResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LeadTimeDemandSimulator 提前期需求蒙特卡洛模拟器单元测试。
 */
public class LeadTimeDemandSimulatorTest {

    private ForecastProperties createProperties() {
        ForecastProperties properties = new ForecastProperties();
        properties.getMonteCarlo().setSeed(20260518L);
        properties.getMonteCarlo().setSimulations(10000);
        properties.getMonteCarlo().setWorkingDays(22);
        properties.getMonteCarlo().setIntervalZ(1.645);
        return properties;
    }

    @Test
    @DisplayName("蒙特卡洛可复现单测：固定种子 20260518 跑两次，输出完全一致")
    void testReproducibility() {
        ForecastProperties properties = createProperties();
        LeadTimeDemandSimulator simulator = new LeadTimeDemandSimulator(properties);

        double occurrenceProb = 0.4;
        double positiveQty = 12.5;
        double lowerBound = 6.0;
        double upperBound = 19.0;
        int leadTime = 14;
        double serviceLevel = 0.95; // B类备件

        SafetyStockResult res1 = simulator.calculateSafetyStock(
                occurrenceProb, positiveQty, lowerBound, upperBound, leadTime, serviceLevel);

        SafetyStockResult res2 = simulator.calculateSafetyStock(
                occurrenceProb, positiveQty, lowerBound, upperBound, leadTime, serviceLevel);

        // 验证两次计算结果的每一个字段完全相等
        assertEquals(res1.getReorderPoint(), res2.getReorderPoint(), "补货点 ROP 必须完全一致");
        assertEquals(res1.getSafetyStock(), res2.getSafetyStock(), "安全库存 SS 必须完全一致");
        assertEquals(res1.getSampleMean(), res2.getSampleMean(), 1e-9, "样本均值必须完全一致");
        assertEquals(res1.getLeadTimeDemandQuantile(), res2.getLeadTimeDemandQuantile(), 1e-9, "分位数必须完全一致");
        assertEquals(res1.getServiceLevel(), res2.getServiceLevel(), 1e-9, "服务水平必须完全一致");

        // 输出具体数值，供记录查验
        System.out.println("可复现测试结果: ROP=" + res1.getReorderPoint() + ", SS=" + res1.getSafetyStock()
                + ", Mean=" + res1.getSampleMean() + ", Quantile=" + res1.getLeadTimeDemandQuantile());
    }

    @Test
    @DisplayName("边界与异常单测：非法输入抛出异常，提前期为 0 时返回 0")
    void testBoundariesAndExceptions() {
        ForecastProperties properties = createProperties();
        LeadTimeDemandSimulator simulator = new LeadTimeDemandSimulator(properties);

        // 概率越界校验
        assertThrows(IllegalArgumentException.class, () ->
                simulator.calculateSafetyStock(-0.1, 10.0, 5.0, 15.0, 14, 0.95));
        assertThrows(IllegalArgumentException.class, () ->
                simulator.calculateSafetyStock(1.05, 10.0, 5.0, 15.0, 14, 0.95));

        // 提前期越界校验
        assertThrows(IllegalArgumentException.class, () ->
                simulator.calculateSafetyStock(0.5, 10.0, 5.0, 15.0, -1, 0.95));

        // 服务水平越界校验
        assertThrows(IllegalArgumentException.class, () ->
                simulator.calculateSafetyStock(0.5, 10.0, 5.0, 15.0, 14, -0.01));
        assertThrows(IllegalArgumentException.class, () ->
                simulator.calculateSafetyStock(0.5, 10.0, 5.0, 15.0, 14, 1.01));

        // 提前期为 0 时，返回 ROP=0, SS=0 的安全结果
        SafetyStockResult zeroRes = simulator.calculateSafetyStock(
                0.8, 20.0, 10.0, 30.0, 0, 0.95);
        assertEquals(0, zeroRes.getReorderPoint());
        assertEquals(0, zeroRes.getSafetyStock());
        assertEquals(0.0, zeroRes.getSampleMean());
        assertEquals(0.0, zeroRes.getLeadTimeDemandQuantile());
    }

    @Test
    @DisplayName("服务水平映射单测：A/B/C 类分别映射到 0.99/0.95/0.90")
    void testServiceLevelMapping() {
        ForecastProperties.Classify classify = new ForecastProperties.Classify();
        assertEquals(0.99, classify.serviceLevelOf("A"), 1e-9);
        assertEquals(0.95, classify.serviceLevelOf("B"), 1e-9);
        assertEquals(0.90, classify.serviceLevelOf("C"), 1e-9);
        // 缺省/未知情况返回 0.90 (C)
        assertEquals(0.90, classify.serviceLevelOf(null), 1e-9);
        assertEquals(0.90, classify.serviceLevelOf("D"), 1e-9);
    }
}
