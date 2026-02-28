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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StockInService {

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

    public List<PurchaseOrderItem> getPendingItems(String poCode) {
        PurchaseOrder po = purchaseOrderMapper.findByCode(poCode);
        if (po == null) {
            throw new RuntimeException("采购单不存在");
        }
        return purchaseOrderItemMapper.findByPurchaseOrderId(po.getId());
    }

    @Transactional
    public void createStockIn(StockInRequestDTO request, Long userId) {
        PurchaseOrder po = purchaseOrderMapper.findByCode(request.getPurchaseOrderCode());
        if (po == null) {
            throw new RuntimeException("采购单不存在");
        }

        List<PurchaseOrderItem> poItems = purchaseOrderItemMapper.findByPurchaseOrderId(po.getId());
        // 使用明细行ID作为Key，避免同一采购单中出现多条同备件明细时的重复键问题
        Map<Long, PurchaseOrderItem> poItemById = poItems.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));

        // 1. 差异确认与约束校验
        for (StockInItemDTO reqItem : request.getItems()) {
            if (reqItem.getActualQuantity() == null || reqItem.getActualQuantity() <= 0)
                continue;
            PurchaseOrderItem poItem = poItemById.get(reqItem.getPoItemId());
            if (poItem == null) {
                throw new RuntimeException("提交的明细行不存在于该采购单中（poItemId=" + reqItem.getPoItemId() + "）");
            }
            int remaining = poItem.getQuantity() - poItem.getReceivedQuantity();
            if (reqItem.getActualQuantity() > remaining
                    && (request.getAllowOverReceive() == null || !request.getAllowOverReceive())) {
                throw new RuntimeException("实收数量大于预期数量（超收），请勾选差异确认后再提交");
            }
        }

        // 2. 生成入库单号
        String receiptCode = "IN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // 3. 保存收货入库单
        StockInReceipt receipt = new StockInReceipt();
        receipt.setReceiptCode(receiptCode);
        receipt.setPurchaseOrderId(po.getId());
        receipt.setReceiptDate(LocalDateTime.now());
        receipt.setStatus("COMPLETED");
        receipt.setHandlerId(userId);
        receipt.setRemark(request.getRemark());
        stockInReceiptMapper.insert(receipt);

        boolean allCompleted = true;

        // 4. 处理每条明细
        for (StockInItemDTO reqItem : request.getItems()) {
            if (reqItem.getActualQuantity() == null || reqItem.getActualQuantity() <= 0)
                continue;

            PurchaseOrderItem poItem = poItemById.get(reqItem.getPoItemId());

            // a. 保存入库明细流水
            StockInItem stockInItem = new StockInItem();
            stockInItem.setStockInReceiptId(receipt.getId());
            stockInItem.setPurchaseOrderItemId(poItem.getId());
            stockInItem.setSparePartId(poItem.getSparePartId());
            stockInItem.setExpectedQuantity(poItem.getQuantity() - poItem.getReceivedQuantity());
            stockInItem.setActualQuantity(reqItem.getActualQuantity());
            stockInItem.setLocationId(reqItem.getLocationId());
            stockInItem.setRemark(reqItem.getRemark());
            stockInItemMapper.insert(stockInItem);

            // b. 更新采购单已收数量
            purchaseOrderItemMapper.updateReceivedQuantity(poItem.getId(), reqItem.getActualQuantity());

            int newReceived = poItem.getReceivedQuantity() + reqItem.getActualQuantity();
            if (newReceived < poItem.getQuantity()) {
                allCompleted = false;
            }

            // c. 更新库存台账
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

        // 5. 更新采购单状态（老表 purchase_order）
        purchaseOrderMapper.updateLegacyStatus(po.getId(), allCompleted ? "COMPLETED" : "PARTIAL");
    }
}
