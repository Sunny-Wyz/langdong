package com.langdong.spare.mapper;

import com.langdong.spare.entity.AiDeviceFeature;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 设备特征记录 Mapper
 */
@Mapper
public interface AiDeviceFeatureMapper {

    /**
     * 批量查询多台设备近N个月的特征数据（禁止在 for 循环中单条查询）
     *
     * @param deviceIds 设备ID列表
     * @param fromMonth 起始月份（含），格式 yyyy-MM
     */
    List<AiDeviceFeature> findByDeviceIds(
            @Param("deviceIds") List<Long> deviceIds,
            @Param("fromMonth") String fromMonth);

    /**
     * 插入或覆盖更新单条设备特征（REPLACE INTO 保证幂等）
     */
    void insertOrReplace(AiDeviceFeature feature);
}
