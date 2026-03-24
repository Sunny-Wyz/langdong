package com.langdong.spare.util;

import java.util.List;

/**
 * 设备健康评分计算工具类（PHM - 预测性维护）
 *
 * 该类包含所有设备健康评估的纯静态方法，不依赖 Spring 上下文，
 * 便于独立单元测试。
 *
 * 健康评分体系：多维度加权评分（满分100分）
 *   - 运行时长评分  权重25%（过载或闲置均扣分）
 *   - 故障频次评分  权重35%（基于MTBF指标）
 *   - 工单数量评分  权重20%（维修工单越多越差）
 *   - 换件频次评分  权重20%（备件更换越多越差）
 *
 * 风险等级判定：
 *   - CRITICAL（严重）: 健康分 < 40
 *   - HIGH（高风险）: 40 ≤ 健康分 < 60
 *   - MEDIUM（中风险）: 60 ≤ 健康分 < 80
 *   - LOW（低风险）: 健康分 ≥ 80
 *
 * 时间衰减策略：最近3个月数据权重分别为 1.0, 0.95, 0.90
 */
public class DeviceHealthCalculator {

    // ================================================================
    // 常量定义
    // ================================================================

    /** 标准月运行时长（小时），按720h/月（30天×24h）计算 */
    public static final double STANDARD_RUN_HOURS = 720.0;

    /** MTBF阈值 - 优秀（≥1000h） */
    public static final double MTBF_EXCELLENT = 1000.0;

    /** MTBF阈值 - 良好（≥500h） */
    public static final double MTBF_GOOD = 500.0;

    /** MTBF阈值 - 警戒（<200h） */
    public static final double MTBF_WARNING = 200.0;

    /** 工单数阈值 - 警戒（≥10） */
    public static final int WORKORDER_WARNING = 10;

    /** 换件数阈值 - 警戒（≥20） */
    public static final int REPLACEMENT_WARNING = 20;

    /** 时间衰减系数 - 当前月 */
    public static final double DECAY_CURRENT_MONTH = 1.0;

    /** 时间衰减系数 - 上一月 */
    public static final double DECAY_LAST_MONTH = 0.95;

    /** 时间衰减系数 - 2个月前 */
    public static final double DECAY_TWO_MONTHS_AGO = 0.90;

    // ================================================================
    // 单维度评分计算
    // ================================================================

    /**
     * 计算运行时长评分（0~100分）
     *
     * 评分逻辑：
     *   - 运行时长占标准时长的比例 > 95% → 40分（过载运行）
     *   - 运行时长占标准时长的比例 85%~95% → 70分（高负荷）
     *   - 运行时长占标准时长的比例 50%~85% → 85分（正常）
     *   - 运行时长占标准时长的比例 < 50% → 60分（闲置或维修频繁）
     *
     * @param runHours 月运行时长（小时）
     * @param standardHours 标准月运行时长（默认720h），null则使用默认值
     * @return 运行时长评分（0~100分）
     */
    public static double calcRuntimeScore(double runHours, Double standardHours) {
        double standard = (standardHours != null && standardHours > 0) ? standardHours : STANDARD_RUN_HOURS;
        double ratio = runHours / standard;

        if (ratio > 0.95) {
            // 过载运行，健康风险高
            return 40.0;
        } else if (ratio >= 0.85) {
            // 高负荷运行，略有风险
            return 70.0;
        } else if (ratio >= 0.50) {
            // 正常运行
            return 85.0;
        } else {
            // 闲置或频繁维修停机
            return 60.0;
        }
    }

    /**
     * 计算故障频次评分（0~100分）
     *
     * 基于MTBF（平均故障间隔时间）指标：
     *   - MTBF ≥ 1000h → 90分（优秀）
     *   - MTBF ≥ 500h → 75分（良好）
     *   - MTBF ≥ 200h → 50分（一般）
     *   - MTBF < 200h → 30分（警戒）
     *   - 无故障记录（MTBF=9999） → 100分（最佳）
     *
     * @param mtbf 平均故障间隔时间（小时），null或0视为无故障
     * @return 故障频次评分（0~100分）
     */
    public static double calcFaultScore(Double mtbf) {
        if (mtbf == null || mtbf <= 0) {
            // 无故障记录，满分
            return 100.0;
        }

        if (mtbf >= 9000) {
            // 近乎无故障（9999表示设备特征计算时的无故障标记）
            return 100.0;
        } else if (mtbf >= MTBF_EXCELLENT) {
            return 90.0;
        } else if (mtbf >= MTBF_GOOD) {
            return 75.0;
        } else if (mtbf >= MTBF_WARNING) {
            return 50.0;
        } else {
            return 30.0;
        }
    }

    /**
     * 计算工单数量评分（0~100分）
     *
     * 评分逻辑：
     *   - 月工单数 = 0 → 100分（无维修）
     *   - 月工单数 1~2 → 85分（偶发维修）
     *   - 月工单数 3~5 → 65分（一般频次）
     *   - 月工单数 6~9 → 45分（频繁维修）
     *   - 月工单数 ≥ 10 → 30分（严重）
     *
     * @param workOrderCount 月工单数量
     * @return 工单数量评分（0~100分）
     */
    public static double calcWorkorderScore(int workOrderCount) {
        if (workOrderCount == 0) {
            return 100.0;
        } else if (workOrderCount <= 2) {
            return 85.0;
        } else if (workOrderCount <= 5) {
            return 65.0;
        } else if (workOrderCount < WORKORDER_WARNING) {
            return 45.0;
        } else {
            return 30.0;
        }
    }

