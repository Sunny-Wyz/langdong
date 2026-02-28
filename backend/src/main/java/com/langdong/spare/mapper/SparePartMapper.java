package com.langdong.spare.mapper;

import com.langdong.spare.dto.MonthlyConsumptionVO;
import com.langdong.spare.entity.SparePart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SparePartMapper {
    List<SparePart> findAll();

    SparePart findById(Long id);

    String findMaxCodeByPrefix(@org.apache.ibatis.annotations.Param("prefix") String prefix);

    int insert(SparePart sparePart);

    int update(SparePart sparePart);

    int deleteById(Long id);

    List<SparePart> findByEquipmentId(Long equipmentId);

    /**
     * 查询所有备件档案（含分类计算所需字段：is_critical、replace_diff、lead_time、price）
     * 用于分类模块全量重算
     */
    List<SparePart> findAllForClassify();

    /**
     * 查询所有备件近12个月的月度消耗汇总
     * 数据来源：biz_requisition_item（出库数量）+ biz_requisition（状态和时间）
     *
     * @param fromMonth 起始月份（含），格式 yyyy-MM-dd，通常为当前日期减12个月
     */
    List<MonthlyConsumptionVO> findAllMonthlyConsumption(@Param("fromMonth") String fromMonth);
}
