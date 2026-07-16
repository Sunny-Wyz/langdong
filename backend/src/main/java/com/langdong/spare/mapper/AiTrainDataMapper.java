package com.langdong.spare.mapper;

import com.langdong.spare.entity.AiTrainDataRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiTrainDataMapper {

    List<AiTrainDataRecord> findByPage(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("partCode") String partCode,
            @Param("sourceLevel") String sourceLevel,
            @Param("isImputed") Integer isImputed,
            @Param("orderBy") String orderBy,
            @Param("offset") int offset,
            @Param("size") int size
    );

    long countByPage(
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("partCode") String partCode,
            @Param("sourceLevel") String sourceLevel,
            @Param("isImputed") Integer isImputed
    );

    /** 看板元信息：行数、日期范围、最近更新时间 */
    java.util.Map<String, Object> selectMeta();

    int deleteOutsideWindow(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
