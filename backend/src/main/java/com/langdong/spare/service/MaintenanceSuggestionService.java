package com.langdong.spare.service;

import com.langdong.spare.entity.*;
import com.langdong.spare.mapper.*;
import com.langdong.spare.util.MaintenanceSuggestionGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 维护建议管理服务
 *
 * 负责:
 *   1. 维护建议生成（基于健康评估和故障预测）
 *   2. 建议审批（采纳/拒绝）
 *   3. 建议查询和统计
 *   4. 自动化集成（创建领用单和工单）
 */
@Service
public class MaintenanceSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceSuggestionService.class);

    @Autowired
    private MaintenanceSuggestionMapper suggestionMapper;

    @Autowired
    private DeviceHealthMapper deviceHealthMapper;

    @Autowired
    private FaultPredictionMapper faultPredictionMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private EquipmentSparePartMapper equipmentSparePartMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ================================================================
    // 1. 维护建议生成
    // ================================================================

    /**
     * 为指定设备生成维护建议
     *
     * @param deviceIds 设备ID列表（null=所有设备）
     * @return 生成的建议数量
     */
    @Transactional
    public int generateSuggestions(List<Long> deviceIds) {
        log.info("[维护建议] 开始生成建议，设备数量={}", deviceIds != null ? deviceIds.size() : "全部");

        List<Equipment> devices;
        if (deviceIds == null || deviceIds.isEmpty()) {
            devices = equipmentMapper.findAll();
        } else {
            devices = new ArrayList<>();
            for (Long deviceId : deviceIds) {
                Equipment device = equipmentMapper.findById(deviceId);
                if (device != null) {
                    devices.add(device);
                }
            }
        }

        if (devices.isEmpty()) {
            log.warn("[维护建议] 无设备需要生成建议");
            return 0;
        }

        int generatedCount = 0;
        for (Equipment device : devices) {
            try {
                Long suggestionId = generateSingleDeviceSuggestion(device);
                if (suggestionId != null) {
                    generatedCount++;
                }
            } catch (Exception e) {
                log.error("[维护建议] 设备 {} 建议生成失败", device.getCode(), e);
            }
        }

        log.info("[维护建议] 生成完成，共生成 {} 条建议", generatedCount);
        return generatedCount;
    }

    /**
     * 为单个设备生成维护建议
     *
     * @param device 设备对象
     * @return 建议记录ID
     */
    private Long generateSingleDeviceSuggestion(Equipment device) {
        log.info("[维护建议] 开始生成设备 {} 的建议", device.getCode());

        // 1. 查询最新的健康评估记录
        DeviceHealth latestHealth = deviceHealthMapper.findLatestByDevice(device.getId());
        if (latestHealth == null) {
            log.warn("[维护建议] 设备 {} 无健康评估记录，跳过", device.getCode());
            return null;
        }

        // 2. 查询最新的故障预测记录
        FaultPrediction latestPrediction = faultPredictionMapper.findLatestByDevice(device.getId());
        if (latestPrediction == null) {
            log.warn("[维护建议] 设备 {} 无故障预测记录，跳过", device.getCode());
            return null;
        }

        // 3. 提取关键指标
        double healthScore = latestHealth.getHealthScore().doubleValue();
        String riskLevel = latestHealth.getRiskLevel();
        double failureProbability = latestPrediction.getFailureProbability().doubleValue();
        int predictedFaults = latestPrediction.getPredictedFaultCount();

        // 4. 判断是否需要生成建议
        if (!MaintenanceSuggestionGenerator.shouldGenerateSuggestion(healthScore, failureProbability)) {
            log.info("[维护建议] 设备 {} 健康状况良好，无需建议", device.getCode());
            return null;
        }

        // 5. 确定维护类型和优先级
        String maintenanceType = MaintenanceSuggestionGenerator.determineMaintenanceType(riskLevel, failureProbability);
        String priorityLevel = MaintenanceSuggestionGenerator.determinePriorityLevel(riskLevel, failureProbability);

        // 6. 计算时间窗口
        Map<String, LocalDate> window = MaintenanceSuggestionGenerator.calcMaintenanceWindow(priorityLevel, LocalDate.now());

        // 7. 生成建议原因
        String reason = MaintenanceSuggestionGenerator.generateSuggestionReason(
                riskLevel, healthScore, failureProbability, predictedFaults
        );

        // 8. 查询关联备件需求
        List<Map<String, Object>> relatedSpareParts = buildRelatedSpareParts(
                device.getId(),
                latestHealth.getRuntimeScore().doubleValue(),
                latestHealth.getFaultScore().doubleValue(),
                predictedFaults
        );

        // 9. 估算维护成本
        double sparePartsCost = calcSparePartsCost(relatedSpareParts);
        double estimatedDowntimeHours = estimateDowntimeHours(maintenanceType);
        double totalCost = MaintenanceSuggestionGenerator.estimateMaintenanceCost(
                sparePartsCost,
                maintenanceType,
                device.getImportanceLevel() != null ? device.getImportanceLevel() : "NORMAL",
                estimatedDowntimeHours
        );

        // 10. 构建建议记录
        MaintenanceSuggestion suggestion = new MaintenanceSuggestion();
        suggestion.setDeviceId(device.getId());
        suggestion.setHealthRecordId(latestHealth.getId());
        suggestion.setSuggestionDate(LocalDate.now());
        suggestion.setMaintenanceType(maintenanceType);
        suggestion.setPriorityLevel(priorityLevel);
        suggestion.setSuggestedStartDate(window.get("startDate"));
        suggestion.setSuggestedEndDate(window.get("endDate"));
        suggestion.setReason(reason);
        suggestion.setEstimatedCost(BigDecimal.valueOf(totalCost));
        suggestion.setStatus("PENDING");

        // 转换关联备件为JSON
        try {
            suggestion.setRelatedSpareParts(objectMapper.writeValueAsString(relatedSpareParts));
        } catch (Exception e) {
            log.error("[维护建议] 备件需求JSON序列化失败", e);
            suggestion.setRelatedSpareParts("[]");
        }

        // 11. 保存建议
        suggestionMapper.insert(suggestion);

        log.info("[维护建议] 设备 {} 建议生成完成，类型={}, 优先级={}, 记录ID={}",
                device.getCode(), maintenanceType, priorityLevel, suggestion.getId());

        return suggestion.getId();
    }

    /**
     * 构建关联备件需求列表
     *
     * @param deviceId        设备ID
     * @param runtimeScore    运行时长评分
     * @param faultScore      故障频次评分
     * @param predictedFaults 预期故障次数
     * @return 备件需求列表（包含备件ID、名称、数量、单价）
     */
    private List<Map<String, Object>> buildRelatedSpareParts(Long deviceId,
                                                               double runtimeScore,
                                                               double faultScore,
                                                               int predictedFaults) {
        List<Map<String, Object>> spareParts = new ArrayList<>();

        // 查询设备配套备件
        List<EquipmentSparePart> configuredParts = equipmentSparePartMapper.findByEquipmentId(deviceId);
        if (configuredParts == null || configuredParts.isEmpty()) {
            log.warn("[维护建议] 设备 ID={} 无配套备件配置", deviceId);
            return spareParts;
        }

        // 根据评分维度筛选建议备件
        List<String> suggestedCategories = MaintenanceSuggestionGenerator.suggestSparePartCategories(
                runtimeScore, faultScore, predictedFaults
        );

        // 这里简化处理：返回所有配套备件，实际应根据category过滤
        // TODO: 需要在SparePart表增加category字段，或者在EquipmentSparePart中标记用途
        for (EquipmentSparePart part : configuredParts) {
            Map<String, Object> sparePartInfo = new HashMap<>();
            sparePartInfo.put("sparePartId", part.getSparePartId());
            sparePartInfo.put("quantity", part.getQuantity());
            // TODO: 查询备件详情获取名称和单价
            // SparePart sparePartDetail = sparePartMapper.findById(part.getSparePartId());
            // sparePartInfo.put("name", sparePartDetail.getName());
            // sparePartInfo.put("unitPrice", sparePartDetail.getUnitPrice());
            spareParts.add(sparePartInfo);
        }

        return spareParts;
    }

    /**
     * 计算备件总成本
     *
     * @param relatedSpareParts 关联备件列表
     * @return 备件总成本
     */
    private double calcSparePartsCost(List<Map<String, Object>> relatedSpareParts) {
        // TODO: 实际计算应从备件详情中获取单价
        // 临时使用固定值
        return relatedSpareParts.size() * 500.0;
    }

    /**
     * 估算停机时长（根据维护类型）
     *
     * @param maintenanceType 维护类型
     * @return 预计停机时长（小时）
     */
    private double estimateDowntimeHours(String maintenanceType) {
        switch (maintenanceType) {
            case "EMERGENCY":
                return 8.0; // 紧急维护预计8小时
            case "PREDICTIVE":
                return 4.0; // 预测性维护预计4小时
            case "PREVENTIVE":
                return 2.0; // 预防性维护预计2小时
            default:
                return 2.0;
        }
    }

    // ================================================================
    // 2. 建议审批
    // ================================================================

    /**
     * 采纳维护建议（自动创建领用单和工单）
     *
     * @param suggestionId 建议ID
     * @param handledBy    处理人ID
     * @return 关联的工单ID和领用单ID
     */
    @Transactional
    public Map<String, Long> approveSuggestion(Long suggestionId, Long handledBy) {
        log.info("[维护建议] 开始采纳建议 ID={}, 处理人={}", suggestionId, handledBy);

        MaintenanceSuggestion suggestion = suggestionMapper.findById(suggestionId);
        if (suggestion == null) {
            throw new RuntimeException("建议不存在");
        }

        if (!"PENDING".equals(suggestion.getStatus())) {
            throw new RuntimeException("建议状态不是待处理，无法采纳");
        }

        // TODO: 自动创建领用单（Phase 2集成）
        // Long requisitionId = createRequisitionFromSuggestion(suggestion);

        // TODO: 自动创建维修工单（Phase 2集成）
        // Long workorderId = createWorkOrderFromSuggestion(suggestion);

        Long requisitionId = null;
        Long workorderId = null;

        // 更新建议状态为已采纳
        suggestionMapper.updateStatusToAccepted(suggestionId, workorderId, requisitionId, handledBy);

        log.info("[维护建议] 建议采纳完成，工单ID={}, 领用单ID={}", workorderId, requisitionId);

        Map<String, Long> result = new HashMap<>();
        result.put("workorderId", workorderId);
        result.put("requisitionId", requisitionId);
        return result;
    }

    /**
     * 拒绝维护建议
     *
     * @param suggestionId  建议ID
     * @param rejectReason  拒绝原因
     * @param handledBy     处理人ID
     */
    @Transactional
    public void rejectSuggestion(Long suggestionId, String rejectReason, Long handledBy) {
        log.info("[维护建议] 拒绝建议 ID={}, 原因={}", suggestionId, rejectReason);

        MaintenanceSuggestion suggestion = suggestionMapper.findById(suggestionId);
        if (suggestion == null) {
            throw new RuntimeException("建议不存在");
        }

        if (!"PENDING".equals(suggestion.getStatus())) {
            throw new RuntimeException("建议状态不是待处理，无法拒绝");
        }

        suggestionMapper.updateStatusToRejected(suggestionId, rejectReason, handledBy);

        log.info("[维护建议] 建议已拒绝");
    }

    /**
     * 标记建议为已完成
     *
     * @param suggestionId 建议ID
     */
    @Transactional
    public void completeSuggestion(Long suggestionId) {
        log.info("[维护建议] 完成建议 ID={}", suggestionId);

        MaintenanceSuggestion suggestion = suggestionMapper.findById(suggestionId);
        if (suggestion == null) {
            throw new RuntimeException("建议不存在");
        }

        if (!"ACCEPTED".equals(suggestion.getStatus())) {
            throw new RuntimeException("只有已采纳的建议才能标记为完成");
        }

        suggestionMapper.updateStatusToCompleted(suggestionId);

        log.info("[维护建议] 建议已完成");
    }

    // ================================================================
    // 3. 建议查询
    // ================================================================

    /**
     * 根据ID查询建议详情
     *
     * @param suggestionId 建议ID
     * @return 建议详情（含设备、健康、预测信息）
     */
    public MaintenanceSuggestion getSuggestionById(Long suggestionId) {
        return suggestionMapper.findById(suggestionId);
    }

    /**
     * 查询设备的所有建议
     *
     * @param deviceId 设备ID
     * @return 建议列表
     */
    public List<MaintenanceSuggestion> getSuggestionsByDevice(Long deviceId) {
        return suggestionMapper.findByDevice(deviceId);
    }

    /**
     * 分页查询建议列表
     *
     * @param status          状态过滤（null=全部）
     * @param priorityLevel   优先级过滤（null=全部）
     * @param maintenanceType 维护类型过滤（null=全部）
     * @param deviceCode      设备编码关键词（null=全部）
     * @param page            页码（从1开始）
     * @param size            每页大小
     * @return 建议列表和总数
     */
    public Map<String, Object> getSuggestionsByPage(String status,
                                                      String priorityLevel,
                                                      String maintenanceType,
                                                      String deviceCode,
                                                      int page,
                                                      int size) {
        int offset = (page - 1) * size;
        List<MaintenanceSuggestion> records = suggestionMapper.findByPage(
                status, priorityLevel, maintenanceType, deviceCode, offset, size);
        long total = suggestionMapper.countByPage(status, priorityLevel, maintenanceType, deviceCode);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 查询待处理的高优先级建议
     *
     * @param limit 返回数量限制（0=全部）
     * @return 建议列表
     */
    public List<MaintenanceSuggestion> getPendingHighPriority(int limit) {
        return suggestionMapper.findPendingHighPriority(limit);
    }

    /**
     * 查询即将到期的建议
     *
     * @param daysAhead 提前天数
     * @param limit     返回数量限制
     * @return 建议列表
     */
    public List<MaintenanceSuggestion> getUpcomingDue(int daysAhead, int limit) {
        return suggestionMapper.findUpcomingDue(daysAhead, limit);
    }

    // ================================================================
    // 4. 统计数据
    // ================================================================

    /**
     * 获取建议管理Dashboard数据
     *
     * @return Dashboard数据
     */
    public Map<String, Object> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();

        // 待处理建议数量
        long pendingCount = suggestionMapper.countPending();
        dashboard.put("pendingCount", pendingCount);

        // 状态分布
        List<Map<String, Object>> statusDistribution = suggestionMapper.countByStatus();
        Map<String, Long> statusMap = new HashMap<>();
        for (Map<String, Object> item : statusDistribution) {
            statusMap.put((String) item.get("status"), ((Number) item.get("cnt")).longValue());
        }
        dashboard.put("statusDistribution", statusMap);

        // 优先级分布（仅统计待处理）
        List<Map<String, Object>> priorityDistribution = suggestionMapper.countByPriority();
        Map<String, Long> priorityMap = new HashMap<>();
        for (Map<String, Object> item : priorityDistribution) {
            priorityMap.put((String) item.get("priorityLevel"), ((Number) item.get("cnt")).longValue());
        }
        dashboard.put("priorityDistribution", priorityMap);

        // 建议采纳率
        Double acceptanceRate = suggestionMapper.calcAcceptanceRate();
        dashboard.put("acceptanceRate", acceptanceRate != null ? acceptanceRate : 0.0);

        // 高优先级待处理建议（前10条）
        List<MaintenanceSuggestion> highPrioritySuggestions = suggestionMapper.findPendingHighPriority(10);
        dashboard.put("highPrioritySuggestions", highPrioritySuggestions);

        return dashboard;
    }

    /**
     * 统计待处理建议数量
     *
     * @return 待处理建议数
     */
    public long countPending() {
        return suggestionMapper.countPending();
    }

    // ================================================================
    // 5. 数据归档
    // ================================================================

    /**
     * 删除指定日期之前的已完成建议（数据归档用）
     *
     * @param beforeDate 截止日期
     * @return 删除行数
     */
    @Transactional
    public int archiveCompletedSuggestions(LocalDate beforeDate) {
        log.info("[维护建议] 开始归档 {} 之前的已完成建议", beforeDate);
        int deletedCount = suggestionMapper.deleteCompletedBeforeDate(beforeDate);
        log.info("[维护建议] 归档完成，删除 {} 条记录", deletedCount);
        return deletedCount;
    }
}
