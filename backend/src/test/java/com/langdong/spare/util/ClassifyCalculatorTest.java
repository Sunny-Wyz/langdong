package com.langdong.spare.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClassifyCalculator 单元测试
 * 使用 JUnit 5，不依赖 Spring 上下文（纯 Java 计算）
 */
@DisplayName("备件分类计算工具类测试")
class ClassifyCalculatorTest {

    // ================================================================
    // 采购提前期得分测试
    // ================================================================

    @Test
    @DisplayName("提前期 > 30天 → 得分100")
    void testLeadTimeScore_GT30() {
        double score = ClassifyCalculator.calcLeadTimeScore(45);
        assertEquals(100.0, score, 0.001, "提前期>30天应返回100分");
    }

    @Test
    @DisplayName("提前期 = 31天（边界值）→ 得分100")
    void testLeadTimeScore_Boundary31() {
        double score = ClassifyCalculator.calcLeadTimeScore(31);
        assertEquals(100.0, score, 0.001, "提前期31天应返回100分");
    }

    @Test
    @DisplayName("提前期 = 30天（边界值）→ 得分60")
    void testLeadTimeScore_Boundary30() {
        double score = ClassifyCalculator.calcLeadTimeScore(30);
        assertEquals(60.0, score, 0.001, "提前期30天应返回60分");
    }

    @Test
    @DisplayName("提前期 15~30天（中间值20天）→ 得分60")
    void testLeadTimeScore_15To30() {
        double score = ClassifyCalculator.calcLeadTimeScore(20);
        assertEquals(60.0, score, 0.001, "提前期15~30天应返回60分");
    }

    @Test
    @DisplayName("提前期 = 15天（边界值）→ 得分60")
    void testLeadTimeScore_Boundary15() {
        double score = ClassifyCalculator.calcLeadTimeScore(15);
        assertEquals(60.0, score, 0.001, "提前期15天应返回60分");
    }

    @Test
    @DisplayName("提前期 < 15天（7天）→ 得分20")
    void testLeadTimeScore_LT15() {
        double score = ClassifyCalculator.calcLeadTimeScore(7);
        assertEquals(20.0, score, 0.001, "提前期<15天应返回20分");
    }

    @Test
    @DisplayName("提前期为 null → 默认30天 → 得分60")
    void testLeadTimeScore_Null() {
        double score = ClassifyCalculator.calcLeadTimeScore(null);
        assertEquals(60.0, score, 0.001, "提前期为null应使用默认值30天，返回60分");
    }

    // ================================================================
    // 设备关键度得分测试
    // ================================================================

    @Test
    @DisplayName("关键备件(is_critical=1) → 得分100")
    void testCriticalScore_Critical() {
        double score = ClassifyCalculator.calcCriticalScore(1);
        assertEquals(100.0, score, 0.001);
    }

    @Test
    @DisplayName("非关键备件(is_critical=0) → 得分0")
    void testCriticalScore_NonCritical() {
        double score = ClassifyCalculator.calcCriticalScore(0);
        assertEquals(0.0, score, 0.001);
    }

    @Test
    @DisplayName("is_critical为null → 得分0")
    void testCriticalScore_Null() {
        double score = ClassifyCalculator.calcCriticalScore(null);
        assertEquals(0.0, score, 0.001);
    }

    // ================================================================
    // 供应替代难度得分测试
    // ================================================================

    @Test
    @DisplayName("替代难度1(最易) → 得分0")
    void testReplaceDiffScore_Min() {
        double score = ClassifyCalculator.calcReplaceDiffScore(1);
        assertEquals(0.0, score, 0.001);
    }

    @Test
    @DisplayName("替代难度5(最难) → 得分100")
    void testReplaceDiffScore_Max() {
        double score = ClassifyCalculator.calcReplaceDiffScore(5);
        assertEquals(100.0, score, 0.001);
    }

    @Test
    @DisplayName("替代难度3(中间) → 得分50")
    void testReplaceDiffScore_Middle() {
        double score = ClassifyCalculator.calcReplaceDiffScore(3);
        assertEquals(50.0, score, 0.001);
    }

    // ================================================================
    // 综合加权得分测试
    // ================================================================

    @Test
    @DisplayName("全部单维度满分 → 综合得分100")
    void testCompositeScore_AllMax() {
        double score = ClassifyCalculator.calcCompositeScore(100, 100, 100, 100);
        assertEquals(100.0, score, 0.001, "全满分时综合得分应为100");
    }

    @Test
    @DisplayName("全部单维度为0 → 综合得分0")
    void testCompositeScore_AllZero() {
        double score = ClassifyCalculator.calcCompositeScore(0, 0, 0, 0);
        assertEquals(0.0, score, 0.001, "全0时综合得分应为0");
    }

