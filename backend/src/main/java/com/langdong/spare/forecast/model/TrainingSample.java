package com.langdong.spare.forecast.model;

import lombok.Data;

/**
 * 单条训练样本：一个历史月的特征向量 + 标签。
 *
 * <p>由 FeatureBuilder 为「预测时点之前的每个历史月」构造，用于组装训练矩阵。
 * 阶段一用 {@link #occurrenceLabel}（I_t = 1 if D_t>0 else 0）训练分类器；
 * 阶段二仅取 {@code demand > 0} 的正需求子集，用 {@link #demand} 训练点/分位数回归器。</p>
 */
@Data
public class TrainingSample {

    /** 该样本的特征向量（含阶段一 9 维与阶段二 2 维正需求滞后）。 */
    private FeatureVector features;

    /** 实际月度消耗量 D_t（阶段二回归标签）。 */
    private double demand;

    /** 需求发生标签 I_t = (demand > 0 ? 1 : 0)（阶段一分类标签）。 */
    private int occurrenceLabel;

    public TrainingSample() {
    }

    public TrainingSample(FeatureVector features, double demand) {
        this.features = features;
        this.demand = demand;
        this.occurrenceLabel = demand > 0 ? 1 : 0;
    }

    /** 是否为正需求样本（阶段二子集筛选用）。 */
    public boolean isPositive() {
        return demand > 0;
    }
}
