package com.langdong.spare.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 维护建议生成器（PHM - 预测性维护）
 *
 * 该类根据设备健康评分和故障预测结果，生成维护建议，
 * 不依赖 Spring 上下文，便于独立单元测试。
 *
 * 生成逻辑：
 *   1. 判断是否需要建议：健康分 ≥ 80 AND 故障概率 < 0.3 → 不生成
 *   2. 确定维护类型：EMERGENCY/PREDICTIVE/PREVENTIVE
 *   3. 确定优先级：HIGH/MEDIUM/LOW
 *   4. 计算时间窗口：HIGH→1-7天，MEDIUM→7-14天，LOW→14-30天
 *   5. 生成关联备件需求：基于评分维度
 *   6. 估算成本：备件成本 + 人工成本 + 停机损失
 */
public class MaintenanceSuggestionGenerator {

    // ================================================================
    // 常量定义
    // ================================================================

    /** 健康分阈值 - 无需建议 */
    public static final double HEALTH_SCORE_OK = 80.0;

    /** 故障概率阈值 - 无需建议 */
    public static final double FAILURE_PROB_LOW = 0.3;

    /** 故障概率阈值 - 紧急维护 */
    public static final double FAILURE_PROB_EMERGENCY = 0.8;

    /** 故障概率阈值 - 预测性维护 */
    public static final double FAILURE_PROB_PREDICTIVE = 0.5;

    /** 维护类型 - 紧急维护 */
    public static final String TYPE_EMERGENCY = "EMERGENCY";

    /** 维护类型 - 预测性维护 */
    public static final String TYPE_PREDICTIVE = "PREDICTIVE";

    /** 维护类型 - 预防性维护 */
    public static final String TYPE_PREVENTIVE = "PREVENTIVE";

    /** 优先级 - 高 */
    public static final String PRIORITY_HIGH = "HIGH";

    /** 优先级 - 中 */
    public static final String PRIORITY_MEDIUM = "MEDIUM";

    /** 优先级 - 低 */
    public static final String PRIORITY_LOW = "LOW";

    /** 人工成本 - 紧急维护（元） */
    private static final double LABOR_COST_EMERGENCY = 2000.0;

    /** 人工成本 - 预测性维护（元） */
    private static final double LABOR_COST_PREDICTIVE = 1500.0;

    /** 人工成本 - 预防性维护（元） */
    private static final double LABOR_COST_PREVENTIVE = 1000.0;

    /** 关键设备停机损失（元/小时） */
    private static final double DOWNTIME_LOSS_CRITICAL = 5000.0;

    /** 重要设备停机损失（元/小时） */
    private static final double DOWNTIME_LOSS_IMPORTANT = 2000.0;

    // ================================================================
    // 建议生成判断
    // ================================================================

    /**
     * 判断是否需要生成维护建议
     *
     * 判断逻辑：
     *   - 健康分 ≥ 80 AND 故障概率 < 0.3 → 不需要建议
     *   - 其他情况 → 需要建议
     *
     * @param healthScore 设备健康评分（0~100）
     * @param failureProbability 故障概率（0~1）
     * @return true表示需要生成建议，false表示不需要
     */
    public static boolean shouldGenerateSuggestion(double healthScore, double failureProbability) {
        // 设备健康且故障风险低，无需建议
        if (healthScore >= HEALTH_SCORE_OK && failureProbability < FAILURE_PROB_LOW) {
            return false;
        }
        return true;
    }

    // ================================================================
    // 维护类型与优先级判定
    // ================================================================

    /**
     * 确定维护类型
     *
     * 判定逻辑：
     *   - 风险=CRITICAL OR 故障概率 > 0.8 → EMERGENCY（紧急维护）
     *   - 风险=HIGH OR 故障概率 > 0.5 → PREDICTIVE（预测性维护）
     *   - 其他 → PREVENTIVE（预防性维护）
     *
     * @param riskLevel 风险等级（CRITICAL/HIGH/MEDIUM/LOW）
     * @param failureProbability 故障概率（0~1）
     * @return 维护类型（EMERGENCY/PREDICTIVE/PREVENTIVE）
     */
    public static String determineMaintenanceType(String riskLevel, double failureProbability) {
        if ("CRITICAL".equals(riskLevel) || failureProbability > FAILURE_PROB_EMERGENCY) {
            return TYPE_EMERGENCY;
        } else if ("HIGH".equals(riskLevel) || failureProbability > FAILURE_PROB_PREDICTIVE) {
            return TYPE_PREDICTIVE;
        } else {
            return TYPE_PREVENTIVE;
        }
    }

