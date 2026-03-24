package com.langdong.spare.service;

import com.langdong.spare.entity.DeviceHealth;
import com.langdong.spare.entity.Equipment;
import com.langdong.spare.entity.HealthConfig;
import com.langdong.spare.mapper.DeviceHealthMapper;
import com.langdong.spare.mapper.EquipmentMapper;
import com.langdong.spare.mapper.HealthConfigMapper;
import com.langdong.spare.mapper.AiDeviceFeatureMapper;
import com.langdong.spare.util.DeviceHealthCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 设备健康评估服务
 *
 * 负责：
 *   1. 单设备健康评估
 *   2. 批量设备健康评估
 *   3. 健康趋势查询
 *   4. 风险设备排行榜
 *   5. 统计和Dashboard数据
 */
@Service
public class DeviceHealthService {

    private static final Logger log = LoggerFactory.getLogger(DeviceHealthService.class);

    @Autowired
    private DeviceHealthMapper deviceHealthMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private HealthConfigMapper healthConfigMapper;

    @Autowired
    private AiDeviceFeatureMapper aiDeviceFeatureMapper;

    // ================================================================
    // 1. 单设备健康评估
    // ================================================================

    /**
     * 评估单个设备的健康状况
     *
     * @param deviceId 设备ID
     * @return 评估记录ID
     */
    @Transactional
    public Long evaluateSingleDevice(Long deviceId) {
        log.info("[健康评估] 开始评估设备 ID={}", deviceId);

        Equipment equipment = equipmentMapper.findById(deviceId);
        if (equipment == null) {
            log.warn("[健康评估] 设备不存在 ID={}", deviceId);
            throw new RuntimeException("设备不存在");
        }

        // 获取配置
        HealthConfig config = getHealthConfig(null, equipment.getImportanceLevel());

        // 查询最近3个月的设备特征数据
        // TODO: 需要在AiDeviceFeatureMapper中添加查询最近N个月数据的方法
        // 临时使用空列表，等待AiDeviceFeatureMapper扩展
        List<Map<String, Object>> recentFeatures = new ArrayList<>();

        if (recentFeatures.isEmpty()) {
            log.warn("[健康评估] 设备 {} 无历史特征数据，跳过评估", equipment.getCode());
            return null;
        }

        // 计算各维度评分
        List<Double> runtimeScores = new ArrayList<>();
        List<Double> faultScores = new ArrayList<>();
        List<Double> workorderScores = new ArrayList<>();
        List<Double> replacementScores = new ArrayList<>();

        for (Map<String, Object> feature : recentFeatures) {
            Double runHours = (Double) feature.get("run_hours");
            Double mtbf = (Double) feature.get("mtbf");
            Integer workOrderCount = (Integer) feature.get("work_order_count");
            Integer partReplaceQty = (Integer) feature.get("part_replace_qty");

            // 计算各维度评分
            double runtimeScore = DeviceHealthCalculator.calcRuntimeScore(
                    runHours != null ? runHours : 0.0,
                    720.0  // 标准运行时长（月度30天×24小时）
            );
            double faultScore = DeviceHealthCalculator.calcFaultScore(mtbf);
            double workorderScore = DeviceHealthCalculator.calcWorkorderScore(
                    workOrderCount != null ? workOrderCount : 0
            );
            double replacementScore = DeviceHealthCalculator.calcReplacementScore(
                    partReplaceQty != null ? partReplaceQty : 0
            );

            runtimeScores.add(runtimeScore);
            faultScores.add(faultScore);
            workorderScores.add(workorderScore);
            replacementScores.add(replacementScore);
        }

        // 计算综合健康评分（使用时间衰减）
        List<Double> monthlyHealthScores = new ArrayList<>();
        for (int i = 0; i < runtimeScores.size(); i++) {
            // DeviceHealthCalculator使用固定权重（25%/35%/20%/20%）
            double monthScore = DeviceHealthCalculator.calcHealthScore(
                    runtimeScores.get(i),
                    faultScores.get(i),
                    workorderScores.get(i),
                    replacementScores.get(i)
            );
            monthlyHealthScores.add(monthScore);
        }

        // 应用时间衰减得到最终健康分
        double finalHealthScore = DeviceHealthCalculator.calcHealthScoreWithDecay(monthlyHealthScores);

        // 确定风险等级
        String riskLevel = DeviceHealthCalculator.determineRiskLevel(
                finalHealthScore,
                config.getCriticalThreshold().doubleValue(),
                config.getHighThreshold().doubleValue(),
                config.getMediumThreshold().doubleValue()
        );

        // 构建健康记录
        DeviceHealth record = new DeviceHealth();
        record.setDeviceId(deviceId);
        record.setRecordDate(LocalDate.now());
        record.setHealthScore(BigDecimal.valueOf(DeviceHealthCalculator.round(finalHealthScore, 2)));
        record.setRiskLevel(riskLevel);

        // 保存最近一个月的各维度评分（索引0为最近月）
        if (!runtimeScores.isEmpty()) {
            record.setRuntimeScore(BigDecimal.valueOf(DeviceHealthCalculator.round(runtimeScores.get(0), 2)));
            record.setFaultScore(BigDecimal.valueOf(DeviceHealthCalculator.round(faultScores.get(0), 2)));
            record.setWorkorderScore(BigDecimal.valueOf(DeviceHealthCalculator.round(workorderScores.get(0), 2)));
            record.setReplacementScore(BigDecimal.valueOf(DeviceHealthCalculator.round(replacementScores.get(0), 2)));
        }

        record.setAlgorithmVersion("DeviceHealthCalculator_v1.0");

        // 插入数据库
        deviceHealthMapper.insert(record);

        log.info("[健康评估] 设备 {} 评估完成，健康分={}, 风险等级={}, 记录ID={}",
                equipment.getCode(), finalHealthScore, riskLevel, record.getId());

        return record.getId();
    }

