package com.langdong.spare.service;

import com.langdong.spare.entity.AiDeviceFeature;
import com.langdong.spare.entity.Equipment;
import com.langdong.spare.entity.FaultPrediction;
import com.langdong.spare.mapper.EquipmentMapper;
import com.langdong.spare.mapper.FaultPredictionMapper;
import com.langdong.spare.mapper.AiDeviceFeatureMapper;
import com.langdong.spare.util.FaultPredictionEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 设备故障预测服务
 *
 * 负责：
 *   1. 单设备故障预测
 *   2. 批量设备故障预测
 *   3. 高风险设备排行榜
 *   4. 预测历史查询
 *   5. 预测准确率统计
 */
@Service
public class FaultPredictionService {

    private static final Logger log = LoggerFactory.getLogger(FaultPredictionService.class);

    @Autowired
    private FaultPredictionMapper faultPredictionMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private AiDeviceFeatureMapper aiDeviceFeatureMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ================================================================
    // 1. 单设备故障预测
    // ================================================================

    /**
     * 预测单个设备未来的故障概率和预期故障次数
     *
     * @param deviceId       设备ID
     * @param predictionDays 预测窗口天数（默认90天）
     * @return 预测记录ID
     */
    @Transactional
    public Long predictSingleDevice(Long deviceId, int predictionDays) {
        log.info("[故障预测] 开始预测设备 ID={}, 预测窗口={}天", deviceId, predictionDays);

        Equipment equipment = equipmentMapper.findById(deviceId);
        if (equipment == null) {
            log.warn("[故障预测] 设备不存在 ID={}", deviceId);
            throw new RuntimeException("设备不存在");
        }

        // 查询最近12个月的设备特征数据
        List<AiDeviceFeature> recentFeatures = aiDeviceFeatureMapper.findRecentMonthsByDevice(deviceId, 12);

        if (recentFeatures.isEmpty() || recentFeatures.size() < 6) {
            log.warn("[故障预测] 设备 {} 历史数据不足（需至少6个月），跳过预测", equipment.getCode());
            return null;
        }

        // 提取特征数据
        List<Double> runHours = new ArrayList<>();
        List<Integer> faultCounts = new ArrayList<>();
        List<Double> mtbfValues = new ArrayList<>();
        List<Double> healthScores = new ArrayList<>();

        for (AiDeviceFeature feature : recentFeatures) {
            // BigDecimal转Double
            Double runHour = feature.getRunHours() != null ? feature.getRunHours().doubleValue() : 0.0;
            Integer faultCount = feature.getFaultCount();
            Double mtbf = feature.getMtbf() != null ? feature.getMtbf().doubleValue() : 9999.0;
            // health_score需要联查ai_device_health表，暂时使用默认值
            Double healthScore = 100.0;

            runHours.add(runHour);
            faultCounts.add(faultCount != null ? faultCount : 0);
            mtbfValues.add(mtbf);
            healthScores.add(healthScore);
        }

        // 计算特征
        double avgRunHours = FaultPredictionEngine.calcAvgRunHours(runHours);
        double faultFrequency = FaultPredictionEngine.calcFaultFrequency(faultCounts);
        double avgMTBF = FaultPredictionEngine.calcAvgMTBF(mtbfValues);
        double deteriorationRate = FaultPredictionEngine.calcDeteriorationRate(healthScores);

        // 预测故障概率
        double failureProbability = FaultPredictionEngine.predictFailureProbability(
                avgRunHours, faultFrequency, avgMTBF, deteriorationRate
        );

        // 预测故障次数
        int expectedFaults = FaultPredictionEngine.predictExpectedFaults(faultFrequency, predictionDays);
        int faultCountLower = FaultPredictionEngine.calcConfidenceIntervalLower(expectedFaults);
        int faultCountUpper = FaultPredictionEngine.calcConfidenceIntervalUpper(expectedFaults);

        // 计算特征重要性（用于可视化）
        Map<String, Double> featureImportance = FaultPredictionEngine.calcFeatureImportance(
                avgRunHours, faultFrequency, avgMTBF, deteriorationRate
        );

        // 构建预测记录
        FaultPrediction prediction = new FaultPrediction();
        prediction.setDeviceId(deviceId);
        prediction.setPredictionDate(LocalDate.now());

        // 计算目标月份（当前月份 + predictionDays/30）
        YearMonth targetMonth = YearMonth.now().plusMonths(predictionDays / 30);
        prediction.setTargetMonth(targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")));

        prediction.setPredictedFaultCount(expectedFaults);
        prediction.setFailureProbability(BigDecimal.valueOf(FaultPredictionEngine.round(failureProbability, 4)));
        prediction.setFaultCountLower(faultCountLower);
        prediction.setFaultCountUpper(faultCountUpper);

        // 转换特征重要性为JSON
        try {
            prediction.setFeatureImportance(objectMapper.writeValueAsString(featureImportance));
        } catch (Exception e) {
            log.error("[故障预测] 特征重要性JSON序列化失败", e);
            prediction.setFeatureImportance("{}");
        }

        prediction.setModelType("LogisticRegression_v1.0");

        // 插入数据库
        faultPredictionMapper.insert(prediction);

        log.info("[故障预测] 设备 {} 预测完成，故障概率={}, 预期故障数={}, 记录ID={}",
                equipment.getCode(), failureProbability, expectedFaults, prediction.getId());

        return prediction.getId();
    }

    // ================================================================
    // 2. 批量设备故障预测
    // ================================================================

    /**
     * 批量预测多个设备的故障情况
     *
     * @param deviceIds      设备ID列表（null=所有设备）
     * @param predictionDays 预测窗口天数
     * @return 预测成功的设备数量
     */
    @Transactional
    public int batchPredict(List<Long> deviceIds, int predictionDays) {
        log.info("[故障预测] 开始批量预测，设备数量={}, 预测窗口={}天",
                deviceIds != null ? deviceIds.size() : "全部", predictionDays);

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
            log.warn("[故障预测] 无设备需要预测");
            return 0;
        }

        int successCount = 0;
        for (Equipment device : devices) {
            try {
                Long recordId = predictSingleDevice(device.getId(), predictionDays);
                if (recordId != null) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("[故障预测] 设备 {} 预测失败", device.getCode(), e);
            }
        }

        log.info("[故障预测] 批量预测完成，成功 {}/{} 台设备", successCount, devices.size());
        return successCount;
    }

