package com.langdong.spare.forecast.calibration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 保序回归（PAV）单元测试。
 */
public class IsotonicRegressionTest {

    @Test
    @DisplayName("PAV 池化：y=[1,0,1] → 拟合 [0.5,0.5,1]")
    void testPavPooling() {
        IsotonicRegression iso = new IsotonicRegression();
        iso.fit(new double[]{1, 2, 3}, new double[]{1, 0, 1});
        double[] ky = iso.getKnotsY();
        assertEquals(0.5, ky[0], 1e-9);
        assertEquals(0.5, ky[1], 1e-9);
        assertEquals(1.0, ky[2], 1e-9);
    }

    @Test
    @DisplayName("拟合结果单调非降，预测被裁剪到 [0,1]")
    void testMonotonicAndClamp() {
        IsotonicRegression iso = new IsotonicRegression();
        // 带噪声但整体递增
        double[] x = {0.0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
        double[] y = {0, 0, 1, 0, 1, 1, 0, 1, 1, 1, 1};
        iso.fit(x, y);

        double prev = -1;
        for (double q = 0.0; q <= 1.0; q += 0.05) {
            double p = iso.predict(q);
            assertTrue(p >= 0 && p <= 1, "预测须在 [0,1]");
            assertTrue(p >= prev - 1e-9, "预测须单调非降");
            prev = p;
        }
    }

    @Test
    @DisplayName("范围外裁剪到端点")
    void testOutOfRange() {
        IsotonicRegression iso = new IsotonicRegression();
        iso.fit(new double[]{0.2, 0.5, 0.8}, new double[]{0, 1, 1});
        assertEquals(iso.predict(0.2), iso.predict(-1.0), 1e-9);
        assertEquals(iso.predict(0.8), iso.predict(2.0), 1e-9);
    }
}
