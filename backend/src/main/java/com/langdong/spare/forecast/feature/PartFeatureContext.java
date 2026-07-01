package com.langdong.spare.forecast.feature;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 单个备件的原始历史数据容器，作为 {@link FeatureBuilder} 的纯输入。
 *
 * <p>所有 map 的 key 均为月份字符串（yyyy-MM），value 为该月的聚合量。特征构造仅读取
 * 「严格早于目标月」的键，从数据结构上支撑防泄露测试。此对象不含任何 DB/Spring 依赖。</p>
 */
@Data
public class PartFeatureContext {

    private String partCode;

    /** 月度消耗量（yyyy-MM → 件数）。缺失月视为 0 需求。 */
    private Map<String, Double> monthlyDemand = new HashMap<>();

    /** 月度关联设备运行时长汇总（yyyy-MM → 小时）。 */
    private Map<String, Double> monthlyEquipHr = new HashMap<>();

    /** 月度关联设备维修工单数（yyyy-MM → 工单数）。 */
    private Map<String, Double> monthlyRepairCnt = new HashMap<>();

    public PartFeatureContext() {
    }

    public PartFeatureContext(String partCode) {
        this.partCode = partCode;
    }

    public double demandOf(String month) {
        return monthlyDemand.getOrDefault(month, 0.0);
    }

    public double equipHrOf(String month) {
        return monthlyEquipHr.getOrDefault(month, 0.0);
    }

    public double repairCntOf(String month) {
        return monthlyRepairCnt.getOrDefault(month, 0.0);
    }
}
