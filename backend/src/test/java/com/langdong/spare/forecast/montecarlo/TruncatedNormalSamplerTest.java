package com.langdong.spare.forecast.montecarlo;

import org.apache.commons.math3.random.JDKRandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TruncatedNormalSampler 截断正态分布采样器单元测试。
 */
public class TruncatedNormalSamplerTest {

    private final TruncatedNormalSampler sampler = new TruncatedNormalSampler();

    @Test
    @DisplayName("截断正态采样：基本属性（全员非负、经验均值符合理论）")
    void testTruncatedNormalProperties() {
        JDKRandomGenerator rng = new JDKRandomGenerator();
        rng.setSeed(42L);

        double mean = 5.0;
        double sd = 2.0;
        int count = 100000;
        double sum = 0.0;

        for (int i = 0; i < count; i++) {
            double val = sampler.sample(rng, mean, sd);
            assertTrue(val >= 0.0, "截断正态采样样本必须大于或等于 0");
            sum += val;
        }

        double expMean = sum / count;
        // 理论截断均值近似为 5.035
        // 我们断言在 100,000 次大样本下，经验均值与 5.035 偏差不超过 0.05
        assertEquals(5.035, expMean, 0.05, "大样本经验均值应与理论值非常接近");
    }

    @Test
    @DisplayName("退化保护测试：当标准差接近 0 时，返回 max(0, μ)")
    void testTruncatedNormalDegenerate() {
        JDKRandomGenerator rng = new JDKRandomGenerator();
        rng.setSeed(42L);

        // 正均值，退化返回 μ
        assertEquals(5.0, sampler.sample(rng, 5.0, 0.0), 1e-9);
        assertEquals(5.0, sampler.sample(rng, 5.0, 1e-12), 1e-9);

        // 负均值，退化截断返回 0.0
        assertEquals(0.0, sampler.sample(rng, -2.0, 0.0), 1e-9);
        assertEquals(0.0, sampler.sample(rng, -2.0, 1e-10), 1e-9);
    }

    @Test
    @DisplayName("极端条件保护：均值极小且标准差极小时触发保护")
    void testTruncatedNormalExtremePhiA() {
        JDKRandomGenerator rng = new JDKRandomGenerator();
        rng.setSeed(42L);

        // 均值极其偏负，Φ(a) 趋近于 1，应该触发 phiA >= 0.9999999 保护直接返回 0.0
        double val = sampler.sample(rng, -100.0, 1.0);
        assertEquals(0.0, val, 1e-9, "极端负值截断应安全返回 0.0");
    }
}
