package com.langdong.spare.mapper;

import com.langdong.spare.entity.SparePartClassification;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SparePartClassificationMapper {
    List<SparePartClassification> findAll();
    SparePartClassification findBySparePartId(Long sparePartId);
    void insert(SparePartClassification classification);
    void update(SparePartClassification classification);
    void deleteBySparePartId(Long sparePartId);
    void deleteAll();
}
