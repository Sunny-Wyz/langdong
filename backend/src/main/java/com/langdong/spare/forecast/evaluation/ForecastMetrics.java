package com.langdong.spare.forecast.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 论文口径指标：wMAPE / MASE / Brier / 条件 90% 覆盖率。
 */
public final class ForecastMetrics {

    private ForecastMetrics() {
    }

    public static double wmape(List<Double> actual, List<Double> pred) {
        double sumAbs = 0;
        double sumAct = 0;
        for (int i = 0; i < actual.size(); i++) {
            double y = actual.get(i);
            double yh = pred.get(i);
            sumAbs += Math.abs(yh - y);
            sumAct += Math.abs(y);
        }
        if (sumAct <= 1e-12) {
            return sumAbs <= 1e-12 ? 0.0 : 100.0;
        }
        return 100.0 * sumAbs / sumAct;
    }

    /** MASE：以滞后一期朴素预测为尺度（跨备件池化）。 */
    public static double mase(List<Double> actual, List<Double> pred, List<Double> naiveScaleErrors) {
        double sumAbs = 0;
        for (int i = 0; i < actual.size(); i++) {
            sumAbs += Math.abs(pred.get(i) - actual.get(i));
        }
        double scale = 0;
        int n = 0;
        for (Double e : naiveScaleErrors) {
            if (e != null && !e.isNaN()) {
                scale += Math.abs(e);
                n++;
            }
        }
        if (n == 0 || scale <= 1e-12) {
            return Double.NaN;
        }
        scale /= n;
        return (sumAbs / actual.size()) / scale;
    }

    public static double brier(List<Double> actual, List<Double> prob) {
        if (actual.isEmpty()) {
            return Double.NaN;
        }
        double s = 0;
        for (int i = 0; i < actual.size(); i++) {
            double ind = actual.get(i) > 0 ? 1.0 : 0.0;
            double p = clamp01(prob.get(i));
            s += (p - ind) * (p - ind);
        }
        return s / actual.size();
    }

    /** 仅正需求月：y∈[L,U] 比例（%）。 */
    public static Map<String, Object> conditionalCoverage90(List<Double> actual,
                                                            List<Double> lower,
                                                            List<Double> upper) {
        int n = 0, covered = 0;
        double widthSum = 0;
        for (int i = 0; i < actual.size(); i++) {
            double y = actual.get(i);
            if (y <= 0) {
                continue;
            }
            n++;
            double L = lower.get(i);
            double U = upper.get(i);
            if (y >= L && y <= U) {
                covered++;
            }
            widthSum += Math.max(0, U - L);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("positivePoints", n);
        m.put("covered", covered);
        m.put("missed", Math.max(0, n - covered));
        m.put("coverageRate", n == 0 ? null : round2(100.0 * covered / n));
        m.put("avgWidth", n == 0 ? null : round2(widthSum / n));
        return m;
    }

    public static Map<String, Object> summarizeGroup(String name,
                                                     List<Double> actual,
                                                     List<Double> predTwoStage,
                                                     List<Double> predSma,
                                                     List<Double> prob,
                                                     List<Double> lower,
                                                     List<Double> upper,
                                                     List<Double> naiveScale) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", name);
        m.put("n", actual.size());
        m.put("wmapeTwoStage", round2(wmape(actual, predTwoStage)));
        m.put("wmapeSma3", round2(wmape(actual, predSma)));
        m.put("mase", round4(mase(actual, predTwoStage, naiveScale)));
        m.put("brier", round4(brier(actual, prob)));
        m.putAll(prefix("cov_", conditionalCoverage90(actual, lower, upper)));
        return m;
    }

    private static Map<String, Object> prefix(String p, Map<String, Object> src) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            out.put(p + e.getKey(), e.getValue());
        }
        return out;
    }

    public static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0;
        }
        return Math.max(0, Math.min(1, v));
    }

    public static double round2(double v) {
        if (Double.isNaN(v)) {
            return Double.NaN;
        }
        return Math.round(v * 100.0) / 100.0;
    }

    public static double round4(double v) {
        if (Double.isNaN(v)) {
            return Double.NaN;
        }
        return Math.round(v * 10000.0) / 10000.0;
    }

    public static List<Double> emptyAligned(int n) {
        List<Double> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(0.0);
        }
        return list;
    }
}
