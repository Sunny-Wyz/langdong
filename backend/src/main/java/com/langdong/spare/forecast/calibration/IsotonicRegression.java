package com.langdong.spare.forecast.calibration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 保序回归（Isotonic Regression，非降），用 PAV（Pool Adjacent Violators）算法拟合。
 *
 * <p>用于第一阶段概率校准（模块 E）：以原始发生概率为 x、真实标签(0/1)为 y，拟合一个单调非降的
 * 映射，缓解树模型在小样本下概率偏向极端的问题。预测时在拟合结点间做线性插值，并裁剪到 [0,1]。</p>
 *
 * <p>纯实现、无外部依赖，便于独立单测。</p>
 */
public class IsotonicRegression {

    /** 拟合结点的 x（升序、去重后）。 */
    private double[] xs;
    /** 拟合结点的 y（非降）。 */
    private double[] ys;

    /**
     * 拟合保序回归。
     *
     * @param x 自变量（原始概率）
     * @param y 因变量（标签 0/1 或目标值）
     */
    public void fit(double[] x, double[] y) {
        if (x == null || y == null || x.length != y.length || x.length == 0) {
            throw new IllegalArgumentException("isotonic fit 输入非法");
        }
        int n = x.length;

        // 1. 按 x 升序排序
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        Arrays.sort(idx, (a, b) -> Double.compare(x[a], x[b]));

        // 2. 合并相同 x（加权平均 y）
        List<Double> ux = new ArrayList<>();
        List<Double> uy = new ArrayList<>();
        List<Double> uw = new ArrayList<>();
        int i = 0;
        while (i < n) {
            double cx = x[idx[i]];
            double sumY = 0;
            double w = 0;
            int j = i;
            while (j < n && x[idx[j]] == cx) {
                sumY += y[idx[j]];
                w += 1.0;
                j++;
            }
            ux.add(cx);
            uy.add(sumY / w);
            uw.add(w);
            i = j;
        }

        // 3. PAV：维护块栈，每块记录 (加权均值, 权重和, 所含唯一点数)，遇违反非降则合并
        int m = ux.size();
        double[] value = new double[m];
        double[] weight = new double[m];
        int[] count = new int[m];
        int top = 0; // 块数
        for (int k = 0; k < m; k++) {
            value[top] = uy.get(k);
            weight[top] = uw.get(k);
            count[top] = 1;
            top++;
            while (top >= 2 && value[top - 2] > value[top - 1]) {
                double wSum = weight[top - 2] + weight[top - 1];
                value[top - 2] = (value[top - 2] * weight[top - 2] + value[top - 1] * weight[top - 1]) / wSum;
                weight[top - 2] = wSum;
                count[top - 2] += count[top - 1];
                top--;
            }
        }

        // 4. 按每块所含唯一点数展开回填
        double[] fitted = new double[m];
        int pos = 0;
        for (int bIdx = 0; bIdx < top; bIdx++) {
            for (int c = 0; c < count[bIdx]; c++) {
                fitted[pos++] = value[bIdx];
            }
        }

        this.xs = new double[m];
        this.ys = new double[m];
        for (int k = 0; k < m; k++) {
            xs[k] = ux.get(k);
            ys[k] = clamp01(fitted[k]);
        }
    }

    /**
     * 预测：在拟合结点间线性插值，范围外裁剪到端点，结果裁剪到 [0,1]。
     */
    public double predict(double v) {
        if (xs == null || xs.length == 0) {
            return clamp01(v);
        }
        if (v <= xs[0]) {
            return ys[0];
        }
        if (v >= xs[xs.length - 1]) {
            return ys[ys.length - 1];
        }
        // 二分找区间
        int lo = 0;
        int hi = xs.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (xs[mid] <= v) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double t = (v - xs[lo]) / (xs[hi] - xs[lo]);
        return clamp01(ys[lo] + t * (ys[hi] - ys[lo]));
    }

    public double[] getKnotsX() {
        return xs == null ? new double[0] : xs.clone();
    }

    public double[] getKnotsY() {
        return ys == null ? new double[0] : ys.clone();
    }

    /** 从已有结点直接构造（用于反序列化）。 */
    public void setKnots(double[] knotsX, double[] knotsY) {
        this.xs = knotsX == null ? new double[0] : knotsX.clone();
        this.ys = knotsY == null ? new double[0] : knotsY.clone();
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        return Math.min(v, 1);
    }
}
