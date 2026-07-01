package com.langdong.spare.forecast.model;

import lombok.Data;

/**
 * 蒙特卡洛安全库存计算输出（算法 3-2）。
 *
 * <p>由 {@code LeadTimeDemandSimulator} 基于提前期累计需求分布的经验分位数得到。</p>
 */
@Data
public class SafetyStockResult {

    /** 补货点 ROP = ceil(Quantile(samples, α))。 */
    private int reorderPoint;
    /** 安全库存 SS = ROP − ceil(Mean(samples))。 */
    private int safetyStock;
    /** 采用的服务水平 α（分位数）。 */
    private double serviceLevel;
    /** 提前期累计需求样本均值（诊断/评估用）。 */
    private double sampleMean;
    /** 服务水平分位数对应的提前期累计需求（ceil 前的原始分位数值，诊断用）。 */
    private double leadTimeDemandQuantile;

    public SafetyStockResult() {
    }

    public SafetyStockResult(int reorderPoint, int safetyStock, double serviceLevel,
                             double sampleMean, double leadTimeDemandQuantile) {
        this.reorderPoint = reorderPoint;
        this.safetyStock = safetyStock;
        this.serviceLevel = serviceLevel;
        this.sampleMean = sampleMean;
        this.leadTimeDemandQuantile = leadTimeDemandQuantile;
    }
}
