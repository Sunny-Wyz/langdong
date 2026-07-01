package com.langdong.spare.forecast.xgboost;

/**
 * 两阶段模型中的 Booster 角色（共 4 个）。
 */
public enum XgbModelType {
    /** 阶段一：需求「是否发生」二分类器（binary:logistic，输出发生概率）。 */
    CLASSIFIER,
    /** 阶段二：正需求量点回归器（reg:squarederror）。 */
    POINT_REGRESSOR,
    /** 阶段二：分位数回归器（reg:quantileerror + quantile_alpha，构造区间上下界）。 */
    QUANTILE_REGRESSOR
}