    @Test
    @DisplayName("验证加权比例：年消耗40%，关键度30%，提前期20%，替代难度10%")
    void testCompositeScore_WeightVerification() {
        // 只有年消耗金额满分，其他0
        double score1 = ClassifyCalculator.calcCompositeScore(100, 0, 0, 0);
        assertEquals(40.0, score1, 0.001, "年消耗金额权重应为40%");

        // 只有设备关键度满分
        double score2 = ClassifyCalculator.calcCompositeScore(0, 100, 0, 0);
        assertEquals(30.0, score2, 0.001, "设备关键度权重应为30%");

        // 只有采购提前期满分
        double score3 = ClassifyCalculator.calcCompositeScore(0, 0, 100, 0);
        assertEquals(20.0, score3, 0.001, "采购提前期权重应为20%");

        // 只有供应替代难度满分
        double score4 = ClassifyCalculator.calcCompositeScore(0, 0, 0, 100);
        assertEquals(10.0, score4, 0.001, "供应替代难度权重应为10%");
    }

    // ================================================================
    // CV² 计算测试
    // ================================================================

    @Test
    @DisplayName("需求完全稳定（每月消耗相同）→ CV²=0")
    void testCalcCV2_PerfectlyStable() {
        List<Integer> demands = Arrays.asList(10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10);
        double cv2 = ClassifyCalculator.calcCV2(demands);
        assertEquals(0.0, cv2, 0.001, "完全稳定需求的CV²应为0");
    }

    @Test
    @DisplayName("需求稳定（小幅波动）→ CV² < 0.5")
    void testCalcCV2_StableData() {
        // 均值约10，波动小
        List<Integer> demands = Arrays.asList(9, 10, 11, 10, 9, 11, 10, 10, 9, 11, 10, 10);
        double cv2 = ClassifyCalculator.calcCV2(demands);
        assertTrue(cv2 < 0.5, "小幅波动需求的CV²应小于0.5，实际值：" + cv2);
    }

