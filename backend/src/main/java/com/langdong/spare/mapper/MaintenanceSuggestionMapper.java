package com.langdong.spare.mapper;

import com.langdong.spare.entity.MaintenanceSuggestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 预防性维护建议 Mapper
 * 对应数据库表：biz_maintenance_suggestion
 */
@Mapper
public interface MaintenanceSuggestionMapper {

    /**
     * 插入单条建议记录
     *
     * @param suggestion 维护建议
     * @return 影响行数
     */
    int insert(MaintenanceSuggestion suggestion);

    /**
     * 批量插入建议记录
     *
     * @param list 建议记录列表
     * @return 影响行数
     */
    int insertBatch(@Param("list") List<MaintenanceSuggestion> list);

    /**
     * 根据ID查询建议详情
     *
     * @param id 建议ID
     * @return 建议记录（含设备、健康、预测、处理人信息）
     */
    MaintenanceSuggestion findById(@Param("id") Long id);

    /**
     * 查询设备的所有建议（按日期降序）
     *
     * @param deviceId 设备ID
     * @return 建议记录列表
     */
    List<MaintenanceSuggestion> findByDevice(@Param("deviceId") Long deviceId);

    /**
     * 分页查询建议列表
     *
     * @param status         建议状态过滤（null=全部）
     * @param priorityLevel  优先级过滤（null=全部）
     * @param maintenanceType 维护类型过滤（null=全部）
     * @param deviceCode     设备编码关键词（null=全部，模糊匹配）
     * @param offset         分页偏移量
     * @param size           每页大小
     * @return 建议记录列表
     */
    List<MaintenanceSuggestion> findByPage(@Param("status") String status,
                                            @Param("priorityLevel") String priorityLevel,
                                            @Param("maintenanceType") String maintenanceType,
                                            @Param("deviceCode") String deviceCode,
                                            @Param("offset") int offset,
                                            @Param("size") int size);

    /**
     * 统计查询总记录数（配合 findByPage 使用）
     */
    long countByPage(@Param("status") String status,
                     @Param("priorityLevel") String priorityLevel,
                     @Param("maintenanceType") String maintenanceType,
                     @Param("deviceCode") String deviceCode);

    /**
     * 查询待处理建议数量
     *
     * @return 待处理建议数
     */
    long countPending();

    /**
     * 查询高优先级待处理建议列表
     *
     * @param limit 返回数量限制（0=全部）
     * @return 建议记录列表
     */
    List<MaintenanceSuggestion> findPendingHighPriority(@Param("limit") int limit);

    /**
     * 查询即将到期的建议（建议结束日期接近）
     *
     * @param daysAhead 提前天数
     * @param limit     返回数量限制
     * @return 建议记录列表
     */
    List<MaintenanceSuggestion> findUpcomingDue(@Param("daysAhead") int daysAhead,
                                                 @Param("limit") int limit);

    /**
     * 更新建议状态为已采纳
     *
     * @param id            建议ID
     * @param workorderId   关联工单ID
     * @param requisitionId 关联领用单ID
     * @param handledBy     处理人ID
     * @return 影响行数
     */
    int updateStatusToAccepted(@Param("id") Long id,
                                @Param("workorderId") Long workorderId,
                                @Param("requisitionId") Long requisitionId,
                                @Param("handledBy") Long handledBy);

    /**
     * 更新建议状态为已拒绝
     *
     * @param id           建议ID
     * @param rejectReason 拒绝原因
     * @param handledBy    处理人ID
     * @return 影响行数
     */
    int updateStatusToRejected(@Param("id") Long id,
                                @Param("rejectReason") String rejectReason,
                                @Param("handledBy") Long handledBy);

    /**
     * 更新建议状态为已完成
     *
     * @param id 建议ID
     * @return 影响行数
     */
    int updateStatusToCompleted(@Param("id") Long id);

    /**
     * 统计各状态的建议数量
     *
     * @return Map列表，格式：[{status: "PENDING", cnt: 10}, ...]
     */
    List<java.util.Map<String, Object>> countByStatus();

    /**
     * 统计各优先级的建议数量
     *
     * @return Map列表，格式：[{priorityLevel: "HIGH", cnt: 5}, ...]
     */
    List<java.util.Map<String, Object>> countByPriority();

    /**
     * 统计建议采纳率（ACCEPTED / (ACCEPTED + REJECTED)）
     *
     * @return 采纳率（0-1）
     */
    Double calcAcceptanceRate();

    /**
     * 删除指定日期之前的已完成建议（数据归档用）
     *
     * @param beforeDate 截止日期
     * @return 删除行数
     */
    int deleteCompletedBeforeDate(@Param("beforeDate") LocalDate beforeDate);
}
