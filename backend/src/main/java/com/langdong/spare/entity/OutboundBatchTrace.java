package com.langdong.spare.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 出库-批次追溯实体
 * 记录每次出库从哪些入库批次扣减了多少数量
 */
@Data
public class OutboundBatchTrace {
    private Long id;
    private Long reqItemId;         // 领用明细ID（关联 biz_requisition_item.id）
    private Long stockInItemId;     // 入库批次ID（关联 stock_in_item.id）
    private Long sparePartId;       // 备件ID（冗余字段，便于查询）
    private Integer deductQty;      // 从该批次扣减的数量
    private LocalDateTime outboundTime;
    private LocalDateTime createdAt;

    // 瞬态字段（用于前端展示，联表查询时填充）
    private String receiptCode;     // 入库单号
    private String sparePartCode;   // 备件编码
    private String batchInfo;       // 批次信息摘要
}
