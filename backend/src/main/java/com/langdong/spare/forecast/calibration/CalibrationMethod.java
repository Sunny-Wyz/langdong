package com.langdong.spare.forecast.calibration;

/**
 * 概率校准方法（模块 E）。
 */
public enum CalibrationMethod {
    /** 保序回归（默认）。 */
    ISOTONIC,
    /** Platt scaling（正样本极少时回退）。 */
    PLATT,
    /** 不校准（原样返回，样本极端不足时兜底）。 */
    IDENTITY
}
