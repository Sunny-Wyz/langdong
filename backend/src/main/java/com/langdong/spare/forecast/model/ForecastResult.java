package com.langdong.spare.forecast.model;

import lombok.Data;

/**
 * 两阶段 Hurdle-Gamma 概率预测的算法输出（内存 DTO，落库时映射为 {@code AiForecastResult} 实体）。
 *
 * <p>包含论文算法 3-1 的六个标准字段，以及后续由蒙特卡洛（算法 3-2）填充的 ROP/SS/服务水平。</p>
 */
@Data
public class ForecastResult {

    private String partCode;
    /** 预测目标月份（yyyy-MM）。 */
    private String targetMonth;

    // ---- 两阶段 Hurdle-Gamma 六标准字段 ----
    /** 需求发生概率 p_t（经概率校准后，∈[0,1]）。 */
    private double occurrenceProb;
    /** 正需求量点估计 ŷ（阶段二点回归器输出）。 */
    private double positiveQty;
    /** 90% 预测区间下界 L（τ=0.05 分位数回归器输出）。 */
    private double lowerBound;
    /** 90% 预测区间上界 U（τ=0.95 分位数回归器输出）。 */
    private double upperBound;
    /** 总需求点估计 D_hat = p_t × ŷ。 */
    private double demandHat;

    // ---- 蒙特卡洛安全库存字段（后续填充）----
    /** 补货点 ROP（件）。 */
    private Integer reorderPoint;
    /** 安全库存 SS（件）。 */
    private Integer safetyStock;
    /** 目标服务水平 α（按 ABC 取，如 0.99/0.95/0.90）。 */
    private Double serviceLevel;

    // ---- 元信息 ----
    /** 算法类型，统一为 TWO_STAGE（前端展示为「两阶段 Hurdle-Gamma」）。 */
    private String algoType = "TWO_STAGE";
    /** 模型版本号。 */
    private String modelVersion;

    /** 数据不足标记：为 true 时本条为跳过占位，需前端标注「数据不足」（TC-FC-04）。 */
    private boolean dataInsufficient;
    /** 数据不足或跳过原因。 */
    private String note;

    /**
     * 构造一条「数据不足」占位结果（新备件等无历史场景），不抛异常。
     */
    public static ForecastResult insufficient(String partCode, String targetMonth, String reason) {
        ForecastResult r = new ForecastResult();
        r.partCode = partCode;
        r.targetMonth = targetMonth;
        r.dataInsufficient = true;
        r.note = reason;
        return r;
    }
}
