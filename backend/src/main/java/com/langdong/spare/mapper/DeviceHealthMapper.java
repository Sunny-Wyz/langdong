package com.langdong.spare.mapper;

import com.langdong.spare.entity.DeviceHealth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 设备健康评估记录 Mapper
 * 对应数据库表：ai_device_health
 */
@Mapper
public interface DeviceHealthMapper {

    /**
     * 插入单条健康评估记录
     *
     * @param deviceHealth 健康评估记录
     * @return 影响行数
     */
    int insert(DeviceHealth deviceHealth);

    /**
     * 批量插入健康评估记录
     *
     * @param list 健康评估记录列表
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<DeviceHealth> list);

    /**
     * 根据设备ID和日期查询健康记录
     *
     * @param deviceId   设备ID
     * @param recordDate 记录日期
     * @return 健康评估记录（含设备信息）
     */
    DeviceHealth findByDeviceAndDate(@Param("deviceId") Long deviceId,
                                      @Param("recordDate") LocalDate recordDate);

    /**
     * 查询设备的最新健康记录
     *
     * @param deviceId 设备ID
     * @return 健康评估记录（含设备信息）
     */
    DeviceHealth findLatestByDevice(@Param("deviceId") Long deviceId);

    /**
     * 查询设备的健康趋势（最近N天）
     *
     * @param deviceId 设备ID
     * @param days     天数
     * @return 健康记录列表（按日期升序）
     */
    List<DeviceHealth> findTrendByDevice(@Param("deviceId") Long deviceId,
                                          @Param("days") int days);

    /**
     * 查询设备在指定日期范围内的健康记录
     *
     * @param deviceId  设备ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 健康记录列表
     */
    List<DeviceHealth> findByDeviceAndDateRange(@Param("deviceId") Long deviceId,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    /**
     * 查询指定风险等级的设备列表（按健康分升序）
     *
     * @param riskLevel 风险等级（CRITICAL/HIGH/MEDIUM/LOW）
     * @param limit     返回数量限制（0=全部）
     * @return 设备健康记录列表（含设备信息）
     */
    List<DeviceHealth> findByRiskLevel(@Param("riskLevel") String riskLevel,
                                        @Param("limit") int limit);

    /**
     * 查询所有设备的最新健康状态（分页）
     *
     * @param riskLevel 风险等级过滤（null=全部）
     * @param deviceCode 设备编码关键词（null=全部，模糊匹配）
     * @param offset    分页偏移量
     * @param size      每页大小
     * @return 设备健康记录列表
     */
    List<DeviceHealth> findLatestAllDevices(@Param("riskLevel") String riskLevel,
                                             @Param("deviceCode") String deviceCode,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    /**
     * 统计查询总记录数（配合 findLatestAllDevices 使用）
     */
    long countLatestAllDevices(@Param("riskLevel") String riskLevel,
                                @Param("deviceCode") String deviceCode);

    /**
     * 统计各风险等级的设备数量
     *
     * @return Map列表，格式：[{riskLevel: "CRITICAL", cnt: 5}, ...]
     */
    List<java.util.Map<String, Object>> countByRiskLevel();

    /**
     * 统计最新健康评估的设备总数
     *
     * @return 设备数量
     */
    long countTotalDevices();

    /**
     * 计算所有设备的平均健康评分
     *
     * @return 平均健康分（0-100）
     */
    Double calcAvgHealthScore();

    /**
     * 查询健康评分最低的设备排行榜
     *
     * @param limit 返回数量
     * @return 设备健康记录列表
     */
    List<DeviceHealth> findLowestHealthRanking(@Param("limit") int limit);

    /**
     * 删除指定日期之前的历史记录（数据归档用）
     *
     * @param beforeDate 截止日期
     * @return 删除行数
     */
    int deleteBeforeDate(@Param("beforeDate") LocalDate beforeDate);
}
