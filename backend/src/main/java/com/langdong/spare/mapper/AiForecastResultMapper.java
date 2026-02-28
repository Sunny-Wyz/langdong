package com.langdong.spare.mapper;

import com.langdong.spare.entity.AiForecastResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 需求预测结果 Mapper
 */
@Mapper
public interface AiForecastResultMapper {

    /**
     * 批量插入预测结果
     */
    void insertBatch(@Param("list") List<AiForecastResult> list);

    /**
     * 按备件编码查询历史预测（按月升序）
     */
    List<AiForecastResult> findByPartCode(@Param("partCode") String partCode);

    /**
     * 分页查询最新/指定月份预测结果（含备件名称联查）
     *
     * @param month    目标月份（null 则取最新月）
     * @param partCode 备件编码模糊过滤（null 则不过滤）
     * @param offset   分页偏移
     * @param size     每页条数
     */
    List<AiForecastResult> findByPage(
            @Param("month") String month,
            @Param("partCode") String partCode,
            @Param("offset") int offset,
            @Param("size") int size);

    /**
     * 统计分页总数
     */
    long countByPage(
            @Param("month") String month,
            @Param("partCode") String partCode);

    /**
     * 查询最新预测月份（用于判断是否有预测数据）
     */
    String findLatestMonth();
}
