package com.langdong.spare.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预测结果查询响应 VO（含备件名称，用于前端表格展示）
 */
@Data
public class ForecastResultVO {
    private Long id;
    private String partCode;
    private String partName;
    private String forecastMonth;
    private BigDecimal predictQty;
    private BigDecimal lowerBound;
    private BigDecimal upperBound;
    private String algoType;
    private BigDecimal mase;
    private String modelVersion;
    private LocalDateTime createTime;
}
