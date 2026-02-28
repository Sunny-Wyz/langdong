package com.langdong.spare.dto;

import com.langdong.spare.entity.AiDeviceFeature;
import lombok.Data;
import java.util.List;

/**
 * 单条备件的预测上下文 DTO
 * 由 AiFeatureService 组装，传入各算法实现进行预测
 */
@Data
public class PredictContextDTO {

    /** 备件编码 */
    private String partCode;

    /** 备件名称 */
    private String partName;

    /** 采购提前期（天） */
    private Integer leadTime;

    /** ABC 分类（A/B/C，用于 SS 的 k 系数选取） */
    private String abcClass;

    /** 预测目标月份，格式 yyyy-MM */
    private String forecastMonth;

    /**
     * 近N个月消耗量时间序列（从最早到最近顺序，无消耗月份填0）
     * 一般取近12个月
     */
    private List<Integer> demandHistory;

    /**
     * 关联设备的近N月运行特征列表（可为 null，适用于无设备关联的备件）
     * 按月份顺序排列，顺序与 demandHistory 对应
     */
    private List<AiDeviceFeature> deviceFeatures;

    /**
     * 由 AiFeatureService 计算后填充：算法类型
     * RF（随机森林）/ SBA / FALLBACK
     */
    private String algoType;

    /**
     * 由 AiFeatureService 计算后填充：平均需求间隔 ADI
     */
    private Double adi;

    /**
     * 由 AiFeatureService 计算后填充：需求变异系数平方 CV²
     */
    private Double cv2;
}
