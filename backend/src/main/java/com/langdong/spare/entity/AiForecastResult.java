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

    /** 需求发生概率 */
    private BigDecimal occurrenceProb;

    /** 正需求量预测均值 */
    private BigDecimal positiveQty;

    /** 提前期分位数 */
    private BigDecimal leadTimeQuantile;

    /** 预测计算时间 */
    private LocalDateTime createTime;

    // ---- JOIN 展示字段 ----
    /** 备件名称（联查显示用） */
    private String partName;

    /** 未来3个月累计需求（查询展示字段，非持久化） */
    private BigDecimal demand3Months;

    /** 安全库存SS（件，联查自分类结果） */
    private Integer safetyStock;

    /** 补货触发点ROP（件，联查自分类结果） */
    private Integer reorderPoint;

    /** 目标服务水平（%，联查自分类结果） */
    private BigDecimal serviceLevel;
}
