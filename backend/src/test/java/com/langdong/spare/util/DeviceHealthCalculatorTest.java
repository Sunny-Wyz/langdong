package com.langdong.spare.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * DeviceHealthCalculator 工具类单元测试
 *
 * 测试覆盖率目标：>80%
 * 测试框架：JUnit 5
 */
public class DeviceHealthCalculatorTest {

    // ================================================================
    // 运行时长评分测试
    // ================================================================

    @Test
    public void testCalcRuntimeScore_Overload() {
        // 过载运行（>95%）
        double score = DeviceHealthCalculator.calcRuntimeScore(700, 720.0);
        assertEquals(40.0, score, 0.01, "运行时长>95%应返回40分（过载）");
    }

    @Test
    public void testCalcRuntimeScore_HighLoad() {
        // 高负荷运行（85%~95%）
        double score = DeviceHealthCalculator.calcRuntimeScore(650, 720.0);
        assertEquals(70.0, score, 0.01, "运行时长85-95%应返回70分（高负荷）");
    }

    @Test
    public void testCalcRuntimeScore_Normal() {
        // 正常运行（50%~85%）
        double score = DeviceHealthCalculator.calcRuntimeScore(500, 720.0);
        assertEquals(85.0, score, 0.01, "运行时长50-85%应返回85分（正常）");
    }

    @Test
    public void testCalcRuntimeScore_LowUsage() {
        // 低负荷运行（<50%）
        double score = DeviceHealthCalculator.calcRuntimeScore(300, 720.0);
        assertEquals(60.0, score, 0.01, "运行时长<50%应返回60分（闲置）");
    }

    @Test
    public void testCalcRuntimeScore_NullStandard() {
        // 标准时长为null，使用默认值720
        double score = DeviceHealthCalculator.calcRuntimeScore(500, null);
        assertEquals(85.0, score, 0.01, "标准时长为null时应使用默认值720");
    }

    @Test
    public void testCalcRuntimeScore_ZeroStandard() {
        // 标准时长为0，使用默认值720
        double score = DeviceHealthCalculator.calcRuntimeScore(500, 0.0);
        assertEquals(85.0, score, 0.01, "标准时长为0时应使用默认值720");
    }

    // ================================================================
    // 故障频次评分测试
    // ================================================================

    @Test
    public void testCalcFaultScore_NoFault() {
        // 无故障记录（MTBF=9999）
        double score = DeviceHealthCalculator.calcFaultScore(9999.0);
        assertEquals(100.0, score, 0.01, "MTBF=9999应返回100分（无故障）");
    }

    @Test
    public void testCalcFaultScore_Excellent() {
        // 优秀（MTBF≥1000）
        double score = DeviceHealthCalculator.calcFaultScore(1200.0);
        assertEquals(90.0, score, 0.01, "MTBF≥1000应返回90分（优秀）");
    }

    @Test
    public void testCalcFaultScore_Good() {
        // 良好（MTBF≥500）
        double score = DeviceHealthCalculator.calcFaultScore(600.0);
        assertEquals(75.0, score, 0.01, "MTBF≥500应返回75分（良好）");
    }

    @Test
    public void testCalcFaultScore_Fair() {
        // 一般（MTBF≥200）
        double score = DeviceHealthCalculator.calcFaultScore(300.0);
        assertEquals(50.0, score, 0.01, "MTBF≥200应返回50分（一般）");
    }

    @Test
    public void testCalcFaultScore_Warning() {
        // 警戒（MTBF<200）
        double score = DeviceHealthCalculator.calcFaultScore(150.0);
        assertEquals(30.0, score, 0.01, "MTBF<200应返回30分（警戒）");
    }

    @Test
    public void testCalcFaultScore_Null() {
        // MTBF为null
        double score = DeviceHealthCalculator.calcFaultScore(null);
        assertEquals(100.0, score, 0.01, "MTBF为null应返回100分（无故障）");
    }

    @Test
    public void testCalcFaultScore_Zero() {
        // MTBF为0
        double score = DeviceHealthCalculator.calcFaultScore(0.0);
        assertEquals(100.0, score, 0.01, "MTBF为0应返回100分（无故障）");
    }

    // ================================================================
    // 工单数量评分测试
    // ================================================================

    @Test
    public void testCalcWorkorderScore_Zero() {
        double score = DeviceHealthCalculator.calcWorkorderScore(0);
        assertEquals(100.0, score, 0.01, "0个工单应返回100分");
    }

