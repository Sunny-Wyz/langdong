package com.langdong.spare.service;

import com.langdong.spare.dto.StockInItemDTO;
import com.langdong.spare.dto.StockInRequestDTO;
import com.langdong.spare.entity.*;
import com.langdong.spare.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 收货入库服务。
 * 优先对接 biz_purchase_order（采购模块主表），兼容旧表 purchase_order + purchase_order_item。
 */
@Service
public class StockInService {

    private static final Set<String> BIZ_RECEIVABLE_STATUS = Set.of("到货", "验收通过");

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;
    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;
    @Autowired
    private StockInReceiptMapper stockInReceiptMapper;
    @Autowired
    private StockInItemMapper stockInItemMapper;
    @Autowired
    private SparePartStockMapper sparePartStockMapper;

    /** 可收货采购单列表（供收货页下拉） */
    public List<PurchaseOrder> listReceivableOrders() {
        List<PurchaseOrder> result = new ArrayList<>();
        List<PurchaseOrder> bizList = purchaseOrderMapper.findReceivableBizOrders();
        if (bizList != null) {
            result.addAll(bizList);
        }
        return result;
    }

    public List<PurchaseOrderItem> getPendingItems(String poCode) {
        ResolvedOrder resolved = resolveOrder(poCode);
        if (resolved == null) {
            throw new RuntimeException("采购单不存在: " + poCode);
        }
        if (!resolved.receivable) {
            throw new RuntimeException(resolved.blockReason != null
                    ? resolved.blockReason
                    : "当前采购单状态不可收货");
        }
        return resolved.items;
    }

    /**
     * 按业务采购单直接入库（验收合格自动入库入口）。
     */
    @Transactional
    public void stockInBizOrder(String orderNo, Integer actualQty, Long userId, String remark) {
        PurchaseOrder po = purchaseOrderMapper.findBizByCode(orderNo);
        if (po == null) {
            throw new RuntimeException("采购单不存在: " + orderNo);
        }
        int received = po.getReceivedQty() != null ? po.getReceivedQty() : 0;
        int orderQty = po.getOrderQty() != null ? po.getOrderQty() : 0;
        int remaining = Math.max(0, orderQty - received);
        int qty = actualQty != null && actualQty > 0 ? Math.min(actualQty, remaining) : remaining;
        if (qty <= 0) {
            // 已收满则只更新状态，不再重复入库
            if (received >= orderQty) {
                purchaseOrderMapper.updateStatus(po.getId(), "验收通过");
                return;
            }
            throw new RuntimeException("该采购单已无可收数量");
        }

        StockInRequestDTO request = new StockInRequestDTO();
        request.setPurchaseOrderCode(orderNo);
        request.setRemark(remark != null ? remark : "验收合格自动入库");
        request.setAllowOverReceive(false);

        StockInItemDTO item = new StockInItemDTO();
        item.setPoItemId(po.getId());
        item.setSparePartId(po.getSparePartId());
        item.setActualQuantity(qty);
        request.setItems(List.of(item));

        createStockIn(request, userId);
    }

