package com.langdong.spare.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 内部 AI 数据查询 Mapper
 * 供 Python AI 服务通过内部 API 获取数据，替代 Python 直连数据库。
 */
@Mapper
public interface InternalAiDataMapper {

    /** 获取备件传感器历史数据 */
    List<Map<String, Object>> findSensorData(@Param("sparePartId") int sparePartId);

    /** 获取备件基本信息 */
    Map<String, Object> findSparePartInfo(@Param("sparePartId") int sparePartId);

    /** 获取月度消耗日志（spare_part_consumption_log 回退表） */
    List<Map<String, Object>> findConsumptionLog(@Param("sparePartId") int sparePartId);

    /** 从业务表聚合月度消耗 */
    List<Map<String, Object>> findConsumptionFromBusiness(@Param("sparePartId") int sparePartId);

    /** 备件级别月均需求估算 */
    Double estimatePartMonthlyDemand(@Param("sparePartId") int sparePartId);

    /** 类目级别月均需求估算 */
    Double estimateCategoryMonthlyDemand(@Param("categoryId") int categoryId);

    /** 全局月均需求估算 */
    Double estimateGlobalMonthlyDemand();

    /** 加载供应商绩效数据 */
    List<Map<String, Object>> findSupplierPerformance(@Param("sparePartId") int sparePartId);
}
