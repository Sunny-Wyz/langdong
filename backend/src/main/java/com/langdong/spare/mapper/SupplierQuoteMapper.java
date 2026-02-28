package com.langdong.spare.mapper;

import com.langdong.spare.entity.SupplierQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface SupplierQuoteMapper {
    void insert(SupplierQuote quote);

    List<SupplierQuote> findByOrderNo(@Param("orderNo") String orderNo);

    void selectQuote(@Param("id") Long id); // 中标：is_selected = 1

    void clearSelections(@Param("orderNo") String orderNo); // 先清空，再选中
}