    @Test
    public void testCalcWorkorderScore_Low() {
        double score = DeviceHealthCalculator.calcWorkorderScore(2);
        assertEquals(85.0, score, 0.01, "1-2个工单应返回85分");
    }

    @Test
    public void testCalcWorkorderScore_Medium() {
        double score = DeviceHealthCalculator.calcWorkorderScore(4);
        assertEquals(65.0, score, 0.01, "3-5个工单应返回65分");
    }

    @Test
    public void testCalcWorkorderScore_High() {
        double score = DeviceHealthCalculator.calcWorkorderScore(8);
        assertEquals(45.0, score, 0.01, "6-9个工单应返回45分");
    }

    @Test
    public void testCalcWorkorderScore_Critical() {
        double score = DeviceHealthCalculator.calcWorkorderScore(12);
        assertEquals(30.0, score, 0.01, "≥10个工单应返回30分");
    }

    // ================================================================
    // 换件频次评分测试
    // ================================================================

    @Test
    public void testCalcReplacementScore_Zero() {
        double score = DeviceHealthCalculator.calcReplacementScore(0);
        assertEquals(100.0, score, 0.01, "0个换件应返回100分");
    }

    @Test
    public void testCalcReplacementScore_Low() {
        double score = DeviceHealthCalculator.calcReplacementScore(3);
        assertEquals(85.0, score, 0.01, "1-5个换件应返回85分");
    }

    @Test
    public void testCalcReplacementScore_Medium() {
        double score = DeviceHealthCalculator.calcReplacementScore(8);
        assertEquals(70.0, score, 0.01, "6-10个换件应返回70分");
    }

    @Test
    public void testCalcReplacementScore_High() {
        double score = DeviceHealthCalculator.calcReplacementScore(15);
        assertEquals(50.0, score, 0.01, "11-19个换件应返回50分");
    }

    @Test
    public void testCalcReplacementScore_Critical() {
        double score = DeviceHealthCalculator.calcReplacementScore(25);
        assertEquals(30.0, score, 0.01, "≥20个换件应返回30分");
    }

    // ================================================================
    // 综合健康评分测试
    // ================================================================

    @Test
    public void testCalcHealthScore_Perfect() {
        // 满分情况：所有维度都是100分
        double score = DeviceHealthCalculator.calcHealthScore(100, 100, 100, 100);
        assertEquals(100.0, score, 0.01, "所有维度满分应返回100分");
    }

    @Test
    public void testCalcHealthScore_Mixed() {
        // 混合评分：运行85, 故障75, 工单65, 换件70
        // 预期：85*0.25 + 75*0.35 + 65*0.20 + 70*0.20 = 21.25 + 26.25 + 13 + 14 = 74.5
        double score = DeviceHealthCalculator.calcHealthScore(85, 75, 65, 70);
        assertEquals(74.5, score, 0.01, "混合评分计算应正确");
    }

    @Test
    public void testCalcHealthScore_AllZero() {
        // 零分情况
        double score = DeviceHealthCalculator.calcHealthScore(0, 0, 0, 0);
        assertEquals(0.0, score, 0.01, "所有维度零分应返回0分");
    }

    @Test
    public void testCalcHealthScore_WeightVerification() {
        // 验证权重总和为1
        // 运行40, 故障35, 工单20, 换件20，每个维度权重乘以100
        // 预期：40*0.25 + 35*0.35 + 20*0.20 + 20*0.20 = 10 + 12.25 + 4 + 4 = 30.25
        double score = DeviceHealthCalculator.calcHealthScore(40, 35, 20, 20);
        assertEquals(30.25, score, 0.01, "权重计算应正确");
    }

    // ================================================================
    // 带时间衰减的健康评分测试
    // ================================================================

    @Test
    public void testCalcHealthScoreWithDecay_ThreeMonths() {
        // 最近3个月评分：[80, 70, 60]（当前月、上月、2月前）
        // 加权平均：(80*1.0 + 70*0.95 + 60*0.90) / (1.0 + 0.95 + 0.90)
        //         = (80 + 66.5 + 54) / 2.85 = 200.5 / 2.85 ≈ 70.35
        List<Double> scores = Arrays.asList(80.0, 70.0, 60.0);
        double result = DeviceHealthCalculator.calcHealthScoreWithDecay(scores);
        assertEquals(70.35, result, 0.01, "3个月数据时间衰减计算应正确");
    }

