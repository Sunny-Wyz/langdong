package com.langdong.spare.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FaultPredictionEngine 工具类单元测试
 *
 * 测试覆盖率目标：>80%
 * 测试框架：JUnit 5
 */
public class FaultPredictionEngineTest {

    // ================================================================
    // 特征工程测试
    // ================================================================

    @Test
    public void testCalcAvgRunHours_Normal() {
        List<Double> runHours = Arrays.asList(650.0, 680.0, 700.0, 670.0, 690.0);
        double avg = FaultPredictionEngine.calcAvgRunHours(runHours);
        assertEquals(678.0, avg, 0.01, "平均运行时长计算应正确");
    }

    @Test
    public void testCalcAvgRunHours_EmptyList() {
        List<Double> runHours = new ArrayList<>();
        double avg = FaultPredictionEngine.calcAvgRunHours(runHours);
        assertEquals(0.0, avg, 0.01, "空列表应返回0");
    }

    @Test
    public void testCalcAvgRunHours_Null() {
        double avg = FaultPredictionEngine.calcAvgRunHours(null);
        assertEquals(0.0, avg, 0.01, "null列表应返回0");
    }

    @Test
    public void testCalcRunHoursTrend_Increasing() {
        // 递增趋势：500, 550, 600, 650, 700
        List<Double> runHours = Arrays.asList(500.0, 550.0, 600.0, 650.0, 700.0);
        double trend = FaultPredictionEngine.calcRunHoursTrend(runHours);
        assertTrue(trend > 0, "递增趋势斜率应为正值");
        assertEquals(50.0, trend, 1.0, "线性递增斜率应约为50");
    }

    @Test
    public void testCalcRunHoursTrend_Decreasing() {
        // 递减趋势：700, 650, 600, 550, 500
        List<Double> runHours = Arrays.asList(700.0, 650.0, 600.0, 550.0, 500.0);
        double trend = FaultPredictionEngine.calcRunHoursTrend(runHours);
        assertTrue(trend < 0, "递减趋势斜率应为负值");
        assertEquals(-50.0, trend, 1.0, "线性递减斜率应约为-50");
    }

    @Test
    public void testCalcRunHoursTrend_Stable() {
        // 稳定趋势：600, 600, 600, 600, 600
        List<Double> runHours = Arrays.asList(600.0, 600.0, 600.0, 600.0, 600.0);
        double trend = FaultPredictionEngine.calcRunHoursTrend(runHours);
        assertEquals(0.0, trend, 0.01, "稳定趋势斜率应为0");
    }

    @Test
    public void testCalcRunHoursTrend_TooFewPoints() {
        // 数据点不足（<2）
        List<Double> runHours = Arrays.asList(650.0);
        double trend = FaultPredictionEngine.calcRunHoursTrend(runHours);
        assertEquals(0.0, trend, 0.01, "数据点不足应返回0");
    }

    @Test
    public void testCalcRunHoursTrend_Null() {
        double trend = FaultPredictionEngine.calcRunHoursTrend(null);
        assertEquals(0.0, trend, 0.01, "null列表应返回0");
    }

    @Test
    public void testCalcFaultFrequency_Normal() {
        List<Integer> faultCounts = Arrays.asList(2, 3, 1, 4, 2, 3);
        double frequency = FaultPredictionEngine.calcFaultFrequency(faultCounts);
        // (2+3+1+4+2+3) / 6 = 15/6 = 2.5
        assertEquals(2.5, frequency, 0.01, "故障频率计算应正确");
    }

    @Test
    public void testCalcFaultFrequency_ZeroFaults() {
        List<Integer> faultCounts = Arrays.asList(0, 0, 0, 0);
        double frequency = FaultPredictionEngine.calcFaultFrequency(faultCounts);
        assertEquals(0.0, frequency, 0.01, "无故障时频率应为0");
    }

    @Test
    public void testCalcFaultFrequency_EmptyList() {
        List<Integer> faultCounts = new ArrayList<>();
        double frequency = FaultPredictionEngine.calcFaultFrequency(faultCounts);
        assertEquals(0.0, frequency, 0.01, "空列表应返回0");
    }

