package com.langdong.spare.forecast.calibration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 概率校准器单元测试（Brier Score、方法选择、保存/加载）。
 */
public class ProbabilityCalibratorTest {

    @Test
    @DisplayName("Brier Score 定义正确")
    void testBrierScore() {
        // p=[0.0,1.0], y=[0,1] → 完美，Brier=0
        assertEquals(0.0, ProbabilityCalibrator.brierScore(new double[]{0, 1}, new int[]{0, 1}), 1e-9);
        // p=[0.5,0.5], y=[0,1] → (0.25+0.25)/2 = 0.25
        assertEquals(0.25, ProbabilityCalibrator.brierScore(new double[]{0.5, 0.5}, new int[]{0, 1}), 1e-9);
    }

    @Test
    @DisplayName("保序回归校准降低 Brier Score（含足够正样本）")
    void testIsotonicImprovesBrier() {
        int n = 600;
        Random rnd = new Random(42L);
        double[] raw = new double[n];
        int[] labels = new int[n];
        for (int i = 0; i < n; i++) {
            double trueP = (i + 0.5) / n;               // [0,1] 真实发生概率
            labels[i] = rnd.nextDouble() < trueP ? 1 : 0;
            raw[i] = trueP * trueP;                       // 人为失准（过度偏低）
        }
        ProbabilityCalibrator calib = new ProbabilityCalibrator();
        calib.fit(raw, labels);

        assertEquals(CalibrationMethod.ISOTONIC, calib.getMethod());
        assertTrue(calib.getBrierAfter() <= calib.getBrierBefore() + 1e-9,
                "校准后 Brier 应不高于校准前: before=" + calib.getBrierBefore()
                        + " after=" + calib.getBrierAfter());
    }

    @Test
    @DisplayName("正样本极少 → 回退 Platt；单一类别 → IDENTITY")
    void testMethodSelection() {
        // 少量正样本（<10），两类都有 → Platt
        double[] raw = new double[50];
        int[] labels = new int[50];
        Random rnd = new Random(7L);
        for (int i = 0; i < 50; i++) {
            raw[i] = rnd.nextDouble();
            labels[i] = i < 5 ? 1 : 0; // 仅 5 个正样本
        }
        ProbabilityCalibrator platt = new ProbabilityCalibrator();
        platt.fit(raw, labels);
        assertEquals(CalibrationMethod.PLATT, platt.getMethod());
        double p = platt.calibrate(0.5);
        assertTrue(p >= 0 && p <= 1);

        // 单一类别 → IDENTITY
        ProbabilityCalibrator identity = new ProbabilityCalibrator();
        identity.fit(new double[]{0.3, 0.4, 0.5}, new int[]{0, 0, 0});
        assertEquals(CalibrationMethod.IDENTITY, identity.getMethod());
        assertEquals(0.4, identity.calibrate(0.4), 1e-9);
    }

    @Test
    @DisplayName("保存/加载后校准输出一致")
    void testSaveLoad(@TempDir Path dir) {
        int n = 300;
        Random rnd = new Random(11L);
        double[] raw = new double[n];
        int[] labels = new int[n];
        for (int i = 0; i < n; i++) {
            double trueP = (i + 0.5) / n;
            labels[i] = rnd.nextDouble() < trueP ? 1 : 0;
            raw[i] = trueP;
        }
        ProbabilityCalibrator calib = new ProbabilityCalibrator();
        calib.fit(raw, labels);
        calib.save(dir, "calib");

        ProbabilityCalibrator loaded = ProbabilityCalibrator.load(dir, "calib");
        assertEquals(calib.getMethod(), loaded.getMethod());
        for (double q = 0.0; q <= 1.0; q += 0.1) {
            assertEquals(calib.calibrate(q), loaded.calibrate(q), 1e-9,
                    "保存/加载后校准输出应一致 @ " + q);
        }
    }
}