    @Test
    public void testCalcHealthScoreWithDecay_TwoMonths() {
        // 最近2个月评分：[90, 80]
        // 加权平均：(90*1.0 + 80*0.95) / (1.0 + 0.95) = (90 + 76) / 1.95 ≈ 85.13
        List<Double> scores = Arrays.asList(90.0, 80.0);
        double result = DeviceHealthCalculator.calcHealthScoreWithDecay(scores);
        assertEquals(85.13, result, 0.01, "2个月数据时间衰减计算应正确");
    }

    @Test
    public void testCalcHealthScoreWithDecay_OneMonth() {
        // 仅1个月评分：[75]
        // 加权平均：(75*1.0) / 1.0 = 75
        List<Double> scores = Arrays.asList(75.0);
        double result = DeviceHealthCalculator.calcHealthScoreWithDecay(scores);
        assertEquals(75.0, result, 0.01, "1个月数据应返回原值");
    }

    @Test
    public void testCalcHealthScoreWithDecay_MoreThanThree() {
        // 超过3个月的数据，仅使用前3个
        // [85, 75, 65, 55, 45]，应只使用前3个
        List<Double> scores = Arrays.asList(85.0, 75.0, 65.0, 55.0, 45.0);
        double result = DeviceHealthCalculator.calcHealthScoreWithDecay(scores);
        // 预期：(85*1.0 + 75*0.95 + 65*0.90) / (1.0 + 0.95 + 0.90)
        //     = (85 + 71.25 + 58.5) / 2.85 = 214.75 / 2.85 ≈ 75.35
        assertEquals(75.35, result, 0.01, "超过3个月数据应只使用前3个");
    }

    @Test
    public void testCalcHealthScoreWithDecay_EmptyList() {
        List<Double> scores = new ArrayList<>();
        double result = DeviceHealthCalculator.calcHealthScoreWithDecay(scores);
        assertEquals(0.0, result, 0.01, "空列表应返回0");
    }

    @Test
    public void testCalcHealthScoreWithDecay_Null() {
        double result = DeviceHealthCalculator.calcHealthScoreWithDecay(null);
        assertEquals(0.0, result, 0.01, "null列表应返回0");
    }

    // ================================================================
    // 风险等级判定测试
    // ================================================================

    @Test
    public void testDetermineRiskLevel_Critical() {
        String risk = DeviceHealthCalculator.determineRiskLevel(35.0);
        assertEquals("CRITICAL", risk, "健康分<40应为CRITICAL");
    }

    @Test
    public void testDetermineRiskLevel_High() {
        String risk = DeviceHealthCalculator.determineRiskLevel(50.0);
        assertEquals("HIGH", risk, "健康分40-60应为HIGH");
    }

    @Test
    public void testDetermineRiskLevel_Medium() {
        String risk = DeviceHealthCalculator.determineRiskLevel(70.0);
        assertEquals("MEDIUM", risk, "健康分60-80应为MEDIUM");
    }

    @Test
    public void testDetermineRiskLevel_Low() {
        String risk = DeviceHealthCalculator.determineRiskLevel(85.0);
        assertEquals("LOW", risk, "健康分≥80应为LOW");
    }

    @Test
    public void testDetermineRiskLevel_BoundaryValues() {
        // 边界值测试
        assertEquals("CRITICAL", DeviceHealthCalculator.determineRiskLevel(39.99), "39.99应为CRITICAL");
        assertEquals("HIGH", DeviceHealthCalculator.determineRiskLevel(40.0), "40.0应为HIGH");
        assertEquals("HIGH", DeviceHealthCalculator.determineRiskLevel(59.99), "59.99应为HIGH");
        assertEquals("MEDIUM", DeviceHealthCalculator.determineRiskLevel(60.0), "60.0应为MEDIUM");
        assertEquals("MEDIUM", DeviceHealthCalculator.determineRiskLevel(79.99), "79.99应为MEDIUM");
        assertEquals("LOW", DeviceHealthCalculator.determineRiskLevel(80.0), "80.0应为LOW");
    }

    @Test
    public void testDetermineRiskLevel_CustomThresholds() {
        // 自定义阈值测试
        String risk = DeviceHealthCalculator.determineRiskLevel(45.0, 50.0, 70.0, 85.0);
        assertEquals("CRITICAL", risk, "45<50应为CRITICAL（自定义阈值）");
    }

