package com.langdong.spare.forecast.calibration;

/**
 * Platt scaling：拟合 sigmoid 校准映射 {@code P(y=1|f) = 1 / (1 + exp(A·f + B))}。
 *
 * <p>作为保序回归在正样本极少时的回退方案（模块 E）。采用 Lin, Lin &amp; Weng (2007) 的
 * 数值稳定实现（带先验目标 t+/t- 与牛顿-回溯）。输入 {@code f} 为原始概率或分数。</p>
 */
public class PlattScaling {

    private double a = -1.0;
    private double b = 0.0;

    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }

    public void setParams(double a, double b) {
        this.a = a;
        this.b = b;
    }

    /**
     * 拟合 A、B（Lin-Lin-Weng 算法）。
     *
     * @param scores 原始分数/概率
     * @param labels 标签（0/1）
     */
    public void fit(double[] scores, int[] labels) {
        if (scores == null || labels == null || scores.length != labels.length || scores.length == 0) {
            throw new IllegalArgumentException("platt fit 输入非法");
        }
        int len = scores.length;
        int prior1 = 0;
        for (int label : labels) {
            if (label > 0) {
                prior1++;
            }
        }
        int prior0 = len - prior1;

        double hiTarget = (prior1 + 1.0) / (prior1 + 2.0);
        double loTarget = 1.0 / (prior0 + 2.0);
        double[] t = new double[len];
        for (int i = 0; i < len; i++) {
            t[i] = labels[i] > 0 ? hiTarget : loTarget;
        }

        a = 0.0;
        b = Math.log((prior0 + 1.0) / (prior1 + 1.0));
        double lambda = 1e-3;
        double olderr = 1e300;
        int maxIter = 100;
        double minStep = 1e-10;
        double sigma = 1e-12;

        for (int it = 0; it < maxIter; it++) {
            double h11 = sigma;
            double h22 = sigma;
            double h21 = 0;
            double g1 = 0;
            double g2 = 0;
            for (int i = 0; i < len; i++) {
                double fApB = scores[i] * a + b;
                double p;
                double q;
                if (fApB >= 0) {
                    p = Math.exp(-fApB) / (1.0 + Math.exp(-fApB));
                    q = 1.0 / (1.0 + Math.exp(-fApB));
                } else {
                    p = 1.0 / (1.0 + Math.exp(fApB));
                    q = Math.exp(fApB) / (1.0 + Math.exp(fApB));
                }
                double d2 = p * q;
                h11 += scores[i] * scores[i] * d2;
                h22 += d2;
                h21 += scores[i] * d2;
                double d1 = t[i] - p;
                g1 += scores[i] * d1;
                g2 += d1;
            }
            if (Math.abs(g1) < 1e-5 && Math.abs(g2) < 1e-5) {
                break;
            }
            double det = h11 * h22 - h21 * h21;
            double da = -(h22 * g1 - h21 * g2) / det;
            double db = -(-h21 * g1 + h11 * g2) / det;
            double gd = g1 * da + g2 * db;
            double stepSize = 1.0;
            while (stepSize >= minStep) {
                double newA = a + stepSize * da;
                double newB = b + stepSize * db;
                double newErr = 0;
                for (int i = 0; i < len; i++) {
                    double fApB = scores[i] * newA + newB;
                    if (fApB >= 0) {
                        newErr += t[i] * fApB + Math.log(1 + Math.exp(-fApB));
                    } else {
                        newErr += (t[i] - 1) * fApB + Math.log(1 + Math.exp(fApB));
                    }
                }
                if (newErr < olderr + 0.0001 * stepSize * gd) {
                    a = newA;
                    b = newB;
                    olderr = newErr;
                    break;
                }
                stepSize /= 2.0;
            }
            if (stepSize < minStep) {
                break;
            }
        }
    }

    /** 校准：返回 1 / (1 + exp(A·f + B))。 */
    public double predict(double f) {
        double fApB = f * a + b;
        if (fApB >= 0) {
            return Math.exp(-fApB) / (1.0 + Math.exp(-fApB));
        }
        return 1.0 / (1.0 + Math.exp(fApB));
    }
}