    @Test
    @DisplayName("需求高度随机 → CV² ≥ 1.0")
    void testCalcCV2_RandomData() {
        // 极端波动：大部分月份为0，偶有大量消耗
        List<Integer> demands = Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 100);
        double cv2 = ClassifyCalculator.calcCV2(demands);
        assertTrue(cv2 >= 1.0, "高度随机需求的CV²应≥1.0，实际值：" + cv2);
    }

    @Test
    @DisplayName("从未有消耗（全0）→ CV² 返回 MAX_VALUE")
    void testCalcCV2_AllZero() {
        List<Integer> demands = Arrays.asList(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        double cv2 = ClassifyCalculator.calcCV2(demands);
        assertEquals(Double.MAX_VALUE, cv2, "全0消耗时CV²应返回MAX_VALUE");
    }

    @Test
    @DisplayName("需求列表为空 → CV² 返回 MAX_VALUE")
    void testCalcCV2_EmptyList() {
        double cv2 = ClassifyCalculator.calcCV2(Collections.emptyList());
        assertEquals(Double.MAX_VALUE, cv2, "空列表时CV²应返回MAX_VALUE");
    }

    // ================================================================
    // XYZ 分类测试
    // ================================================================

    @Test
    @DisplayName("数据不足3个月 → Z类（无论CV²多小）")
    void testClassifyXYZ_LessThan3Months() {
        // 即使CV²为0（极稳定），数据不足3月也应标记Z
        assertEquals("Z", ClassifyCalculator.classifyXYZ(0.0, 2));
        assertEquals("Z", ClassifyCalculator.classifyXYZ(0.0, 0));
        assertEquals("Z", ClassifyCalculator.classifyXYZ(0.0, 1));
    }

    @Test
    @DisplayName("CV² < 0.5 且数据≥3月 → X类")
    void testClassifyXYZ_X() {
        assertEquals("X", ClassifyCalculator.classifyXYZ(0.0, 12));
        assertEquals("X", ClassifyCalculator.classifyXYZ(0.49, 6));
    }

    @Test
    @DisplayName("0.5 ≤ CV² < 1.0 且数据≥3月 → Y类")
    void testClassifyXYZ_Y() {
        assertEquals("Y", ClassifyCalculator.classifyXYZ(0.5, 12));
        assertEquals("Y", ClassifyCalculator.classifyXYZ(0.75, 6));
        assertEquals("Y", ClassifyCalculator.classifyXYZ(0.99, 3));
    }

    @Test
    @DisplayName("CV² ≥ 1.0 且数据≥3月 → Z类")
    void testClassifyXYZ_Z() {
        assertEquals("Z", ClassifyCalculator.classifyXYZ(1.0, 12));
        assertEquals("Z", ClassifyCalculator.classifyXYZ(5.0, 6));
        assertEquals("Z", ClassifyCalculator.classifyXYZ(Double.MAX_VALUE, 12));
    }

    // ================================================================
    // ABC 分类（分位数）测试
    // ================================================================

    @Test
    @DisplayName("排名第1（满分最高），总数10 → A类（前20%）")
    void testClassifyABC_TopRank() {
        // 总数10，前20%=2个，rank=1 → A
        assertEquals("A", ClassifyCalculator.classifyABC(10, 1));
        assertEquals("A", ClassifyCalculator.classifyABC(10, 2));
    }

    @Test
    @DisplayName("排名第3，总数10 → B类（21%~50%）")
    void testClassifyABC_BClass() {
        // 总数10，21~50%=3~5，rank=3/4/5 → B
        assertEquals("B", ClassifyCalculator.classifyABC(10, 3));
        assertEquals("B", ClassifyCalculator.classifyABC(10, 5));
    }

    @Test
    @DisplayName("排名末尾（后50%）→ C类")
    void testClassifyABC_CClass() {
        // 总数10，后50%=6~10，rank=6~10 → C
        assertEquals("C", ClassifyCalculator.classifyABC(10, 6));
        assertEquals("C", ClassifyCalculator.classifyABC(10, 10));
    }

    // ================================================================
    // 安全库存（SS）测试
    // ================================================================

    @Test
    @DisplayName("A类安全系数 k=2.33 验证")
    void testSafetyStock_AClass() {
        // 手动计算：SS = 2.33 × (10/√22) × √30 ≈ 2.33 × 2.132 × 5.477 ≈ 27.2 → 向上取整=28
        double monthlyStdDev = 10.0;
        int leadTime = 30;
        int ss = ClassifyCalculator.calcSafetyStock("A", monthlyStdDev, leadTime);
        double expected = 2.33 * (monthlyStdDev / Math.sqrt(22)) * Math.sqrt(leadTime);
        int expectedCeil = (int) Math.ceil(expected);
        assertEquals(expectedCeil, ss, "A类安全库存计算错误");
    }

    @Test
    @DisplayName("B类安全系数 k=1.65 验证")
    void testSafetyStock_BClass() {
        double monthlyStdDev = 10.0;
        int leadTime = 30;
        int ss = ClassifyCalculator.calcSafetyStock("B", monthlyStdDev, leadTime);
        double expected = 1.65 * (monthlyStdDev / Math.sqrt(22)) * Math.sqrt(leadTime);
        int expectedCeil = (int) Math.ceil(expected);
        assertEquals(expectedCeil, ss, "B类安全库存计算错误");
    }

    @Test
    @DisplayName("C类安全系数 k=1.28 验证")
    void testSafetyStock_CClass() {
        double monthlyStdDev = 10.0;
        int leadTime = 30;
        int ss = ClassifyCalculator.calcSafetyStock("C", monthlyStdDev, leadTime);
        double expected = 1.28 * (monthlyStdDev / Math.sqrt(22)) * Math.sqrt(leadTime);
        int expectedCeil = (int) Math.ceil(expected);
        assertEquals(expectedCeil, ss, "C类安全库存计算错误");
    }

    @Test
    @DisplayName("月标准差为0 → SS=0")
    void testSafetyStock_ZeroStdDev() {
        int ss = ClassifyCalculator.calcSafetyStock("A", 0.0, 30);
        assertEquals(0, ss, "标准差为0时安全库存应为0");
    }

    // ================================================================
    // 补货触发点（ROP）测试
    // ================================================================

    @Test
    @DisplayName("ROP = 日均需求×提前期 + SS 验证")
    void testReorderPoint_Basic() {
        // 月均消耗22件，提前期30天，SS=10
        // 日均 = 22/22 = 1件/天
        // ROP = 1 × 30 + 10 = 40件
        int rop = ClassifyCalculator.calcReorderPoint(22.0, 30, 10);
        assertEquals(40, rop, "ROP计算：日均1件×30天+SS10=40");
    }

    @Test
    @DisplayName("月均消耗0件 → ROP = SS")
    void testReorderPoint_ZeroDemand() {
        int safetyStock = 5;
        int rop = ClassifyCalculator.calcReorderPoint(0.0, 30, safetyStock);
        assertEquals(safetyStock, rop, "需求为0时ROP应等于SS");
    }

    // ================================================================
    // 月标准差计算测试
    // ================================================================

    @Test
    @DisplayName("稳定需求（全部相同）→ 标准差=0")
    void testCalcMonthlyStdDev_Stable() {
        List<Integer> demands = Arrays.asList(5, 5, 5, 5, 5, 5);
        double stdDev = ClassifyCalculator.calcMonthlyStdDev(demands);
        assertEquals(0.0, stdDev, 0.001, "完全稳定需求的标准差应为0");
    }

    @Test
    @DisplayName("空列表 → 标准差=0")
    void testCalcMonthlyStdDev_Empty() {
        double stdDev = ClassifyCalculator.calcMonthlyStdDev(Collections.emptyList());
        assertEquals(0.0, stdDev, 0.001);
    }
}