    // ================================================================
    // 3. 高风险设备排行榜
    // ================================================================

    /**
     * 查询高风险设备列表（按故障概率降序）
     *
     * @param probabilityThreshold 故障概率阈值（默认0.5）
     * @param limit                返回数量限制（0=全部）
     * @return 高风险设备预测列表
     */
    public List<FaultPrediction> getHighRiskDevices(double probabilityThreshold, int limit) {
        return faultPredictionMapper.findHighRiskDevices(probabilityThreshold, limit);
    }

    /**
     * 查询设备的最新预测结果
     *
     * @param deviceId 设备ID
     * @return 预测记录（含设备和健康信息）
     */
    public FaultPrediction getLatestPrediction(Long deviceId) {
        return faultPredictionMapper.findLatestByDevice(deviceId);
    }

    /**
     * 查询指定目标月份的预测结果
     *
     * @param deviceId    设备ID
     * @param targetMonth 目标月份（yyyy-MM）
     * @return 预测记录
     */
    public FaultPrediction getPredictionByMonth(Long deviceId, String targetMonth) {
        return faultPredictionMapper.findByDeviceAndMonth(deviceId, targetMonth);
    }

    // ================================================================
    // 4. 预测历史查询
    // ================================================================

    /**
     * 查询设备的预测历史（最近N个月）
     *
     * @param deviceId 设备ID
     * @param months   月数
     * @return 预测记录列表（按目标月份升序）
     */
    public List<FaultPrediction> getHistory(Long deviceId, int months) {
        return faultPredictionMapper.findHistoryByDevice(deviceId, months);
    }

    /**
     * 分页查询所有设备的最新预测
     *
     * @param deviceCode     设备编码关键词（null=全部）
     * @param minProbability 最小故障概率过滤（null=不过滤）
     * @param page           页码（从1开始）
     * @param size           每页大小
     * @return 预测记录列表和总数
     */
    public Map<String, Object> getLatestAllDevices(String deviceCode, Double minProbability, int page, int size) {
        int offset = (page - 1) * size;
        List<FaultPrediction> records = faultPredictionMapper.findLatestAllDevices(
                deviceCode, minProbability, offset, size);
        long total = faultPredictionMapper.countLatestAllDevices(deviceCode, minProbability);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    // ================================================================
    // 5. 预测准确率统计
    // ================================================================

    /**
     * 计算历史预测的准确率（对比预测值和实际值）
     *
     * @param months 统计最近N个月
     * @return 准确率统计列表
     */
    public List<Map<String, Object>> getPredictionAccuracy(int months) {
        return faultPredictionMapper.calcPredictionAccuracy(months);
    }

    /**
     * 统计高风险设备数量
     *
     * @param probabilityThreshold 故障概率阈值
     * @return 高风险设备数量
     */
    public long countHighRiskDevices(double probabilityThreshold) {
        return faultPredictionMapper.countHighRiskDevices(probabilityThreshold);
    }

    /**
     * 统计指定月份的预测设备总数
     *
     * @param targetMonth 目标月份（yyyy-MM）
     * @return 设备数量
     */
    public long countByMonth(String targetMonth) {
        return faultPredictionMapper.countByMonth(targetMonth);
    }

    // ================================================================
    // 6. 数据归档
    // ================================================================

    /**
     * 删除指定日期之前的历史预测记录（数据归档用）
     *
     * @param beforeDate 截止日期
     * @return 删除行数
     */
    @Transactional
    public int archiveOldRecords(LocalDate beforeDate) {
        log.info("[故障预测] 开始归档 {} 之前的预测记录", beforeDate);
        int deletedCount = faultPredictionMapper.deleteBeforeDate(beforeDate);
        log.info("[故障预测] 归档完成，删除 {} 条记录", deletedCount);
        return deletedCount;
    }
}
