package com.langdong.spare.util;

import java.util.List;

/**
 * 备件分类计算工具类（ABC/XYZ 分析）
 *
 * 该类包含所有分类计算的纯静态方法，不依赖 Spring 上下文，
 * 便于独立单元测试。
 *
 * ABC分类规则：多维度加权评分（满分100分）
 *   - 年消耗金额  权重40%
 *   - 设备关键度  权重30%
 *   - 采购提前期  权重20%
 *   - 供应替代难度 权重10%
 *
 * XYZ分类规则：按需求变异系数 CV² 划分
 *   X: CV² < 0.5，Y: 0.5 ≤ CV² < 1.0，Z: CV² ≥ 1.0（或数据不足3月）
 */
public class ClassifyCalculator {

    // ================================================================
    // ABC 分类 — 单维度得分计算
    // ================================================================

    /**
     * 计算年消耗金额得分（min-max 归一到 0~100）
     * 如果最大金额为0（所有备件均无消耗），得分统一返回0
     *
     * @param annualCost    该备件的年消耗金额
     * @param maxAnnualCost 所有备件中年消耗金额的最大值（用于归一化）
     * @return 0~100 的得分
     */
    public static double calcAnnualCostScore(double annualCost, double maxAnnualCost) {
        if (maxAnnualCost <= 0) {
            return 0.0;
        }
        return (annualCost / maxAnnualCost) * 100.0;
    }

    /**
     * 计算设备关键度得分
     * is_critical=1（关键备件）→ 100分，is_critical=0（非关键）→ 0分
     *
     * @param isCritical 备件关键度标志（1=关键，0=非关键，null视为0）
     * @return 0 或 100
     */
    public static double calcCriticalScore(Integer isCritical) {
        return (isCritical != null && isCritical == 1) ? 100.0 : 0.0;
    }

    /**
     * 计算采购提前期得分
     * 提前期越长，得分越高（采购风险越大）
     *   > 30天 → 100分
     *   15~30天 → 60分
     *   < 15天 → 20分
     *
     * @param leadTime 采购提前期（天），null视为30天
     * @return 20 / 60 / 100
     */
    public static double calcLeadTimeScore(Integer leadTime) {
        int lt = (leadTime != null) ? leadTime : 30;
        if (lt > 30) {
            return 100.0;
        } else if (lt >= 15) {
            return 60.0;
        } else {
            return 20.0;
        }
    }

    /**
     * 计算供应替代难度得分
     * replace_diff 范围 1~5，线性映射到 0~100
     *   公式：(replaceDiff - 1) / 4.0 * 100
     *   replace_diff=1(容易) → 0分，replace_diff=5(极难) → 100分
     *
     * @param replaceDiff 供应替代难度（1~5），null或0视为默认值3
     * @return 0~100 的得分
     */
    public static double calcReplaceDiffScore(Integer replaceDiff) {
        int rd = (replaceDiff != null && replaceDiff >= 1 && replaceDiff <= 5) ? replaceDiff : 3;
        return (rd - 1) / 4.0 * 100.0;
    }

    /**
     * 计算 ABC 综合加权得分（满分100分）
     *
     * 权重：年消耗金额40% + 设备关键度30% + 采购提前期20% + 供应替代难度10%
     *
     * @param annualCostScore   年消耗金额得分（0~100）
     * @param criticalScore     设备关键度得分（0~100）
     * @param leadTimeScore     采购提前期得分（0~100）
     * @param replaceDiffScore  供应替代难度得分（0~100）
     * @return 综合加权得分（0~100）
     */
    public static double calcCompositeScore(double annualCostScore, double criticalScore,
                                             double leadTimeScore, double replaceDiffScore) {
        return annualCostScore  * 0.4
             + criticalScore    * 0.3
             + leadTimeScore    * 0.2
             + replaceDiffScore * 0.1;
    }

    // ================================================================
    // ABC 分类 — 按分位数分档
    // ================================================================

    /**
     * 根据综合得分在全量备件中的分位数确定 ABC 类别
     *
     * 规则：
     *   前20%（综合得分最高）→ A类
     *   21%~50%              → B类
     *   后50%（综合得分最低）→ C类
     *
     * @param totalCount    所有备件总数
     * @param rank          该备件按得分降序排列后的排名（从1开始）
     * @return "A" / "B" / "C"
     */
    public static String classifyABC(int totalCount, int rank) {
        if (totalCount <= 0 || rank <= 0) {
            return "C";
        }
        // 计算百分比位置（从高到低）
        double percentile = (double) rank / totalCount;
        if (percentile <= 0.20) {
            return "A";
        } else if (percentile <= 0.50) {
            return "B";
        } else {
            return "C";
        }
    }

    // ================================================================
    // XYZ 分类 — 需求变异系数计算
    // ================================================================

    /**
     * 计算需求变异系数 CV²
     * CV² = 月需求量标准差² / 月需求量均值²
     *
     * 若均值为0（从未有消耗），返回 Double.MAX_VALUE（视为极不稳定 → Z类）
     *
     * @param monthlyDemands 各月消耗量列表（单位：件，含0值月份）
     * @return CV² 值（≥0）
     */
    public static double calcCV2(List<Integer> monthlyDemands) {
        if (monthlyDemands == null || monthlyDemands.isEmpty()) {
            return Double.MAX_VALUE;
        }

        int n = monthlyDemands.size();
        // 计算均值
        double mean = monthlyDemands.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);

