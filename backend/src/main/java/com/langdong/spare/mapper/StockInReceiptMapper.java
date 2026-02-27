package com.langdong.spare.mapper;

import com.langdong.spare.entity.StockInReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockInReceiptMapper {
    int insert(StockInReceipt receipt);

    StockInReceipt findByCode(@Param("receiptCode") String receiptCode);
}
