package com.langdong.spare.mapper;

import com.langdong.spare.entity.OutboundBatchTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 出库-批次追溯 Mapper
 */
@Mapper
public interface OutboundBatchTraceMapper {

    /**
     * 插入批次追溯记录
     */
    int insert(OutboundBatchTrace trace);

    /**
     * 批量插入（用于一次出库涉及多个批次）
     */
    int insertBatch(@Param("traces") List<OutboundBatchTrace> traces);

    /**
     * 根据领用明细ID查询批次追溯记录
     */
    List<OutboundBatchTrace> findByReqItemId(@Param("reqItemId") Long reqItemId);

    /**
     * 根据入库批次ID查询追溯记录（查看某批次被哪些出库单使用）
     */
    List<OutboundBatchTrace> findByStockInItemId(@Param("stockInItemId") Long stockInItemId);
}
