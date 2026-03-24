package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 设备健康阈值配置实体
 * 对应数据库表：sys_device_health_config
 *
 * 该表存储不同设备类型和重要性级别的健康评分阈值和权重配置
 * 支持按设备类型和重要性进行差异化配置，NULL表示全局默认配置
 */
@Data
public class HealthConfig {

    /** 主键 */
    private Long id;

    /** 设备类型（NULL表示全局默认） */
    private String deviceType;

    /** 设备重要性：CRITICAL（关键）/ IMPORTANT（重要）/ NORMAL（一般），NULL表示适用所有 */
    private String importanceLevel;

    // ---- 风险等级阈值 ----

    /** CRITICAL风险阈值（健康分低于此值判定为严重风险） */
    private BigDecimal criticalThreshold;

    /** HIGH风险阈值（健康分低于此值判定为高风险） */
    private BigDecimal highThreshold;

    /** MEDIUM风险阈值（健康分低于此值判定为中风险） */
    private BigDecimal mediumThreshold;

    // ---- 评分维度权重 ----

    /** 运行时长维度权重（默认0.25） */
    private BigDecimal runtimeWeight;

    /** 故障频次维度权重（默认0.35） */
    private BigDecimal faultWeight;

    /** 工单数量维度权重（默认0.20） */
    private BigDecimal workorderWeight;

    /** 换件频次维度权重（默认0.20） */
    private BigDecimal replacementWeight;

    // ---- 预测参数 ----

    /** 预测窗口天数（默认90天） */
    private Integer predictionWindowDays;

    /** 最少历史数据月数（默认6个月） */
    private Integer minHistoryMonths;
}
