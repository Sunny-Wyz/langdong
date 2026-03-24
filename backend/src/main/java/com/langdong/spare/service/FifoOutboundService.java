package com.langdong.spare.service;

import com.langdong.spare.entity.OutboundBatchTrace;
import com.langdong.spare.entity.StockInItem;
import com.langdong.spare.mapper.OutboundBatchTraceMapper;
import com.langdong.spare.mapper.SparePartStockMapper;
import com.langdong.spare.mapper.StockInItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FIFO 出库核心服务
 * 负责按先进先出原则从批次库存中扣减
 */
@Slf4j
@Service
public class FifoOutboundService {

    @Autowired
    private StockInItemMapper stockInItemMapper;

    @Autowired
    private OutboundBatchTraceMapper outboundBatchTraceMapper;

    @Autowired
    private SparePartStockMapper sparePartStockMapper;

    /**
     * 执行 FIFO 出库扣减
     *
     * @param reqItemId    领用明细ID
     * @param sparePartId  备件ID
     * @param requiredQty  需要出库的数量
     * @return 批次分配信息摘要（如：IN20240101[10件] + IN20240102[5件]）
     * @throws RuntimeException 库存不足时抛出异常
     */
    @Transactional(rollbackFor = Exception.class)
    public String processFifoOutbound(Long reqItemId, Long sparePartId, int requiredQty) {
        log.info("开始 FIFO 出库: reqItemId={}, sparePartId={}, requiredQty={}",
                reqItemId, sparePartId, requiredQty);

        // 1. 检查总库存是否充足
        int totalAvailable = sparePartStockMapper.getAvailableQuantity(sparePartId);
        if (totalAvailable < requiredQty) {
            String errorMsg = String.format("备件[%d]库存不足，需要%d件，可用%d件",
                    sparePartId, requiredQty, totalAvailable);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        // 2. 按入库时间升序加载所有有余量的批次（FIFO 核心排序）
        List<StockInItem> availableBatches = stockInItemMapper
                .findAvailableBatchesBySparePartId(sparePartId);

        if (availableBatches.isEmpty()) {
            throw new RuntimeException("未找到可用入库批次，数据异常");
        }

        // 3. 逐批次扣减
        int remainingNeed = requiredQty;
        List<OutboundBatchTrace> traceRecords = new ArrayList<>();
        StringBuilder batchInfoBuilder = new StringBuilder();

        for (StockInItem batch : availableBatches) {
            if (remainingNeed <= 0) {
                break; // 已满足需求
            }

            int batchRemaining = batch.getRemainingQty();
            int deductQty;

            if (batchRemaining >= remainingNeed) {
                // 当前批次足以满足剩余需求
                deductQty = remainingNeed;
                remainingNeed = 0;
            } else {
                // 当前批次不够，全部扣完
                deductQty = batchRemaining;
                remainingNeed -= batchRemaining;
            }

            // 4. 扣减批次库存（UPDATE stock_in_item SET remaining_qty = remaining_qty - deductQty）
            int updated = stockInItemMapper.deductBatchQuantity(batch.getId(), deductQty);
            if (updated == 0) {
                throw new RuntimeException("批次库存扣减失败，可能并发冲突或数据异常");
            }

            // 5. 记录批次追溯
            OutboundBatchTrace trace = new OutboundBatchTrace();
            trace.setReqItemId(reqItemId);
            trace.setStockInItemId(batch.getId());
            trace.setSparePartId(sparePartId);
            trace.setDeductQty(deductQty);
            trace.setOutboundTime(LocalDateTime.now());
            traceRecords.add(trace);

            // 6. 构建批次信息摘要
            if (batchInfoBuilder.length() > 0) {
                batchInfoBuilder.append(" + ");
            }
            batchInfoBuilder.append(String.format("%s[%d件]",
                    batch.getReceiptCode(), deductQty));

            log.info("从批次[{}]扣减{}件，剩余需求{}件", batch.getReceiptCode(), deductQty, remainingNeed);
        }

        // 7. 批量插入追溯记录
        if (!traceRecords.isEmpty()) {
            outboundBatchTraceMapper.insertBatch(traceRecords);
        }

        // 8. 扣减总库存（spare_part_stock 表）
        sparePartStockMapper.addQuantity(sparePartId, -requiredQty);

        String batchInfo = batchInfoBuilder.toString();
        log.info("FIFO 出库完成: reqItemId={}, 批次分配={}", reqItemId, batchInfo);

        return batchInfo;
    }

    /**
     * 查询出库明细的批次追溯信息
     */
    public List<OutboundBatchTrace> getOutboundBatchTrace(Long reqItemId) {
        return outboundBatchTraceMapper.findByReqItemId(reqItemId);
    }

    /**
     * 查询入库批次的使用情况
     */
    public List<OutboundBatchTrace> getBatchUsageTrace(Long stockInItemId) {
        return outboundBatchTraceMapper.findByStockInItemId(stockInItemId);
    }
}
