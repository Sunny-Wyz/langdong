package com.langdong.spare.forecast.stage;

import com.langdong.spare.forecast.config.XGBoostProperties;
import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.TrainingSample;
import com.langdong.spare.forecast.xgboost.XgbModel;
import com.langdong.spare.forecast.xgboost.XgbTrainer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段二：正需求量回归（算法 3-1 步骤 3~4）。
 *
 * <p>筛选正需求子集 S_pos = { t | D_t &gt; 0 }，在其上用 11 维特征训练点回归器 + 两个分位数
 * 回归器（τ=0.05 下界 / τ=0.95 上界）。</p>
 */
@Component
public class DemandQuantityStage {

    private final XgbTrainer trainer;
    private final XGBoostProperties props;

    public DemandQuantityStage(XgbTrainer trainer, XGBoostProperties props) {
        this.trainer = trainer;
        this.props = props;
    }

    /**
     * 训练阶段二模型（输入需已是正需求子集）。
     *
     * @param positiveSamples 正需求样本（demand &gt; 0）
     * @param cutoffMonth     训练截止月
     */
    public StageTwoModel train(List<TrainingSample> positiveSamples, String cutoffMonth) {
        if (positiveSamples == null || positiveSamples.isEmpty()) {
            throw new IllegalArgumentException("阶段二正需求样本为空");
        }
        List<TrainingSample> pos = new ArrayList<>();
        for (TrainingSample s : positiveSamples) {
            if (s.isPositive()) {
                pos.add(s);
            }
        }
        if (pos.isEmpty()) {
            throw new IllegalArgumentException("阶段二未筛出任何正需求样本");
        }

        float[][] x = new float[pos.size()][];
        float[] y = new float[pos.size()];
        for (int i = 0; i < pos.size(); i++) {
            x[i] = pos.get(i).getFeatures().toStage2Array();
            y[i] = (float) pos.get(i).getDemand();
        }

        XgbModel point = trainer.trainPointRegressor(x, y, FeatureVector.STAGE2_FEATURES, cutoffMonth);
        XgbModel lower = trainer.trainQuantileRegressor(x, y, props.getQuantileLower(),
                FeatureVector.STAGE2_FEATURES, cutoffMonth);
        XgbModel upper = trainer.trainQuantileRegressor(x, y, props.getQuantileUpper(),
                FeatureVector.STAGE2_FEATURES, cutoffMonth);
        return new StageTwoModel(point, lower, upper);
    }
}
