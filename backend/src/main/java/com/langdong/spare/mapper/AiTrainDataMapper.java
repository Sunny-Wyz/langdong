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
}
