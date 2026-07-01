package com.langdong.spare.forecast.service;

import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.forecast.model.TrainingSample;
import com.langdong.spare.forecast.stage.DemandOccurrenceStage;
import com.langdong.spare.forecast.stage.DemandQuantityStage;
import com.langdong.spare.forecast.stage.StageOneModel;
import com.langdong.spare.forecast.stage.StageTwoModel;
import com.langdong.spare.forecast.stage.TwoStageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 两阶段模型编排服务（模块 D / 论文 4.3.6 orchestration 角色）。
 *
 * <p>统一编排 4 个 Booster 的训练与推理：训练时先训阶段一分类器+校准，再在正需求子集上训阶段二
 * 点/分位数回归器；推理时组合出六个标准字段并计算总需求点估计 {@code D_hat = p_t × ŷ}。</p>
 */
@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final DemandOccurrenceStage occurrenceStage;
    private final DemandQuantityStage quantityStage;

    public PredictionService(DemandOccurrenceStage occurrenceStage, DemandQuantityStage quantityStage) {
        this.occurrenceStage = occurrenceStage;
        this.quantityStage = quantityStage;
    }

    /**
     * 训练两阶段模型（全量历史）。
     *
     * @param samples      全量训练样本
     * @param cutoffMonth  训练截止月（yyyy-MM）
     * @param modelVersion 模型版本号
     */
    public TwoStageModel train(List<TrainingSample> samples, String cutoffMonth, String modelVersion) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("训练样本为空，无法训练两阶段模型");
        }
        StageOneModel s1 = occurrenceStage.train(samples, cutoffMonth);

        List<TrainingSample> positives = new ArrayList<>();
        for (TrainingSample s : samples) {
            if (s.isPositive()) {
                positives.add(s);
            }
        }
        if (positives.isEmpty()) {
            throw new IllegalArgumentException("无任何正需求样本，无法训练阶段二回归器");
        }
        StageTwoModel s2 = quantityStage.train(positives, cutoffMonth);

        log.info("[两阶段] 训练完成: 样本={}, 正需求={}, cutoff={}, version={}",
                samples.size(), positives.size(), cutoffMonth, modelVersion);
        return new TwoStageModel(s1, s2, cutoffMonth, modelVersion);
    }

    /**
     * 对单个目标月特征向量做推理，产出六标准字段（算法 3-1 步骤 5~10）。
     *
     * @param model 两阶段模型
     * @param x     目标月特征向量
     * @return 预测结果；特征数据不足时返回标记结果（不抛异常）
     */
    public ForecastResult forecast(TwoStageModel model, FeatureVector x) {
        if (x == null) {
            throw new IllegalArgumentException("特征向量为空");
        }
        if (x.isDataInsufficient()) {
            ForecastResult r = ForecastResult.insufficient(x.getPartCode(), x.getTargetMonth(),
                    x.getInsufficientReason());
            r.setModelVersion(model.getModelVersion());
            return r;
        }

        float[] s1Feat = x.toStage1Array();
        float[] s2Feat = x.toStage2Array();

        // 步骤6：发生概率（经校准）
        double p = clamp01(model.getStageOne().predictOccurrenceProb(s1Feat));
        // 步骤7：正需求量点估计
        double yHat = model.getStageTwo().predictPositiveQty(s2Feat);
        // 步骤8：分位数区间
        double lower = model.getStageTwo().predictLower(s2Feat);
        double upper = model.getStageTwo().predictUpper(s2Feat);
        if (lower > upper) {
            // 分位数交叉（小样本偶发）时交换，保证 L ≤ U
            double tmp = lower;
            lower = upper;
            upper = tmp;
        }
        // 步骤9：总需求点估计
        double dHat = p * yHat;

        ForecastResult r = new ForecastResult();
        r.setPartCode(x.getPartCode());
        r.setTargetMonth(x.getTargetMonth());
        r.setOccurrenceProb(p);
        r.setPositiveQty(yHat);
        r.setLowerBound(lower);
        r.setUpperBound(upper);
        r.setDemandHat(dHat);
        r.setModelVersion(model.getModelVersion());
        return r;
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        return Math.min(v, 1);
    }
}
