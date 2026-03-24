package com.langdong.spare.mapper;

import com.langdong.spare.entity.StockInItem;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface StockInItemMapper {
    int insert(StockInItem item);

    List<StockInItem> findPendingShelving();

    int updateShelvedQuantity(@Param("id") Long id, @Param("addedQuantity") Integer addedQuantity);

    // ===== FIFO 新增方法 =====

    /**
     * 查询指定备件的可用批次（按入库时间升序，FIFO核心）
     * @param sparePartId 备件ID
     * @return 按 in_time ASC 排序的批次列表（只返回 remaining_qty > 0 的批次）
     */
    List<StockInItem> findAvailableBatchesBySparePartId(@Param("sparePartId") Long sparePartId);

    /**
     * 扣减批次库存（原子操作）
     * @param batchId 批次ID
     * @param deductQty 扣减数量
     * @return 影响行数
     */
    int deductBatchQuantity(@Param("batchId") Long batchId, @Param("deductQty") Integer deductQty);
}
