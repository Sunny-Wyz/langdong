package com.langdong.spare.forecast.xgboost;

/**
 * 预测模型层统一运行时异常（包装 XGBoost4J 的受检异常 {@code XGBoostError} 及 IO 异常）。
 */
public class ForecastModelException extends RuntimeException {

    public ForecastModelException(String message, Throwable cause) {
        super(message, cause);
    }

    public ForecastModelException(String message) {
        super(message);
    }
}
