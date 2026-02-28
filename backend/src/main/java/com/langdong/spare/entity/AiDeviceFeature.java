package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * AI设备特征记录实体
 * 对应表：ai_device_feature
 * 用于存储每台设备每月的运行特征，供随机森林模型用作预测输入特征
 */
@Data
public class AiDeviceFeature {
    private Long id;

    /** 设备ID，关联 equipment.id */
    private Long deviceId;

    /** 统计月份，格式 yyyy-MM */
    private String statMonth;

    /** 月运行时长（小时） */
    private BigDecimal runHours;

    /** 当月故障次数 */
    private Integer faultCount;

    /** 当月工单数 */
    private Integer workOrderCount;

    /** 当月换件总数量 */
    private Integer partReplaceQty;
}
