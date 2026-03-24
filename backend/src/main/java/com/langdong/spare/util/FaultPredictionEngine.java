package com.langdong.spare.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备故障预测引擎（PHM - 预测性维护）
 *
 * 该类实现基于逻辑回归的设备故障预测算法（MVP阶段），
 * 不依赖 Spring 上下文，便于独立单元测试。
 *
 * 预测模型（固定系数逻辑回归）：
 *   logit = -2.5 + 0.003×avgRunHours + 1.2×faultFrequency - 0.5/avgMTBF + 2.0×deteriorationRate
 *   probability = sigmoid(logit) = 1 / (1 + e^(-logit))
 *
 * 特征工程：
 *   - avgRunHours: 平均运行时长（近12个月）
 *   - runHoursTrend: 运行时长线性趋势（正值表示递增）
 *   - faultFrequency: 故障频率（故障总数 / 月份数）
 *   - avgMTBF: 平均故障间隔时间（近12个月）
 *   - deteriorationRate: 劣化率（前3月健康度 - 最近3月健康度）/ 前3月健康度
 *
 * Phase 2 升级方向：替换为随机森林模型
 */
public class FaultPredictionEngine {

    // ================================================================
    // 常量定义
    // ================================================================

    /** 逻辑回归模型 - 截距 */
    private static final double LOGISTIC_INTERCEPT = -2.5;

    /** 逻辑回归模型 - 平均运行时长系数 */
    private static final double COEF_AVG_RUN_HOURS = 0.003;

    /** 逻辑回归模型 - 故障频率系数 */
    private static final double COEF_FAULT_FREQUENCY = 1.2;

    /** 逻辑回归模型 - MTBF系数 */
    private static final double COEF_MTBF = -0.5;

    /** 逻辑回归模型 - 劣化率系数 */
    private static final double COEF_DETERIORATION = 2.0;

    /** 置信区间 - Z值（90%置信度，对应Z=1.65） */
    private static final double Z_SCORE_90 = 1.65;

    /** 最小历史数据月数 */
    private static final int MIN_HISTORY_MONTHS = 6;

    // ================================================================
    // 特征工程
    // ================================================================

