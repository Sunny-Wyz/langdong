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
        Map<Long, PurchaseOrderItem> poItemMap = poItems.stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getSparePartId, item -> item));

        // 1. 差异确认与约束校验
        for (StockInItemDTO reqItem : request.getItems()) {
            PurchaseOrderItem poItem = poItemMap.get(reqItem.getSparePartId());
            if (poItem == null) {
                throw new RuntimeException("提交的备件不存在于该采购单中");
            }
            int remainingToReceive = poItem.getQuantity() - poItem.getReceivedQuantity();
            if (reqItem.getActualQuantity() > remainingToReceive
                    && (request.getAllowOverReceive() == null || !request.getAllowOverReceive())) {
                throw new RuntimeException("实收数量大于预期数量（超收），需用户二次确认");
            }
        }

        // 2. 生成入库单号
        String receiptCode = "IN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

        // 3. 保存收货入库单 (stock_in_receipt)
        StockInReceipt receipt = new StockInReceipt();
        receipt.setReceiptCode(receiptCode);
        receipt.setPurchaseOrderId(po.getId());
        receipt.setReceiptDate(LocalDateTime.now());
        receipt.setStatus("COMPLETED");
        receipt.setHandlerId(userId);
        receipt.setRemark(request.getRemark());
        stockInReceiptMapper.insert(receipt);

        boolean isPartiallyReceived = false;

        // 4. 处理明细、更新台账及采购单数量
        for (StockInItemDTO reqItem : request.getItems()) {
            if (reqItem.getActualQuantity() <= 0) {
                continue; // 过滤掉数量为0或负数的错误数据
            }

            PurchaseOrderItem poItem = poItemMap.get(reqItem.getSparePartId());

            // a. 保存入库明细
            StockInItem stockInItem = new StockInItem();
            stockInItem.setStockInReceiptId(receipt.getId());
            stockInItem.setPurchaseOrderItemId(poItem.getId());
            stockInItem.setSparePartId(reqItem.getSparePartId());
            stockInItem.setExpectedQuantity(poItem.getQuantity() - poItem.getReceivedQuantity());
            stockInItem.setActualQuantity(reqItem.getActualQuantity());
            stockInItem.setLocationId(reqItem.getLocationId());
            stockInItem.setRemark(reqItem.getRemark());
            stockInItemMapper.insert(stockInItem);

            // b. 更新采购单已收数量
            purchaseOrderItemMapper.updateReceivedQuantity(poItem.getId(), reqItem.getActualQuantity());

            // 检查采购单该项是否未完全收货
            if (poItem.getReceivedQuantity() + reqItem.getActualQuantity() < poItem.getQuantity()) {
                isPartiallyReceived = true;
            }

            // c. 更新库存台账
            SparePartStock stock = sparePartStockMapper.findBySparePartId(reqItem.getSparePartId());
            if (stock == null) {
                stock = new SparePartStock();
                stock.setSparePartId(reqItem.getSparePartId());
                stock.setQuantity(reqItem.getActualQuantity());
                sparePartStockMapper.insert(stock);
            } else {
                sparePartStockMapper.addQuantity(reqItem.getSparePartId(), reqItem.getActualQuantity());
            }
        }

        // 5. 更新采购单状态
        if (isPartiallyReceived) {
            purchaseOrderMapper.updateStatus(po.getId(), "PARTIAL");
        } else {
            // 简单逻辑：只要没有明细表明还是部分，就认为是全部完成。实际应当遍历所有明细聚合验证。
            purchaseOrderMapper.updateStatus(po.getId(), "COMPLETED");
        }
    }
}
