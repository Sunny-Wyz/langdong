package com.langdong.spare.forecast.xgboost;

import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.XGBoost;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/**
 * 训练好的单个 XGBoost Booster 及其快照元信息。
 *
 * <p>快照包含：原生模型文件（{@code *.xgb}）+ 元信息（{@code *.meta}：Booster 角色、特征列顺序、
 * 训练截止月、分位数 α），用于推理与回滚复用（论文 4.3.6 / 4.4.3）。</p>
 *
 * <p>推理时传入的特征列顺序必须与 {@link #featureNames} 完全一致，否则结果无意义。</p>
 */
public class XgbModel {

    private final Booster booster;
    private final XgbModelType type;
    /** 特征列顺序（与训练矩阵一致）。 */
    private final String[] featureNames;
    /** 训练数据截止月份（yyyy-MM），标记该快照可用于哪个时点之后的推理。 */
    private final String trainCutoffMonth;
    /** 分位数 α（仅 QUANTILE_REGRESSOR 有意义，其余为 NaN）。 */
    private final double quantileAlpha;

    public XgbModel(Booster booster, XgbModelType type, String[] featureNames,
                    String trainCutoffMonth, double quantileAlpha) {
        this.booster = booster;
        this.type = type;
        this.featureNames = featureNames == null ? new String[0] : featureNames.clone();
        this.trainCutoffMonth = trainCutoffMonth;
        this.quantileAlpha = quantileAlpha;
    }

    public XgbModelType getType() {
        return type;
    }

    public String[] getFeatureNames() {
        return featureNames.clone();
    }

    public String getTrainCutoffMonth() {
        return trainCutoffMonth;
    }

    public double getQuantileAlpha() {
        return quantileAlpha;
    }

    // ================================================================
    // 推理
    // ================================================================

    /**
     * 批量预测。
     *
     * @param features 特征矩阵，列顺序须与 {@link #featureNames} 一致
     * @return 每行一个输出（分类器为发生概率，回归器为量值）
     */
    public float[] predict(float[][] features) {
        if (features == null || features.length == 0) {
            return new float[0];
        }
        int ncol = features[0].length;
        float[] flat = flatten(features, ncol);
        try {
            DMatrix dm = new DMatrix(flat, features.length, ncol, Float.NaN);
            float[][] out = booster.predict(dm);
            float[] result = new float[out.length];
            for (int i = 0; i < out.length; i++) {
                result[i] = out[i][0];
            }
            return result;
        } catch (XGBoostError e) {
            throw new ForecastModelException("XGBoost 推理失败: type=" + type, e);
        }
    }

    /**
     * 单样本预测。
     */
    public float predictOne(float[] feature) {
        float[] r = predict(new float[][]{feature});
        return r.length > 0 ? r[0] : 0f;
    }

    // ================================================================
    // 快照保存 / 加载
    // ================================================================

    /**
     * 将模型 + 元信息保存到 {@code dir} 下，文件名前缀为 {@code name}。
     *
     * @return 原生模型文件路径
     */
    public Path save(Path dir, String name) {
        try {
            Files.createDirectories(dir);
            Path modelPath = dir.resolve(name + ".xgb");
            try (OutputStream os = Files.newOutputStream(modelPath)) {
                booster.saveModel(os);
            }
            Properties meta = new Properties();
            meta.setProperty("type", type.name());
            meta.setProperty("features", String.join(",", featureNames));
            meta.setProperty("trainCutoffMonth", trainCutoffMonth == null ? "" : trainCutoffMonth);
            meta.setProperty("quantileAlpha", Double.toString(quantileAlpha));
            try (OutputStream os = Files.newOutputStream(dir.resolve(name + ".meta"))) {
                meta.store(os, "XGBoost model snapshot metadata");
            }
            return modelPath;
        } catch (IOException | XGBoostError e) {
            throw new ForecastModelException("保存模型快照失败: " + name, e);
        }
    }

    /**
     * 从快照加载模型 + 元信息。
     */
    public static XgbModel load(Path dir, String name) {
        try {
            Properties meta = new Properties();
            try (InputStream is = Files.newInputStream(dir.resolve(name + ".meta"))) {
                meta.load(is);
            }
            XgbModelType type = XgbModelType.valueOf(meta.getProperty("type"));
            String featStr = meta.getProperty("features", "");
            String[] features = featStr.isEmpty() ? new String[0] : featStr.split(",");
            String cutoff = meta.getProperty("trainCutoffMonth", "");
            double alpha = Double.parseDouble(meta.getProperty("quantileAlpha", "NaN"));

            Booster booster;
            try (InputStream is = Files.newInputStream(dir.resolve(name + ".xgb"))) {
                booster = XGBoost.loadModel(is);
            }
            return new XgbModel(booster, type, features, cutoff.isEmpty() ? null : cutoff, alpha);
        } catch (IOException | XGBoostError e) {
            throw new ForecastModelException("加载模型快照失败: " + name, e);
        }
    }

    private static float[] flatten(float[][] features, int ncol) {
        float[] flat = new float[features.length * ncol];
        for (int i = 0; i < features.length; i++) {
            if (features[i].length != ncol) {
                throw new ForecastModelException("特征矩阵列数不一致，期望 " + ncol
                        + " 实际 " + features[i].length + "（行 " + i + "）");
            }
            System.arraycopy(features[i], 0, flat, i * ncol, ncol);
        }
        return flat;
    }

    @Override
    public String toString() {
        return "XgbModel{type=" + type + ", features=" + Arrays.toString(featureNames)
                + ", cutoff=" + trainCutoffMonth + ", alpha=" + quantileAlpha + "}";
    }
}