    /**
     * 计算平均运行时长（近N个月）
     *
     * @param runHours 运行时长列表（按时间顺序）
     * @return 平均运行时长（小时）
     */
    public static double calcAvgRunHours(List<Double> runHours) {
        if (runHours == null || runHours.isEmpty()) {
            return 0.0;
        }
        return runHours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * 计算运行时长线性趋势（斜率）
     *
     * 使用简单线性回归：y = a + bx，计算斜率b
     * 正值表示运行时长递增（负荷增加），负值表示递减
     *
     * @param runHours 运行时长列表（按时间顺序，索引0为最早）
     * @return 线性趋势斜率（正值=递增，负值=递减）
     */
    public static double calcRunHoursTrend(List<Double> runHours) {
        if (runHours == null || runHours.size() < 2) {
            return 0.0;
        }

        int n = runHours.size();
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0;

        for (int i = 0; i < n; i++) {
            double x = i; // 时间索引
            double y = runHours.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) {
            return 0.0;
        }

        // 斜率 b = (n*ΣXY - ΣX*ΣY) / (n*ΣX² - (ΣX)²)
        return (n * sumXY - sumX * sumY) / denominator;
    }

    /**
     * 计算故障频率（故障总数 / 月份数）
     *
     * @param faultCounts 故障次数列表（按月）
     * @return 平均月故障频率
     */
    public static double calcFaultFrequency(List<Integer> faultCounts) {
        if (faultCounts == null || faultCounts.isEmpty()) {
            return 0.0;
        }
        int totalFaults = faultCounts.stream().mapToInt(Integer::intValue).sum();
        return (double) totalFaults / faultCounts.size();
    }

    /**
     * 计算平均MTBF（平均故障间隔时间）
     *
     * @param mtbfValues MTBF值列表（按月）
     * @return 平均MTBF（小时），排除9999等标记值
     */
    public static double calcAvgMTBF(List<Double> mtbfValues) {
        if (mtbfValues == null || mtbfValues.isEmpty()) {
            return 0.0;
        }

        // 过滤掉无效值（9999表示无故障）
        List<Double> validValues = new ArrayList<>();
        for (Double mtbf : mtbfValues) {
            if (mtbf != null && mtbf > 0 && mtbf < 9000) {
                validValues.add(mtbf);
            }
        }

        if (validValues.isEmpty()) {
            return 9999.0; // 无有效故障记录
        }

        return validValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /**
     * 计算设备劣化率（健康度下降趋势）
     *
     * 劣化率 = (前3月平均健康度 - 最近3月平均健康度) / 前3月平均健康度
     * 正值表示健康度下降（劣化），负值表示改善
     *
     * @param healthScores 健康评分列表（按时间顺序，索引0为最早，至少6个月数据）
     * @return 劣化率（0~1，0表示无劣化，1表示完全劣化）
     */
    public static double calcDeteriorationRate(List<Double> healthScores) {
        if (healthScores == null || healthScores.size() < 6) {
            return 0.0;
        }

        int n = healthScores.size();

        // 前3个月的平均健康度（索引0~2）
        double firstThreeAvg = (healthScores.get(0) + healthScores.get(1) + healthScores.get(2)) / 3.0;

        // 最近3个月的平均健康度（索引n-3, n-2, n-1）
        double lastThreeAvg = (healthScores.get(n - 3) + healthScores.get(n - 2) + healthScores.get(n - 1)) / 3.0;

        if (firstThreeAvg <= 0) {
            return 0.0;
        }

        double deterioration = (firstThreeAvg - lastThreeAvg) / firstThreeAvg;
        // 限制在[0, 1]范围内
        return Math.max(0.0, Math.min(1.0, deterioration));
    }

    // ================================================================
    // 逻辑回归预测模型
    // ================================================================

    /**
     * 逻辑回归预测故障概率（固定系数模型 - MVP阶段）
     *
     * 模型公式：
     *   logit = -2.5 + 0.003×avgRunHours + 1.2×faultFrequency - 0.5/avgMTBF + 2.0×deteriorationRate
     *   probability = sigmoid(logit) = 1 / (1 + e^(-logit))
     *
     * @param avgRunHours 平均运行时长（小时）
     * @param faultFrequency 故障频率（次/月）
     * @param avgMTBF 平均MTBF（小时）
     * @param deteriorationRate 劣化率（0~1）
     * @return 故障概率（0~1）
     */
    public static double predictFailureProbability(double avgRunHours,
                                                    double faultFrequency,
                                                    double avgMTBF,
                                                    double deteriorationRate) {
        // 处理MTBF=0的情况
        double mtbfTerm = (avgMTBF > 0) ? (COEF_MTBF / avgMTBF) : 0.0;

        // 计算logit值
        double logit = LOGISTIC_INTERCEPT
                + COEF_AVG_RUN_HOURS * avgRunHours
                + COEF_FAULT_FREQUENCY * faultFrequency
                + mtbfTerm
                + COEF_DETERIORATION * deteriorationRate;

        // sigmoid函数：1 / (1 + e^(-logit))
        return sigmoid(logit);
    }

    /**
     * Sigmoid函数（逻辑函数）
     *
     * @param x 输入值
     * @return sigmoid(x) = 1 / (1 + e^(-x))，范围[0, 1]
     */
    private static double sigmoid(double x) {
        // 防止溢出：当x过大时，e^(-x)接近0
        if (x > 20) {
            return 1.0;
        } else if (x < -20) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-x));
    }

    // ================================================================
    // 预期故障次数与置信区间
    // ================================================================

    /**
     * 预测未来N天的预期故障次数（基于泊松分布）
     *
     * 预期故障次数 = 故障频率 × (预测天数 / 30)
     *
     * @param faultFrequency 月平均故障频率（次/月）
     * @param predictionDays 预测窗口天数
     * @return 预期故障次数（整数）
     */
    public static int predictExpectedFaults(double faultFrequency, int predictionDays) {
        double expected = faultFrequency * (predictionDays / 30.0);
        return (int) Math.round(expected);
    }

