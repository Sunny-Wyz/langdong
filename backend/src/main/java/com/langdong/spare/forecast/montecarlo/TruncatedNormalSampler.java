package com.langdong.spare.forecast.montecarlo;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.RandomGenerator;

/**
 * 截断正态分布采样工具（算法 3-2 截断正态部分）。
 *
 * <p>利用逆变换法对下界为 0 的截断正态分布 $TruncatedNormal(\mu, \sigma, 0)$ 进行采样。</p>
 */
public class TruncatedNormalSampler {

    private final NormalDistribution standardNormal;

    public TruncatedNormalSampler() {
        // 使用标准正态分布（mean=0, sd=1）进行 CDF 与分位数（逆 CDF）计算
        this.standardNormal = new NormalDistribution(0.0, 1.0);
    }

    /**
     * 从下界为 0 的截断正态分布中采样。
     *
     * @param rng  共享的随机数生成器，确保可复现性
     * @param mean 正需求均值 μ
     * @param sd   正需求标准差 σ
     * @return 采样值，必大于或等于 0
     */
    public double sample(RandomGenerator rng, double mean, double sd) {
        // σ 为 0 或极小时（区间退化）做保护：直接返回 max(0, μ)
        if (sd <= 1e-9) {
            return Math.max(0.0, mean);
        }

        // 标准化下界 a = (0 - μ) / σ
        double a = -mean / sd;

        // 计算下界处的累积分布函数值 Φ(a)
        double phiA = standardNormal.cumulativeProbability(a);

        // 防止极端的 phiA 导致数值溢出或无效的累积概率
        if (phiA >= 0.9999999) {
            // 此时大部分分布都在 0 以下，退化返回 0
            return 0.0;
        }

        // 抽取均匀分布随机数 u ~ Uniform(0, 1)
        double u = rng.nextDouble();

        // 映射到截断区间概率 p ∈ [Φ(a), 1]
        double p = phiA + u * (1.0 - phiA);

        // 防御性数值修剪，避免 p 刚好为 1.0 时逆 CDF 返回无穷大
        if (p >= 1.0) {
            p = 0.9999999;
        }

        // 计算标准正态分布的逆累积概率 (分位数)
        double z = standardNormal.inverseCumulativeProbability(p);

        // 映射回原尺度：x = μ + σ * z
        double x = mean + sd * z;

        // 双重保险：确保绝不返回小于 0 的需求值
        return Math.max(0.0, x);
    }
}