    /**
     * 确定维护优先级
     *
     * 判定逻辑：
     *   - 风险=CRITICAL OR 故障概率 > 0.8 → HIGH
     *   - 风险=HIGH OR 故障概率 > 0.5 → MEDIUM
     *   - 其他 → LOW
     *
     * @param riskLevel 风险等级（CRITICAL/HIGH/MEDIUM/LOW）
     * @param failureProbability 故障概率（0~1）
     * @return 优先级（HIGH/MEDIUM/LOW）
     */
    public static String determinePriorityLevel(String riskLevel, double failureProbability) {
        if ("CRITICAL".equals(riskLevel) || failureProbability > FAILURE_PROB_EMERGENCY) {
            return PRIORITY_HIGH;
        } else if ("HIGH".equals(riskLevel) || failureProbability > FAILURE_PROB_PREDICTIVE) {
            return PRIORITY_MEDIUM;
        } else {
            return PRIORITY_LOW;
        }
    }

    // ================================================================
    // 时间窗口计算
    // ================================================================

    /**
     * 计算建议的维护时间窗口（开始日期和结束日期）
     *
     * 时间窗口规则：
     *   - HIGH优先级：明天到7天内
     *   - MEDIUM优先级：7-14天
     *   - LOW优先级：14-30天
     *
     * @param priorityLevel 优先级（HIGH/MEDIUM/LOW）
     * @param referenceDate 参考日期（通常为当前日期）
     * @return Map包含startDate和endDate
     */
    public static Map<String, LocalDate> calcMaintenanceWindow(String priorityLevel, LocalDate referenceDate) {
        Map<String, LocalDate> window = new HashMap<>();
        LocalDate refDate = (referenceDate != null) ? referenceDate : LocalDate.now();

        if (PRIORITY_HIGH.equals(priorityLevel)) {
            // 明天到7天内
            window.put("startDate", refDate.plusDays(1));
            window.put("endDate", refDate.plusDays(7));
        } else if (PRIORITY_MEDIUM.equals(priorityLevel)) {
            // 7-14天
            window.put("startDate", refDate.plusDays(7));
            window.put("endDate", refDate.plusDays(14));
        } else {
            // 14-30天
            window.put("startDate", refDate.plusDays(14));
            window.put("endDate", refDate.plusDays(30));
        }

        return window;
    }

    // ================================================================
    // 关联备件需求生成
    // ================================================================

    /**
     * 生成建议原因说明文本
     *
     * @param riskLevel 风险等级
     * @param healthScore 健康评分
     * @param failureProbability 故障概率
     * @param predictedFaults 预期故障次数
     * @return 建议原因文本
     */
    public static String generateSuggestionReason(String riskLevel,
                                                   double healthScore,
                                                   double failureProbability,
                                                   int predictedFaults) {
        StringBuilder reason = new StringBuilder();
        reason.append("设备健康评分：").append(String.format("%.2f", healthScore)).append("分，");
        reason.append("风险等级：").append(translateRiskLevel(riskLevel)).append("；");
        reason.append("预测故障概率：").append(String.format("%.1f%%", failureProbability * 100)).append("，");
        reason.append("预期未来90天内发生").append(predictedFaults).append("次故障。");

        if ("CRITICAL".equals(riskLevel)) {
            reason.append("设备处于严重健康风险状态，建议立即安排维护，避免突发性故障导致生产中断。");
        } else if ("HIGH".equals(riskLevel)) {
            reason.append("设备健康状况不佳，建议尽快安排预测性维护，降低故障风险。");
        } else if ("MEDIUM".equals(riskLevel)) {
            reason.append("设备存在一定健康隐患，建议在2周内安排预防性检查和维护。");
        } else {
            reason.append("设备整体状况良好，建议按计划进行常规维护保养。");
        }

        return reason.toString();
    }

    /**
     * 翻译风险等级为中文
     *
     * @param riskLevel 风险等级英文
     * @return 风险等级中文
     */
    private static String translateRiskLevel(String riskLevel) {
        switch (riskLevel) {
            case "CRITICAL":
                return "严重";
            case "HIGH":
                return "高风险";
            case "MEDIUM":
                return "中风险";
            case "LOW":
                return "低风险";
            default:
                return "未知";
        }
    }

