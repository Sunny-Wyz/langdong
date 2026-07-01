package com.langdong.spare.forecast.classify;

import com.langdong.spare.forecast.config.ForecastProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AbcXyzCalculator 纯计算单元测试（论文规格：min-max + 帕累托 + CV² + 编码）。
 */
public class AbcXyzCalculatorTest {

    private final ForecastProperties.Classify cfg = new ForecastProperties.Classify();

    @Test
    @DisplayName("min-max 归一化：正常区间与全相等退化为 0")
    void testMinMax() {
        assertArrayEquals(new double[]{0.0, 0.5, 1.0},
                AbcXyzCalculator.minMax(new double[]{10, 20, 30}), 1e-9);
        assertArrayEquals(new double[]{0.0, 0.0, 0.0},
                AbcXyzCalculator.minMax(new double[]{7, 7, 7}), 1e-9);
    }

    @Test
    @DisplayName("编码映射：ABC A=3/B=2/C=1，XYZ X=1/Y=2/Z=3（论文表 3-2）")
    void testCodeMapping() {
        assertEquals(3, AbcXyzCalculator.abcCode("A"));
        assertEquals(2, AbcXyzCalculator.abcCode("B"));
        assertEquals(1, AbcXyzCalculator.abcCode("C"));
        assertEquals(1, AbcXyzCalculator.xyzCode("X"));
        assertEquals(2, AbcXyzCalculator.xyzCode("Y"));
        assertEquals(3, AbcXyzCalculator.xyzCode("Z"));
    }

    @Test
    @DisplayName("CV²：稳定序列小、波动序列大、零均值为极大")
    void testCv2() {
        // 稳定：[10,10,10,10] → 方差 0 → CV² 0
        assertEquals(0.0, AbcXyzCalculator.cv2(Arrays.asList(10.0, 10.0, 10.0, 10.0)), 1e-9);
        // 全 0 → MAX_VALUE
        assertEquals(Double.MAX_VALUE, AbcXyzCalculator.cv2(Arrays.asList(0.0, 0.0, 0.0)), 0.0);
        // 波动：[0,10,0,10] mean=5 var=25 → CV²=1.0
        assertEquals(1.0, AbcXyzCalculator.cv2(Arrays.asList(0.0, 10.0, 0.0, 10.0)), 1e-9);
    }

    @Test
    @DisplayName("XYZ 分档：<0.5→X, [0.5,1.0)→Y, ≥1.0→Z")
    void testXyzClass() {
        assertEquals("X", AbcXyzCalculator.xyzClass(0.2, cfg));
        assertEquals("Y", AbcXyzCalculator.xyzClass(0.5, cfg));
        assertEquals("Y", AbcXyzCalculator.xyzClass(0.99, cfg));
        assertEquals("Z", AbcXyzCalculator.xyzClass(1.0, cfg));
        assertEquals("Z", AbcXyzCalculator.xyzClass(5.0, cfg));
    }

    @Test
    @DisplayName("批量分类：帕累托累计占比分档 A/B/C 且编码一致")
    void testClassifyAllPareto() {
        // 构造 composite 明显分层的 5 个备件：靠年消耗金额拉开差距（其余维度相同）
        // 归一后 cost 分别 1.0/0.75/0.5/0.25/0.0，权重 0.40 → composite 主要由此决定
        List<AbcXyzCalculator.PartInput> inputs = Arrays.asList(
                input("P1", 1000, stable()),
                input("P2", 750, stable()),
                input("P3", 500, stable()),
                input("P4", 250, stable()),
                input("P5", 0, stable()));

        Map<String, AbcXyzCalculator.Classification> res =
                AbcXyzCalculator.classifyAll(inputs, cfg);

        // composite 降序即 P1>P2>P3>P4>P5；累计占比前 70% A、70~90% B、其余 C
        // composite（仅 cost 维度贡献 0.4*norm）：0.4/0.3/0.2/0.1/0 → total=1.0
        // 累计：P1=0.4(≤0.7,A) P2=0.7(≤0.7,A) P3=0.9(≤0.9,B) P4=1.0(>0.9,C) P5=1.0(C)
        assertEquals("A", res.get("P1").abcClass());
        assertEquals("A", res.get("P2").abcClass());
        assertEquals("B", res.get("P3").abcClass());
        assertEquals("C", res.get("P4").abcClass());
        assertEquals("C", res.get("P5").abcClass());
        // 编码一致性
        assertEquals(3, res.get("P1").abcCode());
        assertEquals(1, res.get("P5").abcCode());
        // 稳定序列 → X
        assertEquals("X", res.get("P1").xyzClass());
    }

    @Test
    @DisplayName("空输入返回空 map，不抛异常")
    void testEmpty() {
        assertTrue(AbcXyzCalculator.classifyAll(null, cfg).isEmpty());
        assertTrue(AbcXyzCalculator.classifyAll(java.util.Collections.emptyList(), cfg).isEmpty());
    }

    private List<Double> stable() {
        return Arrays.asList(10.0, 10.0, 10.0, 10.0, 10.0, 10.0);
    }

    private AbcXyzCalculator.PartInput input(String code, double annualCost, List<Double> series) {
        // 关键度/提前期/替代难度设为相同，使 composite 仅由年消耗金额区分
        return new AbcXyzCalculator.PartInput(code, annualCost, 0.0, 10.0, 3.0, series);
    }
}