        if (mean <= 0) {
            // 均值为0：从未有消耗，视为极不稳定
            return Double.MAX_VALUE;
        }

        // 计算样本方差（除以 n，使用总体标准差）
        double variance = monthlyDemands.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average()
                .orElse(0.0);

        // CV² = 方差 / 均值²
        return variance / (mean * mean);
    }

    /**
     * 根据 CV² 值和数据月份数确定 XYZ 类别
     *
     * 规则：
     *   历史数据不足3个月 → 直接标记 Z类
     *   CV² < 0.5          → X类（需求稳定）
     *   0.5 ≤ CV² < 1.0   → Y类（需求波动）
     *   CV² ≥ 1.0          → Z类（需求随机）
     *
     * @param cv2        需求变异系数（由 calcCV2 计算得出）
     * @param dataMonths 实际有消耗记录的月份数（含0需求的月份不计入）
     * @return "X" / "Y" / "Z"
     */
    public static String classifyXYZ(double cv2, int dataMonths) {
        // 数据不足3个月，无法判断稳定性，标记为Z类
        if (dataMonths < 3) {
            return "Z";
        }
        if (cv2 < 0.5) {
            return "X";
        } else if (cv2 < 1.0) {
            return "Y";
        } else {
            return "Z";
        }
    }

    // ================================================================
    // 安全库存（SS）与补货触发点（ROP）计算
    // ================================================================

    /**
     * 根据 ABC 分类获取服务水平对应的安全系数 k
     *   A类 → k = 2.33（服务水平 ~99%）
     *   B类 → k = 1.65（服务水平 ~95%）
     *   C类 → k = 1.28（服务水平 ~90%）
     *
     * @param abcClass ABC分类结果（"A"/"B"/"C"）
     * @return 安全系数 k
     */
    public static double getKFactor(String abcClass) {
        if ("A".equals(abcClass)) {
            return 2.33;
        } else if ("B".equals(abcClass)) {
            return 1.65;
        } else {
            return 1.28;
        }
    }

    /**
     * 获取 ABC 分类对应的目标服务水平（%）
     */
    public static double getServiceLevel(String abcClass) {
        if ("A".equals(abcClass)) {
            return 99.0;
        } else if ("B".equals(abcClass)) {
            return 95.0;
        } else {
            return 90.0;
        }
    }

    /**
     * 计算安全库存 SS（Safety Stock）
     *
     * 公式：SS = k × σ_d × √L
     *   其中：
     *     k    = 安全系数（由 ABC 分类决定）
     *     σ_d  = 日均需求标准差 = 月需求标准差 / √22（按每月22个工作日换算）
     *     L    = 采购提前期（天）
     *
     * 结果向上取整，最小值为0
     *
     * @param abcClass       ABC分类结果
     * @param monthlyStdDev  月需求量标准差（件/月）
     * @param leadTime       采购提前期（天），null视为30天
     * @return 安全库存数量（件）
     */
    public static int calcSafetyStock(String abcClass, double monthlyStdDev, Integer leadTime) {
        double k  = getKFactor(abcClass);
        int    lt = (leadTime != null && leadTime > 0) ? leadTime : 30;
        // 月标准差换算为日均标准差（每月22个工作日）
        double dailyStdDev = monthlyStdDev / Math.sqrt(22.0);
        // 安全库存公式
        double ss = k * dailyStdDev * Math.sqrt(lt);
        return (int) Math.ceil(Math.max(ss, 0));
    }

    /**
     * 计算补货触发点 ROP（Reorder Point）
     *
     * 公式：ROP = 日均需求量 × L + SS
     *   其中：
     *     日均需求量 = 近12个月月均消耗量 / 22（每月22个工作日）
     *     L  = 采购提前期（天）
     *     SS = 安全库存
     *
     * 结果向上取整，最小值为0
     *
     * @param avgMonthlyQty  近12个月月均消耗量（件/月）
     * @param leadTime       采购提前期（天），null视为30天
     * @param safetyStock    安全库存（件）
     * @return 补货触发点数量（件）
     */
    public static int calcReorderPoint(double avgMonthlyQty, Integer leadTime, int safetyStock) {
        int lt = (leadTime != null && leadTime > 0) ? leadTime : 30;
        // 日均需求量 = 月均量 / 22个工作日
        double dailyAvg = avgMonthlyQty / 22.0;
        double rop = dailyAvg * lt + safetyStock;
        return (int) Math.ceil(Math.max(rop, 0));
    }

    /**
     * 计算月需求量标准差（用于 SS 计算中的 σ_d 推算）
     *
     * @param monthlyDemands 各月消耗量列表（件）
     * @return 月需求量标准差
     */
    public static double calcMonthlyStdDev(List<Integer> monthlyDemands) {
        if (monthlyDemands == null || monthlyDemands.isEmpty()) {
            return 0.0;
        }
        double mean = monthlyDemands.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
        double variance = monthlyDemands.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average()
                .orElse(0.0);
        return Math.sqrt(variance);
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 将数值四舍五入到指定小数位
     *
     * @param value  待四舍五入的值
     * @param places 保留小数位数
     * @return 处理后的值
     */
    public static double round(double value, int places) {
        if (places < 0) {
            return value;
        }
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
