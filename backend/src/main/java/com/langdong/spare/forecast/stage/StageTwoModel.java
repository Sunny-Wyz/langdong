package com.langdong.spare.forecast.stage;

import com.langdong.spare.forecast.xgboost.XgbModel;

/**
 * 阶段二模型：正需求量点回归器 + 下界(τ=0.05)/上界(τ=0.95)分位数回归器。
 */
public class StageTwoModel {

    private final XgbModel pointRegressor;
    private final XgbModel lowerQuantile;
    private final XgbModel upperQuantile;

    public StageTwoModel(XgbModel pointRegressor, XgbModel lowerQuantile, XgbModel upperQuantile) {
        this.pointRegressor = pointRegressor;
        this.lowerQuantile = lowerQuantile;
        this.upperQuantile = upperQuantile;
    }

    /** 正需求量点估计 ŷ（裁剪 ≥ 0）。 */
    public double predictPositiveQty(float[] stage2Features) {
        return Math.max(0.0, pointRegressor.predictOne(stage2Features));
    }

    /** 区间下界 L（裁剪 ≥ 0）。 */
    public double predictLower(float[] stage2Features) {
        return Math.max(0.0, lowerQuantile.predictOne(stage2Features));
    }

    /** 区间上界 U（裁剪 ≥ 0）。 */
    public double predictUpper(float[] stage2Features) {
        return Math.max(0.0, upperQuantile.predictOne(stage2Features));
    }

    public XgbModel getPointRegressor() {
        return pointRegressor;
    }

    public XgbModel getLowerQuantile() {
        return lowerQuantile;
    }

    public XgbModel getUpperQuantile() {
        return upperQuantile;
    }
}
