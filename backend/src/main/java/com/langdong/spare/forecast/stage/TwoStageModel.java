package com.langdong.spare.forecast.stage;

import com.langdong.spare.forecast.calibration.ProbabilityCalibrator;
import com.langdong.spare.forecast.xgboost.XgbModel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 两阶段概率预测模型（阶段一 + 阶段二）的完整快照句柄。
 */
public class TwoStageModel {

    private final StageOneModel stageOne;
    private final StageTwoModel stageTwo;
    /** 训练数据截止月份（yyyy-MM）。 */
    private final String cutoffMonth;
    /** 模型版本号。 */
    private final String modelVersion;

    public TwoStageModel(StageOneModel stageOne, StageTwoModel stageTwo,
                         String cutoffMonth, String modelVersion) {
        this.stageOne = stageOne;
        this.stageTwo = stageTwo;
        this.cutoffMonth = cutoffMonth;
        this.modelVersion = modelVersion;
    }

    /**
     * 保存模型快照（包括 4 个 XGBoost Booster 和 1 个概率校准器）到指定文件夹下。
     *
     * @param dir 目标保存根目录
     */
    public void save(Path dir) {
        try {
            Files.createDirectories(dir);
            stageOne.getClassifier().save(dir, "classifier");
            stageOne.getCalibrator().save(dir, "calibrator");
            stageTwo.getPointRegressor().save(dir, "point_regressor");
            stageTwo.getLowerQuantile().save(dir, "lower_quantile");
            stageTwo.getUpperQuantile().save(dir, "upper_quantile");
        } catch (Exception e) {
            throw new RuntimeException("序列化保存两阶段模型失败: version=" + modelVersion, e);
        }
    }

    /**
     * 从指定文件夹反序列化加载两阶段模型。
     *
     * @param dir          快照源文件夹路径
     * @param cutoffMonth  数据截止月份
     * @param modelVersion 版本号
     * @return 加载好的完整两阶段模型句柄
     */
    public static TwoStageModel load(Path dir, String cutoffMonth, String modelVersion) {
        try {
            XgbModel classifier = XgbModel.load(dir, "classifier");
            ProbabilityCalibrator calibrator = ProbabilityCalibrator.load(dir, "calibrator");
            XgbModel pointRegressor = XgbModel.load(dir, "point_regressor");
            XgbModel lowerQuantile = XgbModel.load(dir, "lower_quantile");
            XgbModel upperQuantile = XgbModel.load(dir, "upper_quantile");

            StageOneModel s1 = new StageOneModel(classifier, calibrator);
            StageTwoModel s2 = new StageTwoModel(pointRegressor, lowerQuantile, upperQuantile);
            return new TwoStageModel(s1, s2, cutoffMonth, modelVersion);
        } catch (Exception e) {
            throw new RuntimeException("反序列化加载两阶段模型失败: version=" + modelVersion, e);
        }
    }

    public StageOneModel getStageOne() {
        return stageOne;
    }

    public StageTwoModel getStageTwo() {
        return stageTwo;
    }

    public String getCutoffMonth() {
        return cutoffMonth;
    }

    public String getModelVersion() {
        return modelVersion;
    }
}
