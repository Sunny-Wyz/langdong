package com.langdong.spare.dto;

import lombok.Data;

/**
 * 备件月度消耗量视图对象
 * 用于分类模块从 biz_requisition_item 汇总月度消耗数据
 */
@Data
public class MonthlyConsumptionVO {

    /** 备件编码（对应 spare_part.code） */
    private String partCode;

    /** 消耗月份，格式 yyyy-MM */
    private String month;

    /** 当月消耗数量（出库数量之和） */
    private Integer qty;
}
