package com.langdong.spare.forecast.stage;

import com.langdong.spare.forecast.calibration.ProbabilityCalibrator;
import com.langdong.spare.forecast.xgboost.XgbModel;

/**
 * 阶段一模型：需求「是否发生」分类器 + 概率校准器。
 */
public class StageOneModel {

    private final XgbModel classifier;
    private final ProbabilityCalibrator calibrator;

    public StageOneModel(XgbModel classifier, ProbabilityCalibrator calibrator) {
        this.classifier = classifier;
        this.calibrator = calibrator;
    }

    /** 预测经校准的发生概率 p_t ∈ [0,1]。 */
    public double predictOccurrenceProb(float[] stage1Features) {
        double raw = classifier.predictOne(stage1Features);
        return calibrator.calibrate(raw);
    }

    public XgbModel getClassifier() {
        return classifier;
    }

    public ProbabilityCalibrator getCalibrator() {
        return calibrator;
    }
}