    @Test
    public void testCalcAvgMTBF_ValidValues() {
        List<Double> mtbfValues = Arrays.asList(800.0, 600.0, 750.0, 850.0);
        double avgMTBF = FaultPredictionEngine.calcAvgMTBF(mtbfValues);
        // (800+600+750+850) / 4 = 3000/4 = 750
        assertEquals(750.0, avgMTBF, 0.01, "平均MTBF计算应正确");
    }

    @Test
    public void testCalcAvgMTBF_WithInvalidValues() {
        // 包含9999（无故障标记）
        List<Double> mtbfValues = Arrays.asList(800.0, 9999.0, 600.0, 750.0);
        double avgMTBF = FaultPredictionEngine.calcAvgMTBF(mtbfValues);
        // 应过滤掉9999，计算 (800+600+750) / 3 = 716.67
        assertEquals(716.67, avgMTBF, 0.01, "应过滤掉无效值");
    }

    @Test
    public void testCalcAvgMTBF_AllInvalid() {
        // 全部为9999
        List<Double> mtbfValues = Arrays.asList(9999.0, 9999.0, 9999.0);
        double avgMTBF = FaultPredictionEngine.calcAvgMTBF(mtbfValues);
        assertEquals(9999.0, avgMTBF, 0.01, "全部无效时应返回9999");
    }

    @Test
    public void testCalcAvgMTBF_EmptyList() {
        List<Double> mtbfValues = new ArrayList<>();
        double avgMTBF = FaultPredictionEngine.calcAvgMTBF(mtbfValues);
        assertEquals(0.0, avgMTBF, 0.01, "空列表应返回0");
    }

    @Test
    public void testCalcDeteriorationRate_Deteriorating() {
        // 健康度下降：前3月平均75，后3月平均60
        // 劣化率 = (75-60)/75 = 0.2
        List<Double> healthScores = Arrays.asList(80.0, 75.0, 70.0, 65.0, 60.0, 55.0);
        double rate = FaultPredictionEngine.calcDeteriorationRate(healthScores);
        assertEquals(0.2, rate, 0.01, "劣化率计算应正确");
    }

    @Test
    public void testCalcDeteriorationRate_Improving() {
        // 健康度上升：前3月平均60，后3月平均75
        // 劣化率 = (60-75)/60 = -0.25，限制在0
        List<Double> healthScores = Arrays.asList(55.0, 60.0, 65.0, 70.0, 75.0, 80.0);
        double rate = FaultPredictionEngine.calcDeteriorationRate(healthScores);
        assertEquals(0.0, rate, 0.01, "改善时劣化率应为0");
    }

    @Test
    public void testCalcDeteriorationRate_Stable() {
        // 健康度稳定
        List<Double> healthScores = Arrays.asList(75.0, 75.0, 75.0, 75.0, 75.0, 75.0);
        double rate = FaultPredictionEngine.calcDeteriorationRate(healthScores);
        assertEquals(0.0, rate, 0.01, "稳定时劣化率应为0");
    }

    @Test
    public void testCalcDeteriorationRate_TooFewPoints() {
        // 数据点不足（<6）
        List<Double> healthScores = Arrays.asList(80.0, 75.0, 70.0);
        double rate = FaultPredictionEngine.calcDeteriorationRate(healthScores);
        assertEquals(0.0, rate, 0.01, "数据点不足应返回0");
    }

    @Test
    public void testCalcDeteriorationRate_Null() {
        double rate = FaultPredictionEngine.calcDeteriorationRate(null);
        assertEquals(0.0, rate, 0.01, "null列表应返回0");
    }

    // ================================================================
    // 逻辑回归预测模型测试
    // ================================================================

    @Test
    public void testPredictFailureProbability_HighRisk() {
        // 高风险场景：高运行时长、高故障频率、低MTBF、高劣化率
        double probability = FaultPredictionEngine.predictFailureProbability(
                700.0,  // 高运行时长
                3.5,    // 高故障频率
                200.0,  // 低MTBF
                0.8     // 高劣化率
        );
        assertTrue(probability > 0.5, "高风险场景概率应>0.5");
        assertTrue(probability <= 1.0, "概率应≤1.0");
    }

