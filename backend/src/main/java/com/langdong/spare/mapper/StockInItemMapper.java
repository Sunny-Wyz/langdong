package com.langdong.spare.mapper;

import com.langdong.spare.entity.StockInItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockInItemMapper {
    int insert(StockInItem item);
}
