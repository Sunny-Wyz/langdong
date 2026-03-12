package com.langdong.spare.service;

import com.langdong.spare.dto.AcceptanceDTO;
import com.langdong.spare.dto.PurchaseOrderCreateDTO;
import com.langdong.spare.dto.QuoteDTO;
import com.langdong.spare.entity.PurchaseOrder;
import com.langdong.spare.entity.SupplierQuote;
import com.langdong.spare.mapper.PurchaseOrderMapper;
import com.langdong.spare.mapper.ReorderSuggestMapper;
import com.langdong.spare.mapper.SupplierQuoteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PurchaseOrderService {

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;
    @Autowired
    private ReorderSuggestMapper reorderSuggestMapper;
    @Autowired
    private SupplierQuoteMapper supplierQuoteMapper;

    /** 发起采购申请 (生成采购订单核心逻辑) */
    @Transactional
    public PurchaseOrder create(PurchaseOrderCreateDTO dto, Long purchaserId) {
        PurchaseOrder order = new PurchaseOrder();

        // [核心步骤 1] 自动生成采购单号：前缀 PO + 年月日时分秒流水
        order.setOrderNo("PO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));

        // [核心步骤 2] 填充前端传入的采购目标数据 (备件、供应商、采购量、期望交期等)
        order.setSparePartId(dto.getSparePartId());
        order.setSupplierId(dto.getSupplierId());
        order.setOrderQty(dto.getOrderQty());

        // [核心步骤 3] 初始化采购订单状态为 "已下单" (后续可流转为 "已发货", "到货验收" 等)
        order.setOrderStatus("已下单");

        order.setExpectedDate(dto.getExpectedDate());
        order.setReorderSuggestId(dto.getReorderSuggestId());

        // 绑定当前操作人作为采购专员
        order.setPurchaserId(purchaserId);
        order.setRemark(dto.getRemark());

        // [核心步骤 4] 插入订单数据至持久层 (biz_purchase_order 表)
        purchaseOrderMapper.insert(order);

        // [核心步骤 5] 业务闭环联动：如果本次采购是由系统的 "缺件补货建议" 发起的，需要反向把补货建议状态置为 "已采购"
        if (dto.getReorderSuggestId() != null) {
            reorderSuggestMapper.updateStatus(dto.getReorderSuggestId(), "已采购");
        }
        return order;
    }

    public List<PurchaseOrder> getList(String orderStatus, Long sparePartId, Long supplierId) {
        return purchaseOrderMapper.findList(orderStatus, sparePartId, supplierId);
    }

    public PurchaseOrder getDetail(Long id) {
        return purchaseOrderMapper.findById(id);
    }

    /** 更新订单状态（已下单→已发货→到货） */
    public void updateStatus(Long id, String orderStatus) {
        purchaseOrderMapper.updateStatus(id, orderStatus);
    }

    /** 录入询价 */
    @Transactional
    public void addQuote(Long orderId, QuoteDTO dto) {
        PurchaseOrder order = purchaseOrderMapper.findById(orderId);
        if (order == null)
            throw new IllegalArgumentException("订单不存在");
        SupplierQuote quote = new SupplierQuote();
        quote.setOrderNo(order.getOrderNo());
        quote.setSupplierId(dto.getSupplierId());
        quote.setSparePartId(order.getSparePartId());
        quote.setQuotePrice(dto.getQuotePrice());
        quote.setDeliveryDays(dto.getDeliveryDays());
        supplierQuoteMapper.insert(quote);
    }

    public List<SupplierQuote> getQuotes(Long orderId) {
        PurchaseOrder order = purchaseOrderMapper.findById(orderId);
        if (order == null)
            return List.of();
        return supplierQuoteMapper.findByOrderNo(order.getOrderNo());
    }

    /** 选中报价中标并更新订单金额 */
    @Transactional
    public void selectQuote(Long orderId, Long quoteId) {
        PurchaseOrder order = purchaseOrderMapper.findById(orderId);
        if (order == null)
            throw new IllegalArgumentException("订单不存在");

        // 先清空该订单所有中标标记
        supplierQuoteMapper.clearSelections(order.getOrderNo());
        // 再标记选中
        supplierQuoteMapper.selectQuote(quoteId);

        // 获取选中报价的价格，回填订单
        List<SupplierQuote> quotes = supplierQuoteMapper.findByOrderNo(order.getOrderNo());
        quotes.stream()
                .filter(q -> q.getId().equals(quoteId))
                .findFirst()
                .ifPresent(q -> {
                    BigDecimal total = q.getQuotePrice().multiply(BigDecimal.valueOf(order.getOrderQty()));
                    purchaseOrderMapper.updatePriceAndStatus(orderId, q.getQuotePrice(), total, "已下单");
                });
    }

    /** 到货验收 */
    @Transactional
    public void accept(Long id, AcceptanceDTO dto) {
        String status = Boolean.TRUE.equals(dto.getQualified()) ? "验收通过" : "验收失败";
        LocalDate actualDate = Boolean.TRUE.equals(dto.getQualified()) ? LocalDate.now() : null;
        purchaseOrderMapper.updateAcceptance(id, status, actualDate, dto.getRemark());
    }
}
