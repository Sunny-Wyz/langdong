package com.langdong.spare.mapper;

import com.langdong.spare.entity.PurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PurchaseOrderItemMapper {
    List<PurchaseOrderItem> findByPurchaseOrderId(@Param("purchaseOrderId") Long purchaseOrderId);

    int updateReceivedQuantity(@Param("id") Long id, @Param("addedQuantity") Integer addedQuantity);

    int insert(PurchaseOrderItem item);
}