    // ================================================================
    // 2. 批量设备健康评估
    // ================================================================

    /**
     * 批量评估所有设备的健康状况
     *
     * @return 评估成功的设备数量
     */
    @Transactional
    public int batchEvaluateAllDevices() {
        log.info("[健康评估] 开始批量评估所有设备...");

        // 查询所有设备
        List<Equipment> allDevices = equipmentMapper.findAll();
        if (allDevices == null || allDevices.isEmpty()) {
            log.warn("[健康评估] 设备档案为空，跳过评估");
            return 0;
        }

        int successCount = 0;
        for (Equipment device : allDevices) {
            try {
                Long recordId = evaluateSingleDevice(device.getId());
                if (recordId != null) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("[健康评估] 设备 {} 评估失败", device.getCode(), e);
            }
        }

        log.info("[健康评估] 批量评估完成，共评估 {}/{} 台设备", successCount, allDevices.size());
        return successCount;
    }

    // ================================================================
    // 3. 健康趋势查询
    // ================================================================

    /**
     * 查询设备最新健康评估记录
     *
     * @param deviceId 设备ID
     * @return 健康记录
     */
    public DeviceHealth getLatestByDevice(Long deviceId) {
        return deviceHealthMapper.findLatestByDevice(deviceId);
    }

    /**
     * 查询设备健康趋势（最近N天）
     *
     * @param deviceId 设备ID
     * @param days     天数
     * @return 健康记录列表
     */
    public List<DeviceHealth> getHealthTrend(Long deviceId, int days) {
        return deviceHealthMapper.findTrendByDevice(deviceId, days);
    }

    /**
     * 查询设备在日期范围内的健康记录
     *
     * @param deviceId  设备ID
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 健康记录列表
     */
    public List<DeviceHealth> getHealthByDateRange(Long deviceId, LocalDate startDate, LocalDate endDate) {
        return deviceHealthMapper.findByDeviceAndDateRange(deviceId, startDate, endDate);
    }

    // ================================================================
    // 4. 风险设备排行榜
    // ================================================================

    /**
     * 查询风险设备排行榜（按健康分升序）
     *
     * @param riskLevel 风险等级（CRITICAL/HIGH/MEDIUM/LOW，null=全部）
     * @param limit     返回数量限制（0=全部）
     * @return 设备健康记录列表
     */
    public List<DeviceHealth> getRiskRanking(String riskLevel, int limit) {
        if (riskLevel != null && !riskLevel.isEmpty()) {
            return deviceHealthMapper.findByRiskLevel(riskLevel, limit);
        } else {
            return deviceHealthMapper.findLowestHealthRanking(limit);
        }
    }

