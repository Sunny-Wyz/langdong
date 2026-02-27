package com.langdong.spare.mapper;

import com.langdong.spare.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PurchaseOrderMapper {
    PurchaseOrder findByCode(@Param("poCode") String poCode);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int insert(PurchaseOrder purchaseOrder);
}
