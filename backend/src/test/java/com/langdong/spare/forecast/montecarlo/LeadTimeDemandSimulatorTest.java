package com.langdong.spare.forecast.montecarlo;

import com.langdong.spare.forecast.config.ForecastProperties;
import com.langdong.spare.forecast.model.SafetyStockResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LeadTimeDemandSimulator 提前期需求模拟器单元测试。
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
    @DisplayName("Python 接口调用模拟测试：调用 ROP/SS 计算并验证字段提取")
    void testCalculateSafetyStock() {
        ForecastProperties properties = createProperties();
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        Map<String, Object> fakeResponse = new HashMap<>();
        fakeResponse.put("rop", 12);
        fakeResponse.put("ss", 5);
        fakeResponse.put("mean_demand", 6.8);

        Mockito.when(restTemplate.postForObject(
                Mockito.anyString(),
                Mockito.anyMap(),
                Mockito.eq(Map.class)
        )).thenReturn(fakeResponse);

        LeadTimeDemandSimulator simulator = new LeadTimeDemandSimulator(properties, restTemplate);

        double occurrenceProb = 0.4;
        double positiveQty = 12.5;
        double lowerBound = 6.0;
        double upperBound = 19.0;
        int leadTime = 14;
        double serviceLevel = 0.95;

        SafetyStockResult res = simulator.calculateSafetyStock(
                occurrenceProb, positiveQty, lowerBound, upperBound, leadTime, serviceLevel);

        assertEquals(12, res.getReorderPoint(), "补货点 ROP 必须为 12");
        assertEquals(5, res.getSafetyStock(), "安全库存 SS 必须为 5");
        assertEquals(6.8, res.getSampleMean(), 1e-9, "均值需求必须为 6.8");
        assertEquals(0.95, res.getServiceLevel(), 1e-9, "服务水平必须为 0.95");
    }

    @Test
    @DisplayName("边界与异常单测：非法输入抛出异常，提前期为 0 时返回 0")
    void testBoundariesAndExceptions() {
        ForecastProperties properties = createProperties();
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        LeadTimeDemandSimulator simulator = new LeadTimeDemandSimulator(properties, restTemplate);

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