    /**
     * 计算预期故障次数的90%置信区间（下限）
     *
     * 使用泊松分布的近似正态分布：
     *   下限 = max(0, expected - Z * sqrt(expected))
     *   其中 Z = 1.65（90%置信度）
     *
     * @param expectedFaults 预期故障次数
     * @return 置信区间下限（整数）
     */
    public static int calcConfidenceIntervalLower(int expectedFaults) {
        if (expectedFaults <= 0) {
            return 0;
        }
        double stddev = Math.sqrt(expectedFaults);
        double lower = expectedFaults - Z_SCORE_90 * stddev;
        return Math.max(0, (int) Math.round(lower));
    }

    /**
     * 计算预期故障次数的90%置信区间（上限）
     *
     * 使用泊松分布的近似正态分布：
     *   上限 = expected + Z * sqrt(expected)
     *   其中 Z = 1.65（90%置信度）
     *
     * @param expectedFaults 预期故障次数
     * @return 置信区间上限（整数）
     */
    public static int calcConfidenceIntervalUpper(int expectedFaults) {
        if (expectedFaults <= 0) {
            return 0;
        }
        double stddev = Math.sqrt(expectedFaults);
        double upper = expectedFaults + Z_SCORE_90 * stddev;
        return (int) Math.round(upper);
    }

    // ================================================================
    // 特征重要性分析（用于前端可视化）
    // ================================================================

    /**
     * 计算各特征对预测结果的贡献度（特征重要性）
     *
     * 贡献度 = 特征值 × 系数 / |logit总值|
     *
     * @param avgRunHours 平均运行时长
     * @param faultFrequency 故障频率
     * @param avgMTBF 平均MTBF
     * @param deteriorationRate 劣化率
     * @return 特征重要性Map（key=特征名，value=贡献度百分比）
     */
    public static Map<String, Double> calcFeatureImportance(double avgRunHours,
                                                             double faultFrequency,
                                                             double avgMTBF,
                                                             double deteriorationRate) {
        // 计算各特征的贡献值
        double runHoursContrib = COEF_AVG_RUN_HOURS * avgRunHours;
        double faultFreqContrib = COEF_FAULT_FREQUENCY * faultFrequency;
        double mtbfContrib = (avgMTBF > 0) ? (COEF_MTBF / avgMTBF) : 0.0;
        double deteriorationContrib = COEF_DETERIORATION * deteriorationRate;

        // 总贡献值（绝对值之和）
        double totalContrib = Math.abs(runHoursContrib)
                + Math.abs(faultFreqContrib)
                + Math.abs(mtbfContrib)
                + Math.abs(deteriorationContrib);

        Map<String, Double> importance = new HashMap<>();
        if (totalContrib > 0) {
            importance.put("runHours", Math.abs(runHoursContrib) / totalContrib);
            importance.put("faultCount", Math.abs(faultFreqContrib) / totalContrib);
            importance.put("mtbf", Math.abs(mtbfContrib) / totalContrib);
            importance.put("deterioration", Math.abs(deteriorationContrib) / totalContrib);
        } else {
            // 无贡献时均分
            importance.put("runHours", 0.25);
            importance.put("faultCount", 0.25);
            importance.put("mtbf", 0.25);
            importance.put("deterioration", 0.25);
        }

        return importance;
    }

    // ================================================================
    // 工具方法
    // ================================================================

    /**
     * 验证历史数据是否足够进行预测
     *
     * @param dataPoints 历史数据点数量（月份数）
     * @return true表示数据充足，false表示不足
     */
    public static boolean hasEnoughHistoryData(int dataPoints) {
        return dataPoints >= MIN_HISTORY_MONTHS;
    }

    /**
     * 四舍五入到指定小数位数
     *
     * @param value 原始值
     * @param scale 小数位数
     * @return 四舍五入后的值
     */
    public static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }
}
