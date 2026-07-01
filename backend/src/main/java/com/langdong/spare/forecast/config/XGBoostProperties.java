package com.langdong.spare.forecast.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XGBoost4J 超参数配置（论文表 3-3）。
 *
 * <p>所有数值均按论文写死为默认值，可通过 application.yml 的 {@code forecast.xgboost.*} 覆盖。
 * 共 4 个 Booster：1 个分类器（阶段一）+ 1 个点回归器 + 2 个分位数回归器（阶段二）。
 * 分类器与回归器分别使用 {@link Booster} 两组超参；两个分位数回归器与点回归器共用回归超参，
 * 仅 objective 与 quantileAlpha 不同。</p>
 *
 * <p>随机种子固定为 42，保证训练可复现（论文硬约束）。</p>
 */
@Data
@ConfigurationProperties(prefix = "forecast.xgboost")
public class XGBoostProperties {

    /** XGBoost 训练随机种子（论文硬约束，固定 42，不得随意更改）。 */
    private int seed = 42;

    /** 阶段一：需求「是否发生」二分类器超参。 */
    private Booster classifier = Booster.classifierDefault();

    /** 阶段二：正需求量点回归器 / 分位数回归器超参。 */
    private Booster regressor = Booster.regressorDefault();

    /** 下界分位数 τ（90% 预测区间下界），默认 0.05。 */
    private double quantileLower = 0.05;

    /** 上界分位数 τ（90% 预测区间上界），默认 0.95。 */
    private double quantileUpper = 0.95;

    /**
     * 单个 Booster 的超参数集合（表 3-3 列）。
     */
    @Data
    public static class Booster {
        /** 迭代轮数 num_round / n_estimators。 */
        private int numRound;
        /** 树最大深度。 */
        private int maxDepth;
        /** 学习率 eta / learning_rate。 */
        private double eta;
        /** 叶子最小样本权重和。 */
        private double minChildWeight;
        /** 行采样比例。 */
        private double subsample;
        /** 列采样比例。 */
        private double colsampleBytree;
        /** L1 正则。 */
        private double regAlpha;
        /** L2 正则。 */
        private double regLambda;

        /** 阶段一分类器默认超参（表 3-3 左列）。 */
        static Booster classifierDefault() {
            Booster b = new Booster();
            b.numRound = 100;
            b.maxDepth = 4;
            b.eta = 0.1;
            b.minChildWeight = 3;
            b.subsample = 0.8;
            b.colsampleBytree = 0.8;
            b.regAlpha = 0;
            b.regLambda = 1.0;
            return b;
        }

        /** 阶段二回归 / 分位数回归器默认超参（表 3-3 右列）。 */
        static Booster regressorDefault() {
            Booster b = new Booster();
            b.numRound = 150;
            b.maxDepth = 5;
            b.eta = 0.08;
            b.minChildWeight = 2;
            b.subsample = 0.8;
            b.colsampleBytree = 0.8;
            b.regAlpha = 0.01;
            b.regLambda = 1.0;
            return b;
        }
    }
}
