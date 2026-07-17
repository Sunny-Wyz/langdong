package com.langdong.spare.mapper;

import com.langdong.spare.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PurchaseOrderMapper {
    // ---- M6 new biz_purchase_order methods ----
    void insert(PurchaseOrder order);

    PurchaseOrder findById(Long id);

    List<PurchaseOrder> findList(@Param("orderStatus") String orderStatus,
                                 @Param("sparePartId") Long sparePartId,
                                 @Param("supplierId") Long supplierId);

    void updateStatus(@Param("id") Long id, @Param("orderStatus") String orderStatus);

    void updatePriceAndStatus(@Param("id") Long id,
                              @Param("unitPrice") BigDecimal unitPrice,
                              @Param("totalAmount") BigDecimal totalAmount,
                              @Param("orderStatus") String orderStatus);

    void updateAcceptance(@Param("id") Long id,
                          @Param("orderStatus") String orderStatus,
                          @Param("actualDate") LocalDate actualDate,
                          @Param("remark") String remark);

    /** 按业务采购单号查询（biz_purchase_order） */
    PurchaseOrder findBizByCode(@Param("poCode") String poCode);

    /** 可收货业务采购单列表 */
    List<PurchaseOrder> findReceivableBizOrders();

    /** 累加业务采购单已收数量 */
    void addBizReceivedQty(@Param("id") Long id, @Param("addedQuantity") Integer addedQuantity);

    // ---- Legacy methods used by StockInService (old purchase_order table) ----
    PurchaseOrder findByLegacyCode(@Param("poCode") String poCode);

    void updateLegacyStatus(@Param("id") Long id, @Param("status") String status);

    /** @deprecated 使用 findBizByCode / findByLegacyCode */
    @Deprecated
    default PurchaseOrder findByCode(String poCode) {
        PurchaseOrder biz = findBizByCode(poCode);
        return biz != null ? biz : findByLegacyCode(poCode);
    }
}
