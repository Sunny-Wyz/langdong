package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI需求预测结果实体
 * 对应表：ai_forecast_result
 */
@Data
public class AiForecastResult {
    private Long id;

    /** 备件编码，关联 spare_part.code */
    private String partCode;

    /** 预测目标月份，格式 yyyy-MM */
    private String forecastMonth;

    /** 预测消耗量（件） */
    private BigDecimal predictQty;

    /** 90%置信区间下界 */
    private BigDecimal lowerBound;

    /** 90%置信区间上界 */
    private BigDecimal upperBound;

    /** 预测算法类型：RF（随机森林）/ SBA / FALLBACK（数据不足回退） */
    private String algoType;

    /** MASE 评估指标（平均绝对比例误差），null 表示数据不足无法评估 */
    private BigDecimal mase;

    /** 模型版本号 */
    private String modelVersion;

    /** 预测计算时间 */
    private LocalDateTime createTime;

    // ---- JOIN 展示字段 ----
    /** 备件名称（联查显示用） */
    private String partName;
}
