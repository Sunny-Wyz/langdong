package com.langdong.spare.controller;

import com.langdong.spare.entity.OutboundBatchTrace;
import com.langdong.spare.service.FifoOutboundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 批次追溯查询控制器
 */
@RestController
@RequestMapping("/api/outbound-trace")
public class OutboundBatchTraceController {

    @Autowired
    private FifoOutboundService fifoOutboundService;

    /**
     * 查询出库明细的批次追溯记录
     * GET /api/outbound-trace/requisition-item/{reqItemId}
     *
     * @param reqItemId 领用明细ID
     * @return 批次追溯记录列表
     */
    @GetMapping("/requisition-item/{reqItemId}")
    public ResponseEntity<List<OutboundBatchTrace>> getByReqItem(@PathVariable Long reqItemId) {
        List<OutboundBatchTrace> traces = fifoOutboundService.getOutboundBatchTrace(reqItemId);
        return ResponseEntity.ok(traces);
    }

    /**
     * 查询入库批次的使用情况
     * GET /api/outbound-trace/stock-in-batch/{stockInItemId}
     *
     * @param stockInItemId 入库批次ID
     * @return 批次使用记录列表
     */
    @GetMapping("/stock-in-batch/{stockInItemId}")
    public ResponseEntity<List<OutboundBatchTrace>> getByStockInBatch(@PathVariable Long stockInItemId) {
        List<OutboundBatchTrace> traces = fifoOutboundService.getBatchUsageTrace(stockInItemId);
        return ResponseEntity.ok(traces);
    }
}
