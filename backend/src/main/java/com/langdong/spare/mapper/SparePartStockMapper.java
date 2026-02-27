package com.langdong.spare.mapper;

import com.langdong.spare.entity.SparePartStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SparePartStockMapper {
    SparePartStock findBySparePartId(@Param("sparePartId") Long sparePartId);

    List<SparePartStock> findAll();

    int insert(SparePartStock stock);

    int addQuantity(@Param("sparePartId") Long sparePartId, @Param("addedQuantity") Integer addedQuantity);
}