    @Test
    public void testPredictFailureProbability_LowRisk() {
        // 低风险场景：低运行时长、低故障频率、高MTBF、低劣化率
        double probability = FaultPredictionEngine.predictFailureProbability(
                400.0,   // 低运行时长
                0.5,     // 低故障频率
                1500.0,  // 高MTBF
                0.1      // 低劣化率
        );
        assertTrue(probability < 0.5, "低风险场景概率应<0.5");
        assertTrue(probability >= 0.0, "概率应≥0.0");
    }

    @Test
    public void testPredictFailureProbability_ZeroMTBF() {
        // MTBF为0的情况
        double probability = FaultPredictionEngine.predictFailureProbability(
                600.0, 2.0, 0.0, 0.3
        );
        assertTrue(probability >= 0.0 && probability <= 1.0, "概率应在0-1范围内");
    }

    @Test
    public void testPredictFailureProbability_BoundaryCheck() {
        // 极端值测试
        double prob1 = FaultPredictionEngine.predictFailureProbability(
                1000.0, 10.0, 50.0, 1.0
        );
        assertTrue(prob1 > 0.99, "极端高风险应接近1");

        double prob2 = FaultPredictionEngine.predictFailureProbability(
                100.0, 0.0, 9999.0, 0.0
        );
        assertTrue(prob2 < 0.1, "极端低风险应接近0");
    }

    // ================================================================
    // 预期故障次数与置信区间测试
    // ================================================================

    @Test
    public void testPredictExpectedFaults_Normal() {
        // 故障频率2.5次/月，预测90天
        int expectedFaults = FaultPredictionEngine.predictExpectedFaults(2.5, 90);
        // 2.5 * (90/30) = 2.5 * 3 = 7.5 ≈ 8
        assertEquals(8, expectedFaults, "预期故障次数计算应正确");
    }

    @Test
    public void testPredictExpectedFaults_ZeroFrequency() {
        int expectedFaults = FaultPredictionEngine.predictExpectedFaults(0.0, 90);
        assertEquals(0, expectedFaults, "零故障频率应返回0");
    }

    @Test
    public void testPredictExpectedFaults_ShortPeriod() {
        // 故障频率3.0次/月，预测30天
        int expectedFaults = FaultPredictionEngine.predictExpectedFaults(3.0, 30);
        // 3.0 * (30/30) = 3.0 * 1 = 3
        assertEquals(3, expectedFaults, "短期预测应正确");
    }

    @Test
    public void testCalcConfidenceIntervalLower_Normal() {
        // 预期故障10次，下限 = 10 - 1.65*sqrt(10) ≈ 10 - 5.22 ≈ 5
        int lower = FaultPredictionEngine.calcConfidenceIntervalLower(10);
        assertEquals(5, lower, "置信区间下限计算应正确");
    }

    @Test
    public void testCalcConfidenceIntervalLower_Zero() {
        int lower = FaultPredictionEngine.calcConfidenceIntervalLower(0);
        assertEquals(0, lower, "零故障时下限应为0");
    }

    @Test
    public void testCalcConfidenceIntervalLower_SmallValue() {
        // 预期故障2次，下限 = 2 - 1.65*sqrt(2) ≈ 2 - 2.33 ≈ 0（最小0）
        int lower = FaultPredictionEngine.calcConfidenceIntervalLower(2);
        assertEquals(0, lower, "小值时下限应不低于0");
    }

    @Test
    public void testCalcConfidenceIntervalUpper_Normal() {
        // 预期故障10次，上限 = 10 + 1.65*sqrt(10) ≈ 10 + 5.22 ≈ 15
        int upper = FaultPredictionEngine.calcConfidenceIntervalUpper(10);
        assertEquals(15, upper, "置信区间上限计算应正确");
    }

    @Test
    public void testCalcConfidenceIntervalUpper_Zero() {
        int upper = FaultPredictionEngine.calcConfidenceIntervalUpper(0);
        assertEquals(0, upper, "零故障时上限应为0");
    }