    @Test
    public void testDetermineRiskLevel_NullThresholds() {
        // null阈值应使用默认值
        String risk = DeviceHealthCalculator.determineRiskLevel(50.0, null, null, null);
        assertEquals("HIGH", risk, "null阈值应使用默认值");
    }

    // ================================================================
    // 工具方法测试
    // ================================================================

    @Test
    public void testRound_TwoDecimals() {
        double result = DeviceHealthCalculator.round(74.567, 2);
        assertEquals(74.57, result, 0.001, "四舍五入到2位小数应正确");
    }

    @Test
    public void testRound_FourDecimals() {
        double result = DeviceHealthCalculator.round(0.12345, 4);
        assertEquals(0.1235, result, 0.00001, "四舍五入到4位小数应正确");
    }

    @Test
    public void testRound_Zero() {
        double result = DeviceHealthCalculator.round(74.5, 0);
        assertEquals(75.0, result, 0.01, "四舍五入到整数应正确");
    }

    @Test
    public void testIsValidScore_Valid() {
        assertTrue(DeviceHealthCalculator.isValidScore(50.0), "50应为有效评分");
        assertTrue(DeviceHealthCalculator.isValidScore(0.0), "0应为有效评分");
        assertTrue(DeviceHealthCalculator.isValidScore(100.0), "100应为有效评分");
    }

    @Test
    public void testIsValidScore_Invalid() {
        assertFalse(DeviceHealthCalculator.isValidScore(-1.0), "-1应为无效评分");
        assertFalse(DeviceHealthCalculator.isValidScore(101.0), "101应为无效评分");
        assertFalse(DeviceHealthCalculator.isValidScore(Double.NaN), "NaN应为无效评分");
    }

    // ================================================================
    // 综合场景测试
    // ================================================================

    @Test
    public void testHealthEvaluation_HealthyDevice() {
        // 场景：健康设备
        // 运行时长：500h/720h = 69%（正常）→ 85分
        // MTBF：1500h（优秀）→ 90分
        // 工单数：1次（偶发）→ 85分
        // 换件数：2个（正常）→ 85分
        double runtimeScore = DeviceHealthCalculator.calcRuntimeScore(500, 720.0);
        double faultScore = DeviceHealthCalculator.calcFaultScore(1500.0);
        double workorderScore = DeviceHealthCalculator.calcWorkorderScore(1);
        double replacementScore = DeviceHealthCalculator.calcReplacementScore(2);
        double healthScore = DeviceHealthCalculator.calcHealthScore(
                runtimeScore, faultScore, workorderScore, replacementScore);
        String riskLevel = DeviceHealthCalculator.determineRiskLevel(healthScore);

        assertEquals(85.0, runtimeScore, 0.01);
        assertEquals(90.0, faultScore, 0.01);
        assertEquals(85.0, workorderScore, 0.01);
        assertEquals(85.0, replacementScore, 0.01);
        // 综合：85*0.25 + 90*0.35 + 85*0.20 + 85*0.20 = 21.25 + 31.5 + 17 + 17 = 86.75
        assertEquals(86.75, healthScore, 0.01);
        assertEquals("LOW", riskLevel);
    }

    @Test
    public void testHealthEvaluation_CriticalDevice() {
        // 场景：严重风险设备
        // 运行时长：700h/720h = 97%（过载）→ 40分
        // MTBF：150h（警戒）→ 30分
        // 工单数：12次（严重）→ 30分
        // 换件数：25个（严重）→ 30分
        double runtimeScore = DeviceHealthCalculator.calcRuntimeScore(700, 720.0);
        double faultScore = DeviceHealthCalculator.calcFaultScore(150.0);
        double workorderScore = DeviceHealthCalculator.calcWorkorderScore(12);
        double replacementScore = DeviceHealthCalculator.calcReplacementScore(25);
        double healthScore = DeviceHealthCalculator.calcHealthScore(
                runtimeScore, faultScore, workorderScore, replacementScore);
        String riskLevel = DeviceHealthCalculator.determineRiskLevel(healthScore);

        assertEquals(40.0, runtimeScore, 0.01);
        assertEquals(30.0, faultScore, 0.01);
        assertEquals(30.0, workorderScore, 0.01);
        assertEquals(30.0, replacementScore, 0.01);
        // 综合：40*0.25 + 30*0.35 + 30*0.20 + 30*0.20 = 10 + 10.5 + 6 + 6 = 32.5
        assertEquals(32.5, healthScore, 0.01);
        assertEquals("CRITICAL", riskLevel);
    }
}
