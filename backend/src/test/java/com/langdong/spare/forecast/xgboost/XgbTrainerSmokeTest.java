package com.langdong.spare.forecast.xgboost;

import com.langdong.spare.forecast.config.XGBoostProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XGBoost 四 Booster 封装冒烟测试（模块 C）。
 *
 * <p>用小规模合成数据验证训练/推理/保存/加载全链路，不依赖真实业务库。
 * 需运行环境已装 libomp（Mac）。</p>
 */
public class XgbTrainerSmokeTest {

    private final XgbTrainer trainer = new XgbTrainer(new XGBoostProperties());
    private static final String[] FEATS = {"x0", "x1"};

    /** 合成 n 条 2 特征样本：x0,x1 ∈ [0,1]。 */
    private float[][] genFeatures(int n, long seed) {
        Random rnd = new Random(seed);
        float[][] x = new float[n][2];
        for (int i = 0; i < n; i++) {
            x[i][0] = rnd.nextFloat();
            x[i][1] = rnd.nextFloat();
        }
        return x;
    }

    @Test
    @DisplayName("分类器：可分数据上发生概率区分正确（binary:logistic）")
    void testClassifier() {
        float[][] x = genFeatures(300, 1L);
        float[] label = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            label[i] = x[i][0] >= 0.5f ? 1f : 0f; // 由 x0 可分
        }
        XgbModel clf = trainer.trainClassifier(x, label, FEATS, "2026-06");
        assertEquals(XgbModelType.CLASSIFIER, clf.getType());

        float pHigh = clf.predictOne(new float[]{0.9f, 0.5f});
        float pLow = clf.predictOne(new float[]{0.1f, 0.5f});
        assertTrue(pHigh > 0.5f, "x0=0.9 应预测高发生概率，实际 " + pHigh);
        assertTrue(pLow < 0.5f, "x0=0.1 应预测低发生概率，实际 " + pLow);
        assertTrue(pHigh >= 0f && pHigh <= 1f, "概率须落在 [0,1]");
    }

    @Test
    @DisplayName("点回归器：拟合 y=3x0+2x1（reg:squarederror）")
    void testPointRegressor() {
        float[][] x = genFeatures(400, 2L);
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = 3 * x[i][0] + 2 * x[i][1];
        }
        XgbModel reg = trainer.trainPointRegressor(x, y, FEATS, "2026-06");
        float pred = reg.predictOne(new float[]{1.0f, 1.0f}); // 期望 ~5
        assertTrue(pred > 3.5f && pred < 6.0f, "预测应接近 5，实际 " + pred);
    }

    @Test
    @DisplayName("分位数回归器：0.05 下界 ≤ 0.95 上界（reg:quantileerror）")
    void testQuantileRegressors() {
        float[][] x = genFeatures(400, 3L);
        float[] y = new float[x.length];
        Random noise = new Random(99L);
        for (int i = 0; i < x.length; i++) {
            y[i] = 10 * x[i][0] + (float) (noise.nextGaussian() * 2.0); // 带噪声便于区间张开
        }
        XgbModel lower = trainer.trainQuantileRegressor(x, y, 0.05, FEATS, "2026-06");
        XgbModel upper = trainer.trainQuantileRegressor(x, y, 0.95, FEATS, "2026-06");
        assertEquals(0.05, lower.getQuantileAlpha(), 1e-9);
        assertEquals(0.95, upper.getQuantileAlpha(), 1e-9);

        float[][] probe = {{0.2f, 0.5f}, {0.5f, 0.5f}, {0.8f, 0.5f}};
        float[] lo = lower.predict(probe);
        float[] up = upper.predict(probe);
        for (int i = 0; i < probe.length; i++) {
            assertTrue(lo[i] <= up[i] + 1e-4f,
                    "下界应 ≤ 上界，probe[" + i + "] lo=" + lo[i] + " up=" + up[i]);
        }
    }

    @Test
    @DisplayName("保存/加载后推理结果完全一致")
    void testSaveLoad(@TempDir Path dir) {
        float[][] x = genFeatures(200, 4L);
        float[] y = new float[x.length];
        for (int i = 0; i < x.length; i++) {
            y[i] = 3 * x[i][0] + 2 * x[i][1];
        }
        XgbModel reg = trainer.trainPointRegressor(x, y, FEATS, "2026-06");
        float[][] probe = genFeatures(20, 777L);
        float[] before = reg.predict(probe);

        reg.save(dir, "point");
        XgbModel loaded = XgbModel.load(dir, "point");
        float[] after = loaded.predict(probe);

        assertArrayEquals(before, after, 1e-6f, "保存/加载后推理结果应完全一致");
        assertEquals("2026-06", loaded.getTrainCutoffMonth());
        assertArrayEquals(FEATS, loaded.getFeatureNames());
        assertEquals(XgbModelType.POINT_REGRESSOR, loaded.getType());
    }
}