    @Test
    public void testCalcConfidenceIntervalUpper_LargeValue() {
        // 预期故障100次，上限 = 100 + 1.65*sqrt(100) ≈ 100 + 16.5 ≈ 117
        int upper = FaultPredictionEngine.calcConfidenceIntervalUpper(100);
        assertEquals(117, upper, "大值时上限计算应正确");
    }

    // ================================================================
    // 特征重要性分析测试
    // ================================================================

    @Test
    public void testCalcFeatureImportance_Normal() {
        Map<String, Double> importance = FaultPredictionEngine.calcFeatureImportance(
                650.0,  // 平均运行时长
                2.5,    // 故障频率
                600.0,  // 平均MTBF
                0.4     // 劣化率
        );

        assertNotNull(importance, "特征重要性不应为null");
        assertEquals(4, importance.size(), "应包含4个特征");
        assertTrue(importance.containsKey("runHours"), "应包含runHours特征");
        assertTrue(importance.containsKey("faultCount"), "应包含faultCount特征");
        assertTrue(importance.containsKey("mtbf"), "应包含mtbf特征");
        assertTrue(importance.containsKey("deterioration"), "应包含deterioration特征");

        // 验证特征重要性总和为1
        double sum = importance.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.01, "特征重要性总和应为1");

        // 验证所有值都在0-1范围内
        importance.values().forEach(value ->
                assertTrue(value >= 0.0 && value <= 1.0, "特征重要性应在0-1范围内")
        );
    }

    @Test
    public void testCalcFeatureImportance_ZeroContribution() {
        // 所有特征贡献为0
        Map<String, Double> importance = FaultPredictionEngine.calcFeatureImportance(
                0.0, 0.0, 0.0, 0.0
        );

        // 应均分
        assertEquals(0.25, importance.get("runHours"), 0.01);
        assertEquals(0.25, importance.get("faultCount"), 0.01);
        assertEquals(0.25, importance.get("mtbf"), 0.01);
        assertEquals(0.25, importance.get("deterioration"), 0.01);
    }

    @Test
    public void testCalcFeatureImportance_ZeroMTBF() {
        // MTBF为0
        Map<String, Double> importance = FaultPredictionEngine.calcFeatureImportance(
                600.0, 2.0, 0.0, 0.3
        );

        assertNotNull(importance, "特征重要性不应为null");
        double sum = importance.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1.0, sum, 0.01, "特征重要性总和应为1");
    }

    // ================================================================
    // 工具方法测试
    // ================================================================

    @Test
    public void testHasEnoughHistoryData_Sufficient() {
        assertTrue(FaultPredictionEngine.hasEnoughHistoryData(6), "6个月数据应充足");
        assertTrue(FaultPredictionEngine.hasEnoughHistoryData(12), "12个月数据应充足");
    }

    @Test
    public void testHasEnoughHistoryData_Insufficient() {
        assertFalse(FaultPredictionEngine.hasEnoughHistoryData(3), "3个月数据应不足");
        assertFalse(FaultPredictionEngine.hasEnoughHistoryData(5), "5个月数据应不足");
    }

    @Test
    public void testRound_TwoDecimals() {
        double result = FaultPredictionEngine.round(0.12345, 2);
        assertEquals(0.12, result, 0.001, "四舍五入到2位小数应正确");
    }

    @Test
    public void testRound_FourDecimals() {
        double result = FaultPredictionEngine.round(0.12345, 4);
        assertEquals(0.1235, result, 0.00001, "四舍五入到4位小数应正确");
    }

    // ================================================================
    // 综合场景测试
    // ================================================================

    @Test
    public void testFullPredictionWorkflow_HighRiskDevice() {
        // 场景：高风险设备完整预测流程
        // 1. 历史数据特征提取
        List<Double> runHours = Arrays.asList(680.0, 690.0, 700.0, 710.0, 720.0, 730.0);
        List<Integer> faultCounts = Arrays.asList(3, 4, 5, 6, 5, 7);
        List<Double> mtbfValues = Arrays.asList(220.0, 200.0, 180.0, 170.0, 190.0, 160.0);
        List<Double> healthScores = Arrays.asList(75.0, 70.0, 65.0, 60.0, 55.0, 50.0);

        double avgRunHours = FaultPredictionEngine.calcAvgRunHours(runHours);
        double runHoursTrend = FaultPredictionEngine.calcRunHoursTrend(runHours);
        double faultFrequency = FaultPredictionEngine.calcFaultFrequency(faultCounts);
        double avgMTBF = FaultPredictionEngine.calcAvgMTBF(mtbfValues);
        double deteriorationRate = FaultPredictionEngine.calcDeteriorationRate(healthScores);

        // 2. 故障概率预测
        double failureProbability = FaultPredictionEngine.predictFailureProbability(
                avgRunHours, faultFrequency, avgMTBF, deteriorationRate
        );

        // 3. 预期故障次数
        int expectedFaults = FaultPredictionEngine.predictExpectedFaults(faultFrequency, 90);
        int lowerBound = FaultPredictionEngine.calcConfidenceIntervalLower(expectedFaults);
        int upperBound = FaultPredictionEngine.calcConfidenceIntervalUpper(expectedFaults);

        // 4. 特征重要性
        Map<String, Double> featureImportance = FaultPredictionEngine.calcFeatureImportance(
                avgRunHours, faultFrequency, avgMTBF, deteriorationRate
        );

        // 验证结果
        assertTrue(avgRunHours > 600, "平均运行时长应>600h");
        assertTrue(runHoursTrend > 0, "运行时长趋势应递增");
        assertTrue(faultFrequency > 3.0, "故障频率应>3次/月");
        assertTrue(avgMTBF < 250, "平均MTBF应<250h");
        assertTrue(deteriorationRate > 0.2, "劣化率应>0.2");
        assertTrue(failureProbability > 0.6, "故障概率应>0.6（高风险）");
        assertTrue(expectedFaults >= 10, "预期故障次数应>=10");
        assertTrue(lowerBound < expectedFaults, "下限应<预期值");
        assertTrue(upperBound > expectedFaults, "上限应>预期值");
        assertNotNull(featureImportance, "特征重要性不应为null");
    }

    @Test
    public void testFullPredictionWorkflow_LowRiskDevice() {
        // 场景：低风险设备完整预测流程（低运行时长、低故障频率）
        List<Double> runHours = Arrays.asList(400.0, 420.0, 410.0, 405.0, 415.0, 425.0);
        List<Integer> faultCounts = Arrays.asList(0, 0, 0, 1, 0, 0);
        List<Double> mtbfValues = Arrays.asList(9999.0, 9999.0, 9999.0, 1500.0, 9999.0, 9999.0);
        List<Double> healthScores = Arrays.asList(85.0, 87.0, 86.0, 88.0, 87.0, 89.0);

        double avgRunHours = FaultPredictionEngine.calcAvgRunHours(runHours);
        double faultFrequency = FaultPredictionEngine.calcFaultFrequency(faultCounts);
        double avgMTBF = FaultPredictionEngine.calcAvgMTBF(mtbfValues);
        double deteriorationRate = FaultPredictionEngine.calcDeteriorationRate(healthScores);

        double failureProbability = FaultPredictionEngine.predictFailureProbability(
                avgRunHours, faultFrequency, avgMTBF, deteriorationRate
        );

        int expectedFaults = FaultPredictionEngine.predictExpectedFaults(faultFrequency, 90);

        // 验证结果
        assertTrue(avgRunHours < 450, "平均运行时长应<450h");
        assertTrue(faultFrequency < 0.2, "故障频率应<0.2次/月");
        assertTrue(avgMTBF > 1000 || avgMTBF == 9999, "平均MTBF应>1000h或为9999");
        assertEquals(0.0, deteriorationRate, 0.01, "劣化率应为0（改善中）");
        assertTrue(failureProbability < 0.3, "故障概率应<0.3（低风险）");
        assertTrue(expectedFaults <= 1, "预期故障次数应<=1");
    }
}
