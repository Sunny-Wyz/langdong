package com.langdong.spare.forecast.calibration;

import com.langdong.spare.forecast.xgboost.ForecastModelException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 第一阶段发生概率校准器（模块 E）。
 *
 * <p>默认用保序回归（Isotonic Regression）拟合校准映射；当正样本极少（保序不稳定）时回退到
 * Platt scaling；样本极端不足（缺少某一类）时兜底为 IDENTITY（不校准）。</p>
 *
 * <p>记录校准前后 Brier Score 便于核对（论文报告两阶段平均 Brier Score ≈ 0.15）。校准器可随
 * 模型版本一起保存/加载。</p>
 */
public class ProbabilityCalibrator {

    /** 正样本数达到该阈值才用保序回归，否则回退 Platt。 */
    private static final int MIN_POSITIVES_FOR_ISOTONIC = 10;

    private CalibrationMethod method = CalibrationMethod.IDENTITY;
    private final IsotonicRegression isotonic = new IsotonicRegression();
    private final PlattScaling platt = new PlattScaling();

    private double brierBefore = Double.NaN;
    private double brierAfter = Double.NaN;

    public CalibrationMethod getMethod() {
        return method;
    }

    public double getBrierBefore() {
        return brierBefore;
    }

    public double getBrierAfter() {
        return brierAfter;
    }

    /**
     * 在验证折上拟合校准映射。
     *
     * @param rawProbs 分类器原始发生概率
     * @param labels   真实标签（0/1）
     */
    public void fit(double[] rawProbs, int[] labels) {
        if (rawProbs == null || labels == null || rawProbs.length != labels.length || rawProbs.length == 0) {
            method = CalibrationMethod.IDENTITY;
            return;
        }
        int positives = 0;
        for (int label : labels) {
            if (label > 0) {
                positives++;
            }
        }
        int negatives = labels.length - positives;

        brierBefore = brierScore(rawProbs, labels);

        if (positives == 0 || negatives == 0) {
            // 只有单一类别，无法学习有意义的校准 → 不校准
            method = CalibrationMethod.IDENTITY;
        } else if (positives >= MIN_POSITIVES_FOR_ISOTONIC) {
            double[] y = new double[labels.length];
            for (int i = 0; i < labels.length; i++) {
                y[i] = labels[i];
            }
            isotonic.fit(rawProbs, y);
            method = CalibrationMethod.ISOTONIC;
        } else {
            platt.fit(rawProbs, labels);
            method = CalibrationMethod.PLATT;
        }

        double[] calibrated = new double[rawProbs.length];
        for (int i = 0; i < rawProbs.length; i++) {
            calibrated[i] = calibrate(rawProbs[i]);
        }
        brierAfter = brierScore(calibrated, labels);
    }

    /**
     * 对单个原始概率做校准。
     */
    public double calibrate(double rawProb) {
        switch (method) {
            case ISOTONIC:
                return isotonic.predict(rawProb);
            case PLATT:
                return platt.predict(rawProb);
            case IDENTITY:
            default:
                return clamp01(rawProb);
        }
    }

    /**
     * Brier Score = 均值((p − y)²)，越小越好。
     */
    public static double brierScore(double[] probs, int[] labels) {
        if (probs == null || labels == null || probs.length != labels.length || probs.length == 0) {
            return Double.NaN;
        }
        double sum = 0;
        for (int i = 0; i < probs.length; i++) {
            double d = probs[i] - labels[i];
            sum += d * d;
        }
        return sum / probs.length;
    }

    // ================================================================
    // 保存 / 加载
    // ================================================================

    public void save(Path dir, String name) {
        try {
            Files.createDirectories(dir);
            Properties p = new Properties();
            p.setProperty("method", method.name());
            p.setProperty("brierBefore", Double.toString(brierBefore));
            p.setProperty("brierAfter", Double.toString(brierAfter));
            if (method == CalibrationMethod.ISOTONIC) {
                p.setProperty("knotsX", joinDoubles(isotonic.getKnotsX()));
                p.setProperty("knotsY", joinDoubles(isotonic.getKnotsY()));
            } else if (method == CalibrationMethod.PLATT) {
                p.setProperty("plattA", Double.toString(platt.getA()));
                p.setProperty("plattB", Double.toString(platt.getB()));
            }
            try (OutputStream os = Files.newOutputStream(dir.resolve(name + ".calib"))) {
                p.store(os, "Probability calibrator");
            }
        } catch (IOException e) {
            throw new ForecastModelException("保存校准器失败: " + name, e);
        }
    }

    public static ProbabilityCalibrator load(Path dir, String name) {
        try {
            Properties p = new Properties();
            try (InputStream is = Files.newInputStream(dir.resolve(name + ".calib"))) {
                p.load(is);
            }
            ProbabilityCalibrator c = new ProbabilityCalibrator();
            c.method = CalibrationMethod.valueOf(p.getProperty("method", "IDENTITY"));
            c.brierBefore = Double.parseDouble(p.getProperty("brierBefore", "NaN"));
            c.brierAfter = Double.parseDouble(p.getProperty("brierAfter", "NaN"));
            if (c.method == CalibrationMethod.ISOTONIC) {
                c.isotonic.setKnots(parseDoubles(p.getProperty("knotsX", "")),
                        parseDoubles(p.getProperty("knotsY", "")));
            } else if (c.method == CalibrationMethod.PLATT) {
                c.platt.setParams(Double.parseDouble(p.getProperty("plattA", "-1")),
                        Double.parseDouble(p.getProperty("plattB", "0")));
            }
            return c;
        } catch (IOException e) {
            throw new ForecastModelException("加载校准器失败: " + name, e);
        }
    }

    private static String joinDoubles(double[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private static double[] parseDoubles(String s) {
        if (s == null || s.isEmpty()) {
            return new double[0];
        }
        String[] parts = s.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i]);
        }
        return out;
    }

    private static double clamp01(double v) {
        if (v < 0) {
            return 0;
        }
        return Math.min(v, 1);
    }
}
