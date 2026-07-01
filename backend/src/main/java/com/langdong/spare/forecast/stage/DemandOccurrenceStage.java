package com.langdong.spare.forecast.stage;

import com.langdong.spare.forecast.calibration.ProbabilityCalibrator;
import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.TrainingSample;
import com.langdong.spare.forecast.xgboost.XgbModel;
import com.langdong.spare.forecast.xgboost.XgbTrainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 阶段一：需求发生分类（算法 3-1 步骤 1~2）。
 *
 * <p>由历史需求序列构造二分类标签 I_t = (D_t &gt; 0)，用全量历史 (X, I) 训练分类器；并在时序留出
 * 验证折上拟合概率校准器（模块 E）。</p>
 *
 * <p>校准纪律（防泄露）：按目标月时间排序后取「较早 ~70%」训练一个折内分类器，在「较晚 ~30%」上
 * 预测并拟合校准映射；最终分类器用全量历史重训（更充分），校准映射沿用折内结果。</p>
 */
@Component
public class DemandOccurrenceStage {

    private static final Logger log = LoggerFactory.getLogger(DemandOccurrenceStage.class);

    /** 少于该样本数不做校准折切分，直接不校准。 */
    private static final int MIN_SAMPLES_FOR_CALIBRATION = 30;
    /** 校准折占比（较晚部分）。 */
    private static final double CALIBRATION_FRACTION = 0.3;

    private final XgbTrainer trainer;

    public DemandOccurrenceStage(XgbTrainer trainer) {
        this.trainer = trainer;
    }

    /**
     * 训练阶段一模型。
     *
     * @param samples     全量训练样本（各备件各历史月）
     * @param cutoffMonth 训练截止月
     */
    public StageOneModel train(List<TrainingSample> samples, String cutoffMonth) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("阶段一训练样本为空");
        }

        // 按目标月时间排序，保证校准折切分符合时间顺序（防泄露）
        List<TrainingSample> ordered = new ArrayList<>(samples);
        ordered.sort(Comparator.comparing(s -> s.getFeatures().getTargetMonth()));

        ProbabilityCalibrator calibrator = new ProbabilityCalibrator();
        if (ordered.size() >= MIN_SAMPLES_FOR_CALIBRATION) {
            int calibSize = (int) Math.round(ordered.size() * CALIBRATION_FRACTION);
            int trainSize = ordered.size() - calibSize;
            if (trainSize > 0 && calibSize > 0) {
                List<TrainingSample> foldTrain = ordered.subList(0, trainSize);
                List<TrainingSample> foldCalib = ordered.subList(trainSize, ordered.size());
                if (bothClassesPresent(foldTrain)) {
                    XgbModel foldClf = trainClassifier(foldTrain, cutoffMonth);
                    double[] rawProbs = new double[foldCalib.size()];
                    int[] labels = new int[foldCalib.size()];
                    for (int i = 0; i < foldCalib.size(); i++) {
                        FeatureVector fv = foldCalib.get(i).getFeatures();
                        rawProbs[i] = foldClf.predictOne(fv.toStage1Array());
                        labels[i] = foldCalib.get(i).getOccurrenceLabel();
                    }
                    calibrator.fit(rawProbs, labels);
                    log.info("[阶段一] 校准方法={}, Brier: {} → {}",
                            calibrator.getMethod(), calibrator.getBrierBefore(), calibrator.getBrierAfter());
                }
            }
        }

        // 最终分类器：全量历史重训
        XgbModel classifier = trainClassifier(ordered, cutoffMonth);
        return new StageOneModel(classifier, calibrator);
    }

    private XgbModel trainClassifier(List<TrainingSample> samples, String cutoffMonth) {
        float[][] x = new float[samples.size()][];
        float[] label = new float[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            x[i] = samples.get(i).getFeatures().toStage1Array();
            label[i] = samples.get(i).getOccurrenceLabel();
        }
        return trainer.trainClassifier(x, label, FeatureVector.STAGE1_FEATURES, cutoffMonth);
    }

    private boolean bothClassesPresent(List<TrainingSample> samples) {
        boolean pos = false;
        boolean neg = false;
        for (TrainingSample s : samples) {
            if (s.getOccurrenceLabel() > 0) {
                pos = true;
            } else {
                neg = true;
            }
            if (pos && neg) {
                return true;
            }
        }
        return false;
    }
}
