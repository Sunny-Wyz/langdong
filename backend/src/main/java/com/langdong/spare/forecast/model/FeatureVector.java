package com.langdong.spare.forecast.model;

import lombok.Data;

/**
 * 单个 (备件, 目标月) 的特征向量（论文表 3-2）。
 *
 * <p>阶段一分类器使用 9 维特征；阶段二回归器在正需求子集上额外使用 2 维（{@link #posLag1}、
 * {@link #posLag3Mean}），共 11 维。列顺序由 {@link #STAGE1_FEATURES} / {@link #STAGE2_FEATURES}
 * 严格固定，供 XGBoost DMatrix 与推理向量对齐，禁止随意调整。</p>
 *
 * <p><b>防泄露铁律：</b>所有字段只能由「目标月前一个月及更早」的数据计算得到。</p>
 */
@Data
public class FeatureVector {

    /** 阶段一特征列顺序（9 维），供 DMatrix 列对齐。 */
    public static final String[] STAGE1_FEATURES = {
            "lag_1", "lag_3_mean", "lag_3_std", "zero_ratio_6",
            "EquipHr", "RepairCnt", "Month", "ABC_code", "XYZ_code"
    };

    /** 阶段二特征列顺序（11 维 = 阶段一 9 维 + 2 维正需求滞后）。 */
    public static final String[] STAGE2_FEATURES = {
            "lag_1", "lag_3_mean", "lag_3_std", "zero_ratio_6",
            "EquipHr", "RepairCnt", "Month", "ABC_code", "XYZ_code",
            "pos_lag_1", "pos_lag_3_mean"
    };

    private String partCode;
    /** 该特征向量对应的目标月份（yyyy-MM），即被预测/被标注的那个月。 */
    private String targetMonth;

    // ---- 阶段一 9 维 ----
    /** #1 近 1 个月消耗量。 */
    private double lag1;
    /** #2 近 3 个月消耗均值。 */
    private double lag3Mean;
    /** #3 近 3 个月消耗样本标准差。 */
    private double lag3Std;
    /** #4 近 6 个月零需求月份占比。 */
    private double zeroRatio6;
    /** #5 上月关联设备运行小时数汇总。 */
    private double equipHr;
    /** #6 上月关联设备维修工单数。 */
    private double repairCnt;
    /** #7 月份季节性编码（1~12，整数编码，⚠️默认用整数）。 */
    private double month;
    /** #8 ABC 等级编码（A=3,B=2,C=1，取目标月前一月分类结果）。 */
    private double abcCode;
    /** #9 XYZ 等级编码（X=1,Y=2,Z=3）。 */
    private double xyzCode;

    // ---- 阶段二额外 2 维 ----
    /** #10 最近一次正需求的消耗量。 */
    private double posLag1;
    /** #11 最近三次正需求的消耗均值。 */
    private double posLag3Mean;

    /**
     * 数据不足标记：新备件或历史记录不够时置 true，预测任务应跳过并标注「数据不足」（TC-FC-04）。
     */
    private boolean dataInsufficient;

    /** 数据不足原因说明。 */
    private String insufficientReason;

    /**
     * 导出阶段一特征数组（顺序严格对应 {@link #STAGE1_FEATURES}）。
     */
    public float[] toStage1Array() {
        return new float[]{
                (float) lag1, (float) lag3Mean, (float) lag3Std, (float) zeroRatio6,
                (float) equipHr, (float) repairCnt, (float) month, (float) abcCode, (float) xyzCode
        };
    }

    /**
     * 导出阶段二特征数组（顺序严格对应 {@link #STAGE2_FEATURES}）。
     */
    public float[] toStage2Array() {
        return new float[]{
                (float) lag1, (float) lag3Mean, (float) lag3Std, (float) zeroRatio6,
                (float) equipHr, (float) repairCnt, (float) month, (float) abcCode, (float) xyzCode,
                (float) posLag1, (float) posLag3Mean
        };
    }
}
