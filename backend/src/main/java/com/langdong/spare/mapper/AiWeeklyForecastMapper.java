package com.langdong.spare.mapper;

import com.langdong.spare.entity.AiWeeklyForecast;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiWeeklyForecastMapper {

    /** 按备件编码和周起始日期查询最新预测（分页） */
    List<AiWeeklyForecast> findPage(
            @Param("partCode") String partCode,
            @Param("weekStart") String weekStart,
            @Param("algoType") String algoType,
            @Param("offset") int offset,
            @Param("limit") int limit);

    int countPage(
            @Param("partCode") String partCode,
            @Param("weekStart") String weekStart,
            @Param("algoType") String algoType);

    /** 查询某备件未来 N 周预测 */
    List<AiWeeklyForecast> findByPartCode(
            @Param("partCode") String partCode,
            @Param("limit") int limit);

    /** 批量插入（Python 回调用） */
    int insertBatch(@Param("list") List<AiWeeklyForecast> list);

    /** 按备件+周起始唯一删除（幂等重算） */
    int deleteByPartAndWeek(
            @Param("partCode") String partCode,
            @Param("weekStart") String weekStart);
}
