package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 周粒度 TFT/DeepAR 需求预测结果
 * 对应表：ai_weekly_forecast
 */
@Data
public class AiWeeklyForecast {
    private Long id;
    private String partCode;
    private LocalDate weekStart;
    private BigDecimal predictQty;
    private BigDecimal p10;
    private BigDecimal p25;
    private BigDecimal p75;
    private BigDecimal p90;
    private BigDecimal distMu;
    private BigDecimal distSigma;
    private String algoType;
    private String modelVersion;
    private BigDecimal adi;
    private BigDecimal cv2;
    private LocalDateTime createTime;

    // 联查字段
    private String partName;
}