    /**
     * 计算换件频次评分（0~100分）
     *
     * 评分逻辑：
     *   - 月换件数 = 0 → 100分（无换件）
     *   - 月换件数 1~5 → 85分（正常消耗）
     *   - 月换件数 6~10 → 70分（较多）
     *   - 月换件数 11~19 → 50分（频繁）
     *   - 月换件数 ≥ 20 → 30分（严重）
     *
     * @param replacementCount 月备件更换数量
     * @return 换件频次评分（0~100分）
     */
    public static double calcReplacementScore(int replacementCount) {
        if (replacementCount == 0) {
            return 100.0;
        } else if (replacementCount <= 5) {
            return 85.0;
        } else if (replacementCount <= 10) {
            return 70.0;
        } else if (replacementCount < REPLACEMENT_WARNING) {
            return 50.0;
        } else {
            return 30.0;
        }
    }

    // ================================================================
    // 综合评分计算
    // ================================================================

    /**
     * 计算设备综合健康评分（0~100分）
     *
     * 加权公式：
     *   健康分 = 运行时长评分 × 25% + 故障频次评分 × 35%
     *           + 工单数量评分 × 20% + 换件频次评分 × 20%
     *
     * @param runtimeScore 运行时长评分（0~100）
     * @param faultScore 故障频次评分（0~100）
     * @param workorderScore 工单数量评分（0~100）
     * @param replacementScore 换件频次评分（0~100）
     * @return 综合健康评分（0~100）
     */
    public static double calcHealthScore(double runtimeScore, double faultScore,
                                          double workorderScore, double replacementScore) {
        return runtimeScore * 0.25
                + faultScore * 0.35
                + workorderScore * 0.20
                + replacementScore * 0.20;
    }

    /**
     * 计算带时间衰减的综合健康评分（基于最近3个月数据）
     *
     * 时间衰减权重：
     *   - 当前月（索引0）：1.0
     *   - 上一月（索引1）：0.95
     *   - 2月前（索引2）：0.90
     *
     * 计算步骤：
     *   1. 对每个月的数据计算健康分
     *   2. 应用时间衰减系数
     *   3. 加权平均
     *
     * @param recentScores 最近3个月的健康评分列表（按时间倒序：当前月在前）
     * @return 带时间衰减的综合健康评分（0~100）
     */
    public static double calcHealthScoreWithDecay(List<Double> recentScores) {
        if (recentScores == null || recentScores.isEmpty()) {
            return 0.0;
        }

        // 衰减系数数组
        double[] decayFactors = {DECAY_CURRENT_MONTH, DECAY_LAST_MONTH, DECAY_TWO_MONTHS_AGO};

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (int i = 0; i < Math.min(recentScores.size(), 3); i++) {
            double score = recentScores.get(i);
            double weight = decayFactors[i];
            weightedSum += score * weight;
            totalWeight += weight;
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    // ================================================================
    // 风险等级判定
    // ================================================================

    /**
     * 根据健康评分判定风险等级
     *
     * 等级划分：
     *   - CRITICAL（严重）: 健康分 < 40
     *   - HIGH（高风险）: 40 ≤ 健康分 < 60
     *   - MEDIUM（中风险）: 60 ≤ 健康分 < 80
     *   - LOW（低风险）: 健康分 ≥ 80
     *
     * @param healthScore 健康评分（0~100）
     * @param criticalThreshold 严重风险阈值（默认40）
     * @param highThreshold 高风险阈值（默认60）
     * @param mediumThreshold 中风险阈值（默认80）
     * @return 风险等级字符串（CRITICAL/HIGH/MEDIUM/LOW）
     */
    public static String determineRiskLevel(double healthScore,
                                             Double criticalThreshold,
                                             Double highThreshold,
                                             Double mediumThreshold) {
        double criticalTh = (criticalThreshold != null) ? criticalThreshold : 40.0;
        double highTh = (highThreshold != null) ? highThreshold : 60.0;
        double mediumTh = (mediumThreshold != null) ? mediumThreshold : 80.0;

        if (healthScore < criticalTh) {
            return "CRITICAL";
        } else if (healthScore < highTh) {
            return "HIGH";
        } else if (healthScore < mediumTh) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * 使用默认阈值判定风险等级
     *
     * @param healthScore 健康评分（0~100）
     * @return 风险等级字符串（CRITICAL/HIGH/MEDIUM/LOW）
     */
    public static String determineRiskLevel(double healthScore) {
        return determineRiskLevel(healthScore, null, null, null);
    }

    // ================================================================
    // 工具方法
    // ================================================================

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

    /**
     * 验证健康评分是否在有效范围内（0~100）
     *
     * @param score 评分值
     * @return true表示有效，false表示无效
     */
    public static boolean isValidScore(double score) {
        return score >= 0.0 && score <= 100.0;
    }
}
