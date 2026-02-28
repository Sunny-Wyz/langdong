package com.langdong.spare.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 备件ABC/XYZ分类结果实体
 * 对应数据库表：biz_part_classify
 * 每次重算插入新记录，不修改旧记录，以保留历史趋势数据
 */
@Data
public class PartClassify {

    /** 主键 */
    private Long id;

    /** 备件编码 */
    private String partCode;

    /** 分类所属月份，格式 yyyy-MM */
    private String classifyMonth;

    /** ABC分类结果：A / B / C */
    private String abcClass;

    /** XYZ分类结果：X / Y / Z */
    private String xyzClass;

    /** ABC综合加权得分，满分100分 */
    private BigDecimal compositeScore;

    /** 年消耗金额（元），= 年均消耗量 × 单价 */
    private BigDecimal annualCost;

    /** 平均需求间隔ADI（暂存，扩展用） */
    private BigDecimal adi;

    /** 需求变异系数CV²，= 标准差² / 均值² */
    private BigDecimal cv2;

    /** 安全库存SS（件） */
    private Integer safetyStock;

    /** 补货触发点ROP（件） */
    private Integer reorderPoint;

    /** 目标服务水平（%），由k值反推 */
    private BigDecimal serviceLevel;

    /** ABC×XYZ策略编码，如 AX、BZ */
    private String strategyCode;

    /** 记录创建时间 */
    private LocalDateTime createTime;

    // ---- 以下为联查字段，不存储在本表 ----

    /** 备件名称（联查 spare_part.name） */
    private String partName;
}