    @Transactional
    public void createStockIn(StockInRequestDTO request, Long userId) {
        ResolvedOrder resolved = resolveOrder(request.getPurchaseOrderCode());
        if (resolved == null) {
            throw new RuntimeException("采购单不存在");
        }
        if (!resolved.receivable) {
            throw new RuntimeException(resolved.blockReason != null
                    ? resolved.blockReason
                    : "当前采购单状态不可收货");
        }

        PurchaseOrder po = resolved.order;
        Map<Long, PurchaseOrderItem> poItemById = resolved.items.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item, (a, b) -> a));

        for (StockInItemDTO reqItem : request.getItems()) {
            if (reqItem.getActualQuantity() == null || reqItem.getActualQuantity() <= 0) {
                continue;
            }
            PurchaseOrderItem poItem = resolveItem(poItemById, resolved.items, reqItem);
            if (poItem == null) {
                throw new RuntimeException("提交的明细行不存在于该采购单中（poItemId=" + reqItem.getPoItemId() + "）");
            }
            int remaining = poItem.getQuantity() - (poItem.getReceivedQuantity() != null ? poItem.getReceivedQuantity() : 0);
            if (reqItem.getActualQuantity() > remaining
                    && (request.getAllowOverReceive() == null || !request.getAllowOverReceive())) {
                throw new RuntimeException("实收数量大于预期数量（超收），请勾选差异确认后再提交");
            }
        }

        String receiptCode = "IN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", (int) (Math.random() * 1000));

        StockInReceipt receipt = new StockInReceipt();
        receipt.setReceiptCode(receiptCode);
        receipt.setPurchaseOrderId(po.getId());
        receipt.setReceiptDate(LocalDateTime.now());
        receipt.setStatus("COMPLETED");
        receipt.setHandlerId(userId);
        receipt.setRemark(request.getRemark());
        stockInReceiptMapper.insert(receipt);

        boolean allCompleted = true;
        boolean anyReceived = false;

        for (StockInItemDTO reqItem : request.getItems()) {
            if (reqItem.getActualQuantity() == null || reqItem.getActualQuantity() <= 0) {
                continue;
            }

            PurchaseOrderItem poItem = resolveItem(poItemById, resolved.items, reqItem);
            if (poItem == null) {
                throw new RuntimeException("明细不存在");
            }

            int prevReceived = poItem.getReceivedQuantity() != null ? poItem.getReceivedQuantity() : 0;

            StockInItem stockInItem = new StockInItem();
            stockInItem.setStockInReceiptId(receipt.getId());
            stockInItem.setPurchaseOrderItemId(resolved.bizOrder ? null : poItem.getId());
            stockInItem.setSparePartId(poItem.getSparePartId());
            stockInItem.setExpectedQuantity(Math.max(0, poItem.getQuantity() - prevReceived));
            stockInItem.setActualQuantity(reqItem.getActualQuantity());
            stockInItem.setRemainingQty(reqItem.getActualQuantity());
            stockInItem.setInTime(LocalDateTime.now());
            stockInItem.setLocationId(reqItem.getLocationId());
            stockInItem.setRemark(reqItem.getRemark());
            stockInItemMapper.insert(stockInItem);

            if (resolved.bizOrder) {
                purchaseOrderMapper.addBizReceivedQty(po.getId(), reqItem.getActualQuantity());
            } else {
                purchaseOrderItemMapper.updateReceivedQuantity(poItem.getId(), reqItem.getActualQuantity());
            }

            int newReceived = prevReceived + reqItem.getActualQuantity();
            poItem.setReceivedQuantity(newReceived);
            if (newReceived < poItem.getQuantity()) {
                allCompleted = false;
            }
            anyReceived = true;

            SparePartStock stock = sparePartStockMapper.findBySparePartId(poItem.getSparePartId());
            if (stock == null) {
                stock = new SparePartStock();
                stock.setSparePartId(poItem.getSparePartId());
                stock.setQuantity(reqItem.getActualQuantity());
                sparePartStockMapper.insert(stock);
            } else {
                sparePartStockMapper.addQuantity(poItem.getSparePartId(), reqItem.getActualQuantity());
            }
        }

        if (!anyReceived) {
            throw new RuntimeException("本次入库没有有效数量");
        }

        if (resolved.bizOrder) {
            if (allCompleted) {
                purchaseOrderMapper.updateStatus(po.getId(), "验收通过");
            }
        } else {
            purchaseOrderMapper.updateLegacyStatus(po.getId(), allCompleted ? "COMPLETED" : "PARTIAL");
        }
    }

    private PurchaseOrderItem resolveItem(Map<Long, PurchaseOrderItem> byId,
                                          List<PurchaseOrderItem> items,
                                          StockInItemDTO reqItem) {
        PurchaseOrderItem poItem = byId.get(reqItem.getPoItemId());
        if (poItem != null) {
            return poItem;
        }
        return items.stream()
                .filter(i -> Objects.equals(i.getSparePartId(), reqItem.getSparePartId()))
                .findFirst()
                .orElse(null);
    }

    private ResolvedOrder resolveOrder(String poCode) {
        if (poCode == null || poCode.isBlank()) {
            return null;
        }
        PurchaseOrder biz = purchaseOrderMapper.findBizByCode(poCode.trim());
        if (biz != null) {
            return buildBizResolved(biz);
        }
        PurchaseOrder legacy = purchaseOrderMapper.findByLegacyCode(poCode.trim());
        if (legacy != null) {
            return buildLegacyResolved(legacy);
        }
        return null;
    }

    private ResolvedOrder buildBizResolved(PurchaseOrder po) {
        ResolvedOrder r = new ResolvedOrder();
        r.order = po;
        r.bizOrder = true;

        int received = po.getReceivedQty() != null ? po.getReceivedQty() : 0;
        int orderQty = po.getOrderQty() != null ? po.getOrderQty() : 0;
        String status = po.getOrderStatus();

        if (status == null || !BIZ_RECEIVABLE_STATUS.contains(status)) {
            r.receivable = false;
            r.blockReason = "采购单状态为「" + status + "」，仅「到货/验收通过」可收货入库";
        } else if (received >= orderQty) {
            r.receivable = false;
            r.blockReason = "该采购单已全部收货完成（已收 " + received + " / 订购 " + orderQty + "）";
        } else {
            r.receivable = true;
        }

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(po.getId());
        item.setPurchaseOrderId(po.getId());
        item.setSparePartId(po.getSparePartId());
        item.setQuantity(orderQty);
        item.setReceivedQuantity(received);
        item.setUnitPrice(po.getUnitPrice());
        item.setSparePartCode(po.getSparePartCode());
        item.setSparePartName(po.getSparePartName());
        r.items = List.of(item);
        return r;
    }

    private ResolvedOrder buildLegacyResolved(PurchaseOrder po) {
        ResolvedOrder r = new ResolvedOrder();
        r.order = po;
        r.bizOrder = false;
        String status = po.getOrderStatus();
        if ("COMPLETED".equalsIgnoreCase(status)) {
            r.receivable = false;
            r.blockReason = "旧采购单已全部入库完成";
        } else {
            r.receivable = true;
        }
        List<PurchaseOrderItem> items = purchaseOrderItemMapper.findByPurchaseOrderId(po.getId());
        r.items = items != null ? items : List.of();
        if (r.receivable && !r.items.isEmpty() && r.items.stream().allMatch(i -> {
            int recv = i.getReceivedQuantity() != null ? i.getReceivedQuantity() : 0;
            return recv >= (i.getQuantity() != null ? i.getQuantity() : 0);
        })) {
            r.receivable = false;
            r.blockReason = "旧采购单明细均已收满";
        }
        return r;
    }

    private static class ResolvedOrder {
        PurchaseOrder order;
        boolean bizOrder;
        boolean receivable;
        String blockReason;
        List<PurchaseOrderItem> items = List.of();
    }
}
