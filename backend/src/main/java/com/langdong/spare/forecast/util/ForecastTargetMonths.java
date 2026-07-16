package com.langdong.spare.forecast.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * 月度需求预测目标属期统一口径。
 *
 * <p>业务约定：预测「下个月」的需求（滚动采购/备库），定时、手动重算、任务中心共用。</p>
 */
public final class ForecastTargetMonths {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private ForecastTargetMonths() {
    }

    /** 默认目标月：当前自然月的下一个月，格式 yyyy-MM。 */
    public static String defaultTargetMonth() {
        return YearMonth.from(LocalDate.now()).plusMonths(1).toString();
    }

    /** 解析 yyyy-MM；非法则回退默认目标月。 */
    public static String normalizeOrDefault(String month) {
        if (month == null || month.isBlank()) {
            return defaultTargetMonth();
        }
        String trimmed = month.trim();
        try {
            return YearMonth.parse(trimmed, MONTH).toString();
        } catch (Exception ex) {
            return defaultTargetMonth();
        }
    }
}
