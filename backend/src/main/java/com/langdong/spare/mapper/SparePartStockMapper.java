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

    /**
     * 查询备件的可用库存总量（FIFO 使用）
     * @param sparePartId 备件ID
     * @return 可用库存数量
     */
    int getAvailableQuantity(@Param("sparePartId") Long sparePartId);
}
