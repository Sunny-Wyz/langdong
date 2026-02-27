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
}
