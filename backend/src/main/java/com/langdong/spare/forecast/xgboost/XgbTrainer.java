package com.langdong.spare.forecast.xgboost;

import com.langdong.spare.forecast.config.XGBoostProperties;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * XGBoost4J 训练器（模块 C）。
 *
 * <p>按论文表 3-3 超参训练 4 个 Booster：分类器（binary:logistic）、点回归器（reg:squarederror）、
 * 两个分位数回归器（reg:quantileerror + quantile_alpha=0.05/0.95）。所有超参与随机种子由
 * {@link XGBoostProperties} 注入，种子固定 42 保证可复现。</p>
 *
 * <p>M1 冒烟已验证 XGBoost 2.1.0 原生支持 {@code reg:quantileerror}，故分位数回归直接用原生
 * objective，无需自定义 pinball loss。</p>
 */
@Component
public class XgbTrainer {

    private final XGBoostProperties props;

    public XgbTrainer(XGBoostProperties props) {
        this.props = props;
    }

    /**
     * 训练阶段一分类器（objective=binary:logistic，输出发生概率）。
     *
     * @param features     特征矩阵（列顺序须与 featureNames 一致）
     * @param labels       二分类标签 I_t（0/1）
     * @param featureNames 特征列顺序
     * @param cutoffMonth  训练截止月（yyyy-MM）
     */
    public XgbModel trainClassifier(float[][] features, float[] labels,
                                    String[] featureNames, String cutoffMonth) {
        Map<String, Object> params = baseParams(props.getClassifier());
        params.put("objective", "binary:logistic");
        params.put("eval_metric", "logloss");
        Booster booster = train(features, labels, params, props.getClassifier().getNumRound());
        return new XgbModel(booster, XgbModelType.CLASSIFIER, featureNames, cutoffMonth, Double.NaN);
    }

    /**
     * 训练阶段二点回归器（objective=reg:squarederror）。
     */
    public XgbModel trainPointRegressor(float[][] features, float[] targets,
                                        String[] featureNames, String cutoffMonth) {
        Map<String, Object> params = baseParams(props.getRegressor());
        params.put("objective", "reg:squarederror");
        params.put("eval_metric", "rmse");
        Booster booster = train(features, targets, params, props.getRegressor().getNumRound());
        return new XgbModel(booster, XgbModelType.POINT_REGRESSOR, featureNames, cutoffMonth, Double.NaN);
    }

    /**
     * 训练阶段二分位数回归器（objective=reg:quantileerror + quantile_alpha）。
     *
     * @param alpha 分位数 τ（如 0.05 下界 / 0.95 上界）
     */
    public XgbModel trainQuantileRegressor(float[][] features, float[] targets, double alpha,
                                           String[] featureNames, String cutoffMonth) {
        Map<String, Object> params = baseParams(props.getRegressor());
        params.put("objective", "reg:quantileerror");
        params.put("quantile_alpha", alpha);
        Booster booster = train(features, targets, params, props.getRegressor().getNumRound());
        return new XgbModel(booster, XgbModelType.QUANTILE_REGRESSOR, featureNames, cutoffMonth, alpha);
    }

    // ================================================================
    // 内部
    // ================================================================

    /** 组装表 3-3 通用超参（objective 由调用方补充）。 */
    private Map<String, Object> baseParams(XGBoostProperties.Booster cfg) {
        Map<String, Object> params = new HashMap<>();
        params.put("max_depth", cfg.getMaxDepth());
        params.put("eta", cfg.getEta());
        params.put("min_child_weight", cfg.getMinChildWeight());
        params.put("subsample", cfg.getSubsample());
        params.put("colsample_bytree", cfg.getColsampleBytree());
        params.put("alpha", cfg.getRegAlpha());     // reg_alpha
        params.put("lambda", cfg.getRegLambda());   // reg_lambda
        params.put("seed", props.getSeed());        // 固定 42，可复现
        params.put("verbosity", 0);
        return params;
    }

    private Booster train(float[][] features, float[] targets, Map<String, Object> params, int numRound) {
        if (features == null || features.length == 0) {
            throw new ForecastModelException("训练特征矩阵为空");
        }
        if (targets == null || targets.length != features.length) {
            throw new ForecastModelException("标签数量与样本数不一致");
        }
        int ncol = features[0].length;
        float[] flat = new float[features.length * ncol];
        for (int i = 0; i < features.length; i++) {
            if (features[i].length != ncol) {
                throw new ForecastModelException("训练矩阵列数不一致（行 " + i + "）");
            }
            System.arraycopy(features[i], 0, flat, i * ncol, ncol);
        }
        try {
            DMatrix dtrain = new DMatrix(flat, features.length, ncol, Float.NaN);
            dtrain.setLabel(targets);
            Map<String, DMatrix> watches = new HashMap<>();
            return XGBoost.train(dtrain, params, numRound, watches, null, null);
        } catch (XGBoostError e) {
            throw new ForecastModelException("XGBoost 训练失败", e);
        }
    }
}
