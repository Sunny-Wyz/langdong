package com.langdong.spare.service;

import com.langdong.spare.dto.AcceptanceDTO;
import com.langdong.spare.dto.PurchaseOrderCreateDTO;
import com.langdong.spare.dto.QuoteDTO;
import com.langdong.spare.entity.EquipmentSparePart;
import com.langdong.spare.entity.PurchaseOrder;
import com.langdong.spare.entity.SupplierQuote;
import com.langdong.spare.mapper.EquipmentSparePartMapper;
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
    @Autowired
    private StockInService stockInService;
    @Autowired
    private EquipmentSparePartMapper equipmentSparePartMapper;

    /** 发起采购申请 */
    @Transactional
    public PurchaseOrder create(PurchaseOrderCreateDTO dto, Long purchaserId) {
        if (dto.getEquipmentId() != null && dto.getSparePartId() != null) {
            EquipmentSparePart link = equipmentSparePartMapper.findByEqAndSpId(
                    dto.getEquipmentId(), dto.getSparePartId());
            if (link == null) {
                throw new IllegalArgumentException("所选备件不属于该设备配套关系，请重新选择");
            }
        }

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo("PO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        order.setSparePartId(dto.getSparePartId());
        order.setSupplierId(dto.getSupplierId());
        order.setOrderQty(dto.getOrderQty());
        order.setReceivedQty(0);
        order.setOrderStatus("已下单");
        order.setExpectedDate(dto.getExpectedDate());
        order.setReorderSuggestId(dto.getReorderSuggestId());
        order.setPurchaserId(purchaserId);
        String remark = dto.getRemark();
        if (dto.getEquipmentId() != null) {
            String tag = "[设备ID:" + dto.getEquipmentId() + "]";
            remark = remark == null || remark.isBlank() ? tag : remark + " " + tag;
        }
        order.setRemark(remark);

        purchaseOrderMapper.insert(order);

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

    public void updateStatus(Long id, String orderStatus) {
        purchaseOrderMapper.updateStatus(id, orderStatus);
    }

    @Transactional
    public void addQuote(Long orderId, QuoteDTO dto) {
        PurchaseOrder order = purchaseOrderMapper.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
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
        if (order == null) {
            return List.of();
        }
        return supplierQuoteMapper.findByOrderNo(order.getOrderNo());
    }

    @Transactional
    public void selectQuote(Long orderId, Long quoteId) {
        PurchaseOrder order = purchaseOrderMapper.findById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        supplierQuoteMapper.clearSelections(order.getOrderNo());
        supplierQuoteMapper.selectQuote(quoteId);

        List<SupplierQuote> quotes = supplierQuoteMapper.findByOrderNo(order.getOrderNo());
        quotes.stream()
                .filter(q -> q.getId().equals(quoteId))
                .findFirst()
                .ifPresent(q -> {
                    BigDecimal total = q.getQuotePrice().multiply(BigDecimal.valueOf(order.getOrderQty()));
                    purchaseOrderMapper.updatePriceAndStatus(orderId, q.getQuotePrice(), total, "已下单");
                });
    }

    /**
     * 到货验收：合格则更新状态并自动入库；不合格仅改状态。
     */
    @Transactional
    public void accept(Long id, AcceptanceDTO dto, Long userId) {
        PurchaseOrder order = purchaseOrderMapper.findById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        String status = Boolean.TRUE.equals(dto.getQualified()) ? "验收通过" : "验收失败";
        LocalDate actualDate = Boolean.TRUE.equals(dto.getQualified()) ? LocalDate.now() : null;
        purchaseOrderMapper.updateAcceptance(id, status, actualDate, dto.getRemark());

        if (Boolean.TRUE.equals(dto.getQualified())) {
            // 自动入库：使用验收数量（默认剩余未收数量）
            stockInService.stockInBizOrder(
                    order.getOrderNo(),
                    dto.getReceivedQty(),
                    userId,
                    dto.getRemark() != null && !dto.getRemark().isBlank()
                            ? "验收合格自动入库：" + dto.getRemark()
                            : "验收合格自动入库"
            );
        }
    }
}
