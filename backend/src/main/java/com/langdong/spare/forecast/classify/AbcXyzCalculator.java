package com.langdong.spare.forecast.classify;

import com.langdong.spare.forecast.config.ForecastProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ABC×XYZ 分类纯计算工具（论文 4.2.2 / 4.1.3.2）。
 *
 * <p>与现有 F9 的 {@code util.ClassifyCalculator} 相互独立、互不影响：本类严格按论文规格实现，
 * 仅服务于两阶段预测的 ABC_code / XYZ_code 特征。</p>
 *
 * <ul>
 *   <li><b>ABC</b>：四维（年消耗金额 0.40、设备关键度 0.25、采购提前期 0.20、供应替代难度 0.15）
 *       各自 min-max 归一到 [0,1] 后加权求和得 composite_score；再按 composite_score 降序，
 *       累计占比前 70% 为 A、70%~90% 为 B、其余 C（帕累托法）。</li>
 *   <li><b>XYZ</b>：按需求变异系数 CV²（= 方差/均值²，基于历史月度消耗，含 0 需求月）分档：
 *       CV² &lt; 0.5 → X；0.5 ≤ CV² &lt; 1.0 → Y；CV² ≥ 1.0 → Z。</li>
 *   <li><b>编码（论文表 3-2 写死，不得改）</b>：ABC_code A=3/B=2/C=1；XYZ_code X=1/Y=2/Z=3。</li>
 * </ul>
 *
 * <p>纯静态、无状态，不依赖 Spring 上下文，便于独立单测。</p>
 */
public final class AbcXyzCalculator {

    private AbcXyzCalculator() {
    }

    /**
     * 单个备件参与 ABC 评分的原始四维输入 + XYZ 的月度消耗序列。
     *
     * @param partCode      备件编码
     * @param annualCost    年消耗金额原始值（price × 窗口内消耗量），越大越重要
     * @param criticalityRaw 设备关键度原始值（⚠️默认：关键=1、非关键=0）
     * @param leadTimeRaw   采购提前期原始值（天），越长越重要
     * @param replaceDiffRaw 供应替代难度原始值（1~5）
     * @param monthlyDemands 历史月度消耗序列（含 0 需求月，仅统计到分类月份前一月）
     */
    public record PartInput(String partCode,
                            double annualCost,
                            double criticalityRaw,
                            double leadTimeRaw,
                            double replaceDiffRaw,
                            List<Double> monthlyDemands) {
    }

    /**
     * 单个备件的分类结果。
     *
     * @param abcClass       A/B/C
     * @param xyzClass       X/Y/Z
     * @param abcCode        A=3/B=2/C=1
     * @param xyzCode        X=1/Y=2/Z=3
     * @param compositeScore ABC 综合加权得分（[0,1] 区间）
     * @param cv2            需求变异系数 CV²
     * @param annualCost     年消耗金额原始值（元）
     */
    public record Classification(String abcClass, String xyzClass,
                                 int abcCode, int xyzCode,
                                 double compositeScore, double cv2,
                                 double annualCost) {
    }

    // ================================================================
    // 归一化与编码
    // ================================================================

    /**
     * min-max 归一化到 [0,1]。当 max==min（全相等）时统一返回 0，避免除零。
     */
    public static double[] minMax(double[] xs) {
        double[] out = new double[xs.length];
        if (xs.length == 0) {
            return out;
        }
        double min = xs[0];
        double max = xs[0];
        for (double x : xs) {
            if (x < min) {
                min = x;
            }
            if (x > max) {
                max = x;
            }
        }
        double range = max - min;
        if (range <= 0) {
            return out; // 全相等 → 全 0
        }
        for (int i = 0; i < xs.length; i++) {
            out[i] = (xs[i] - min) / range;
        }
        return out;
    }

    /** ABC 等级编码（论文表 3-2）：A=3, B=2, C=1。 */
    public static int abcCode(String abcClass) {
        if ("A".equals(abcClass)) {
            return 3;
        }
        if ("B".equals(abcClass)) {
            return 2;
        }
        return 1;
    }

    /** XYZ 等级编码（论文表 3-2）：X=1, Y=2, Z=3。 */
    public static int xyzCode(String xyzClass) {
        if ("X".equals(xyzClass)) {
            return 1;
        }
        if ("Y".equals(xyzClass)) {
            return 2;
        }
        return 3;
    }

