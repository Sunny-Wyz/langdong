package com.langdong.spare.mapper;

import com.langdong.spare.entity.FaultPrediction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 设备故障预测结果 Mapper
 * 对应数据库表：ai_fault_prediction
 */
@Mapper
public interface FaultPredictionMapper {

    /**
     * 插入单条预测记录
     *
     * @param prediction 预测记录
     * @return 影响行数
     */
    int insert(FaultPrediction prediction);

    /**
     * 批量插入预测记录
     *
     * @param list 预测记录列表
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<FaultPrediction> list);

    /**
     * 根据设备ID和目标月份查询预测结果
     *
     * @param deviceId    设备ID
     * @param targetMonth 目标月份（yyyy-MM）
     * @return 预测记录（含设备和健康信息）
     */
    FaultPrediction findByDeviceAndMonth(@Param("deviceId") Long deviceId,
                                          @Param("targetMonth") String targetMonth);

    /**
     * 查询设备的最新预测记录
     *
     * @param deviceId 设备ID
     * @return 预测记录（含设备和健康信息）
     */
    FaultPrediction findLatestByDevice(@Param("deviceId") Long deviceId);

    /**
     * 查询设备的预测历史（最近N个月）
     *
     * @param deviceId 设备ID
     * @param months   月数
     * @return 预测记录列表（按目标月份升序）
     */
    List<FaultPrediction> findHistoryByDevice(@Param("deviceId") Long deviceId,
                                               @Param("months") int months);

    /**
     * 查询高风险设备列表（故障概率高于阈值）
     *
     * @param probabilityThreshold 故障概率阈值（默认0.5）
     * @param limit                返回数量限制（0=全部）
     * @return 预测记录列表（含设备和健康信息，按故障概率降序）
     */
    List<FaultPrediction> findHighRiskDevices(@Param("probabilityThreshold") double probabilityThreshold,
                                                @Param("limit") int limit);

    /**
     * 查询所有设备的最新预测（分页）
     *
     * @param deviceCode 设备编码关键词（null=全部，模糊匹配）
     * @param minProbability 最小故障概率过滤（null=不过滤）
     * @param offset     分页偏移量
     * @param size       每页大小
     * @return 预测记录列表
     */
    List<FaultPrediction> findLatestAllDevices(@Param("deviceCode") String deviceCode,
                                                @Param("minProbability") Double minProbability,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    /**
     * 统计查询总记录数（配合 findLatestAllDevices 使用）
     */
    long countLatestAllDevices(@Param("deviceCode") String deviceCode,
                                @Param("minProbability") Double minProbability);

    /**
     * 统计指定月份的预测设备总数
     *
     * @param targetMonth 目标月份（yyyy-MM）
     * @return 设备数量
     */
    long countByMonth(@Param("targetMonth") String targetMonth);

    /**
     * 统计高风险设备数量（故障概率高于阈值）
     *
     * @param probabilityThreshold 故障概率阈值
     * @return 设备数量
     */
    long countHighRiskDevices(@Param("probabilityThreshold") double probabilityThreshold);

    /**
     * 查询预测准确率统计（对比历史预测值和实际值）
     * 返回格式：[{targetMonth, avgError, accuracy}, ...]
     *
     * @param months 统计最近N个月
     * @return 准确率统计列表
     */
    List<java.util.Map<String, Object>> calcPredictionAccuracy(@Param("months") int months);

    /**
     * 删除指定日期之前的历史记录（数据归档用）
     *
     * @param beforeDate 截止日期
     * @return 删除行数
     */
    int deleteBeforeDate(@Param("beforeDate") LocalDate beforeDate);
}