    // ================================================================
    // 5. 统计和Dashboard数据
    // ================================================================

    /**
     * 获取健康监控Dashboard数据
     *
     * @return Dashboard数据（包括设备总数、风险分布、平均健康分、最近预警）
     */
    public Map<String, Object> getDashboardData() {
        Map<String, Object> dashboard = new HashMap<>();

        // 设备总数
        long totalDevices = deviceHealthMapper.countTotalDevices();
        dashboard.put("totalDevices", totalDevices);

        // 风险等级分布
        List<Map<String, Object>> riskDistribution = deviceHealthMapper.countByRiskLevel();
        Map<String, Long> riskMap = new HashMap<>();
        for (Map<String, Object> item : riskDistribution) {
            riskMap.put((String) item.get("riskLevel"), ((Number) item.get("cnt")).longValue());
        }
        dashboard.put("riskDistribution", riskMap);

        // 平均健康评分
        Double avgHealthScore = deviceHealthMapper.calcAvgHealthScore();
        dashboard.put("avgHealthScore", avgHealthScore != null ?
                DeviceHealthCalculator.round(avgHealthScore, 2) : 0.0);

        // 最近预警设备（风险等级为CRITICAL或HIGH的前10台）
        List<DeviceHealth> criticalDevices = deviceHealthMapper.findByRiskLevel("CRITICAL", 10);
        List<DeviceHealth> highRiskDevices = deviceHealthMapper.findByRiskLevel("HIGH", 10);
        List<DeviceHealth> recentAlerts = new ArrayList<>();
        recentAlerts.addAll(criticalDevices);
        recentAlerts.addAll(highRiskDevices);
        dashboard.put("recentAlerts", recentAlerts.stream()
                .limit(10)
                .toArray());

        return dashboard;
    }

    /**
     * 分页查询所有设备最新健康状态
     *
     * @param riskLevel  风险等级过滤（null=全部）
     * @param deviceCode 设备编码关键词（null=全部）
     * @param page       页码（从1开始）
     * @param size       每页大小
     * @return 健康记录列表和总数
     */
    public Map<String, Object> getLatestAllDevices(String riskLevel, String deviceCode, int page, int size) {
        int offset = (page - 1) * size;
        List<DeviceHealth> records = deviceHealthMapper.findLatestAllDevices(riskLevel, deviceCode, offset, size);
        long total = deviceHealthMapper.countLatestAllDevices(riskLevel, deviceCode);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 获取健康配置（支持优先级匹配）
     *
     * @param deviceType      设备类型
     * @param importanceLevel 重要性等级
     * @return 健康配置
     */
    private HealthConfig getHealthConfig(String deviceType, String importanceLevel) {
        HealthConfig config = healthConfigMapper.findByDeviceTypeAndImportance(deviceType, importanceLevel);
        if (config == null) {
            // 兜底：使用全局默认配置
            config = healthConfigMapper.findGlobalDefault();
            if (config == null) {
                log.error("[健康评估] 未找到健康配置，使用硬编码默认值");
                config = createDefaultConfig();
            }
        }
        return config;
    }

    /**
     * 创建默认配置（兜底）
     */
    private HealthConfig createDefaultConfig() {
        HealthConfig config = new HealthConfig();
        config.setCriticalThreshold(BigDecimal.valueOf(40));
        config.setHighThreshold(BigDecimal.valueOf(60));
        config.setMediumThreshold(BigDecimal.valueOf(80));
        config.setRuntimeWeight(BigDecimal.valueOf(0.25));
        config.setFaultWeight(BigDecimal.valueOf(0.35));
        config.setWorkorderWeight(BigDecimal.valueOf(0.20));
        config.setReplacementWeight(BigDecimal.valueOf(0.20));
        config.setPredictionWindowDays(90);
        config.setMinHistoryMonths(6);
        return config;
    }
}