    // ================================================================
    // XYZ：CV² 与分档
    // ================================================================

    /**
     * 计算需求变异系数 CV² = 方差 / 均值²（基于给定月度序列，含 0 需求月）。
     * 序列为空或均值 ≤ 0（从未消耗）时返回 {@link Double#MAX_VALUE}（视为极不稳定 → Z）。
     */
    public static double cv2(List<Double> monthlyDemands) {
        if (monthlyDemands == null || monthlyDemands.isEmpty()) {
            return Double.MAX_VALUE;
        }
        double mean = monthlyDemands.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (mean <= 0) {
            return Double.MAX_VALUE;
        }
        double variance = monthlyDemands.stream()
                .mapToDouble(d -> (d - mean) * (d - mean))
                .average().orElse(0.0);
        return variance / (mean * mean);
    }

    /**
     * 按 CV² 分档 XYZ。
     *
     * @param cv2  需求变异系数
     * @param cfg  阈值配置（xyzCutoffX=0.5, xyzCutoffY=1.0）
     */
    public static String xyzClass(double cv2, ForecastProperties.Classify cfg) {
        if (cv2 < cfg.getXyzCutoffX()) {
            return "X";
        }
        if (cv2 < cfg.getXyzCutoffY()) {
            return "Y";
        }
        return "Z";
    }

    // ================================================================
    // 批量分类（ABC 帕累托需要全量横向比较）
    // ================================================================

    /**
     * 对一批备件一次性计算 ABC×XYZ。
     *
     * <p>ABC 维度需横向 min-max 归一与帕累托累计分档，故必须批量处理；XYZ 维度按各自 CV² 独立分档。</p>
     *
     * @param inputs 全量备件的原始输入
     * @param cfg    权重与阈值配置
     * @return partCode → 分类结果；保持 inputs 的顺序（LinkedHashMap）
     */
    public static Map<String, Classification> classifyAll(List<PartInput> inputs,
                                                          ForecastProperties.Classify cfg) {
        Map<String, Classification> result = new LinkedHashMap<>();
        if (inputs == null || inputs.isEmpty()) {
            return result;
        }

        int n = inputs.size();
        double[] cost = new double[n];
        double[] crit = new double[n];
        double[] lead = new double[n];
        double[] repl = new double[n];
        for (int i = 0; i < n; i++) {
            PartInput in = inputs.get(i);
            cost[i] = in.annualCost();
            crit[i] = in.criticalityRaw();
            lead[i] = in.leadTimeRaw();
            repl[i] = in.replaceDiffRaw();
        }

        double[] nCost = minMax(cost);
        double[] nCrit = minMax(crit);
        double[] nLead = minMax(lead);
        double[] nRepl = minMax(repl);

        // composite_score（[0,1]）与 CV²
        double[] composite = new double[n];
        double[] cv2Arr = new double[n];
        double totalComposite = 0.0;
        for (int i = 0; i < n; i++) {
            composite[i] = nCost[i] * cfg.getWeightAnnualCost()
                    + nCrit[i] * cfg.getWeightCriticality()
                    + nLead[i] * cfg.getWeightLeadTime()
                    + nRepl[i] * cfg.getWeightReplaceDiff();
            cv2Arr[i] = cv2(inputs.get(i).monthlyDemands());
            totalComposite += composite[i];
        }

        // 帕累托：按 composite 降序累计占比分档
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            order.add(i);
        }
        order.sort((a, b) -> Double.compare(composite[b], composite[a]));

        String[] abc = new String[n];
        double cumulative = 0.0;
        for (int idx : order) {
            if (totalComposite <= 0) {
                // 所有 composite 均为 0（如全量单一备件或维度全相等）：统一归 C
                abc[idx] = "C";
                continue;
            }
            cumulative += composite[idx];
            double share = cumulative / totalComposite;
            if (share <= cfg.getAbcCutoffA()) {
                abc[idx] = "A";
            } else if (share <= cfg.getAbcCutoffB()) {
                abc[idx] = "B";
            } else {
                abc[idx] = "C";
            }
        }

        for (int i = 0; i < n; i++) {
            String xyz = xyzClass(cv2Arr[i], cfg);
            result.put(inputs.get(i).partCode(), new Classification(
                    abc[i], xyz, abcCode(abc[i]), xyzCode(xyz), composite[i], cv2Arr[i], cost[i]));
        }
        return result;
    }
}
