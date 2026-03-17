package com.langdong.spare.mapper;

import com.langdong.spare.entity.StockInItem;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface StockInItemMapper {
    int insert(StockInItem item);

    List<StockInItem> findPendingShelving();

    int updateShelvedQuantity(@Param("id") Long id, @Param("addedQuantity") Integer addedQuantity);

    /** 按入库日期升序（先进先出）查询指定备件的有剩余库存的批次 */
    List<StockInItem> findFifoBySparePartId(@Param("sparePartId") Long sparePartId);

    /** 扣减批次剩余数量 */
    int deductRemainingQty(@Param("id") Long id, @Param("qty") Integer qty);
}