    /**
     * 根据评分维度判断需要准备的备件类型
     *
     * 判断逻辑：
     *   - 运行时长评分 < 50 → 易损件（轴承、密封圈、皮带）
     *   - 故障评分 < 60 → 关键部件（电机、传感器、控制器）
     *   - 预测故障次数 ≥ 2 → 常用备件（螺栓、垫片、润滑油）
     *
     * @param runtimeScore 运行时长评分（0~100）
     * @param faultScore 故障频次评分（0~100）
     * @param predictedFaults 预期故障次数
     * @return 建议备件类型列表（用于后续从设备配套关系中查询）
     */
    public static List<String> suggestSparePartCategories(double runtimeScore,
                                                           double faultScore,
                                                           int predictedFaults) {
        List<String> categories = new ArrayList<>();

        // 运行时长评分低（过载或频繁停机）→ 易损件
        if (runtimeScore < 50) {
            categories.add("WEAR_PARTS"); // 易损件：轴承、密封圈、皮带
        }

        // 故障评分低（MTBF低）→ 关键部件
        if (faultScore < 60) {
            categories.add("CRITICAL_PARTS"); // 关键部件：电机、传感器、控制器
        }

        // 预测故障次数高 → 常用备件
        if (predictedFaults >= 2) {
            categories.add("COMMON_PARTS"); // 常用备件：螺栓、垫片、润滑油
        }

        // 如果没有特定需求，至少包含常规维护备件
        if (categories.isEmpty()) {
            categories.add("ROUTINE_PARTS"); // 常规维护：滤芯、润滑油
        }

        return categories;
    }

    // ================================================================
    // 成本估算
    // ================================================================

    /**
     * 估算维护成本（备件成本 + 人工成本 + 停机损失）
     *
     * @param sparePartsCost 备件总成本（元）
     * @param maintenanceType 维护类型（EMERGENCY/PREDICTIVE/PREVENTIVE）
     * @param deviceImportance 设备重要性（CRITICAL/IMPORTANT/NORMAL）
     * @param estimatedDowntimeHours 预计停机时长（小时）
     * @return 总维护成本（元）
     */
    public static double estimateMaintenanceCost(double sparePartsCost,
                                                  String maintenanceType,
                                                  String deviceImportance,
                                                  double estimatedDowntimeHours) {
        // 人工成本（根据维护类型）
        double laborCost = calcLaborCost(maintenanceType);

        // 停机损失（根据设备重要性）
        double downtimeLoss = calcDowntimeLoss(deviceImportance, estimatedDowntimeHours);

        // 总成本 = 备件成本 + 人工成本 + 停机损失
        return sparePartsCost + laborCost + downtimeLoss;
    }

    /**
     * 计算人工成本（根据维护类型）
     *
     * @param maintenanceType 维护类型
     * @return 人工成本（元）
     */
    private static double calcLaborCost(String maintenanceType) {
        switch (maintenanceType) {
            case TYPE_EMERGENCY:
                return LABOR_COST_EMERGENCY;
            case TYPE_PREDICTIVE:
                return LABOR_COST_PREDICTIVE;
            case TYPE_PREVENTIVE:
                return LABOR_COST_PREVENTIVE;
            default:
                return LABOR_COST_PREVENTIVE;
        }
    }

    /**
     * 计算停机损失（根据设备重要性和停机时长）
     *
     * @param deviceImportance 设备重要性
     * @param downtimeHours 停机时长（小时）
     * @return 停机损失（元）
     */
    private static double calcDowntimeLoss(String deviceImportance, double downtimeHours) {
        double lossPerHour;

        switch (deviceImportance) {
            case "CRITICAL":
                lossPerHour = DOWNTIME_LOSS_CRITICAL;
                break;
            case "IMPORTANT":
                lossPerHour = DOWNTIME_LOSS_IMPORTANT;
                break;
            default:
                lossPerHour = 0.0; // 一般设备不计停机损失
        }

        return lossPerHour * downtimeHours;
    }

    /**
     * 根据维护类型估算停机时长（小时）
     *
     * @param maintenanceType 维护类型
     * @return 预计停机时长（小时）
     */
    public static double estimateDowntimeHours(String maintenanceType) {
        switch (maintenanceType) {
            case TYPE_EMERGENCY:
                return 8.0; // 紧急维护：8小时
            case TYPE_PREDICTIVE:
                return 4.0; // 预测性维护：4小时
            case TYPE_PREVENTIVE:
                return 2.0; // 预防性维护：2小时
            default:
                return 2.0;
        }
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
}
