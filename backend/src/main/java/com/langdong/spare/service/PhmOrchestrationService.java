package com.langdong.spare.service;

import com.langdong.spare.entity.DeviceHealth;
import com.langdong.spare.entity.FaultPrediction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * PHM编排服务（预测性健康管理 - Orchestration Layer）
 *
 * 负责：
 *   1. 定时任务编排：每天凌晨3点自动运行完整PHM流程
 *   2. 流程编排：健康评估 → 故障预测 → 维护建议生成
 *   3. 自动化集成：高优先级建议自动触发（Phase 2）
 *   4. 防重复执行控制
 *
 * PHM流程：
 *   Step 1: 批量评估所有设备的健康状况（DeviceHealthService）
 *   Step 2: 对高风险设备进行故障预测（FaultPredictionService）
 *   Step 3: 根据评估和预测结果生成维护建议（MaintenanceSuggestionService）
 *   Step 4: （可选）高优先级建议自动创建领用单和工单
 */
@Service
public class PhmOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(PhmOrchestrationService.class);

    @Autowired
    private DeviceHealthService deviceHealthService;

    @Autowired
    private FaultPredictionService faultPredictionService;

    @Autowired
    private MaintenanceSuggestionService suggestionService;

    /** 防重复执行标志 */
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /** 故障预测窗口天数（默认90天） */
    private static final int PREDICTION_WINDOW_DAYS = 90;

    /** 高风险设备故障概率阈值（用于筛选需要预测的设备） */
    private static final double HIGH_RISK_PROBABILITY_THRESHOLD = 0.5;

    // ================================================================
    // 1. 定时任务入口
    // ================================================================

    /**
     * 定时任务：每天凌晨3点自动触发完整PHM流程
     * cron 表达式：秒 分 时 日 月 周
     *
     * 执行时间选择凌晨3点的原因：
     *   - 避开白天业务高峰期
     *   - 在凌晨2点备份完成后执行
     *   - 确保最新数据已入库（工单、领用等）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledPhmFlow() {
        log.info("[PHM编排] 定时任务触发PHM每日流程（每天凌晨3点）");
        runPhmFlow();
    }

    /**
     * 异步触发完整PHM流程
     * 由定时任务调用（自动触发）或 Controller 调用（手动触发）
     * 使用 @Async 确保不阻塞调用方线程
     */
    @Async
    public void runPhmFlow() {
        // 防止重复执行
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("[PHM编排] PHM流程正在运行中，跳过本次触发");
            return;
        }

        try {
            log.info("[PHM编排] ============================================");
            log.info("[PHM编排] 开始执行PHM每日流程");
            log.info("[PHM编排] ============================================");

            long startTime = System.currentTimeMillis();

            // Step 1: 批量评估所有设备的健康状况
            int healthEvaluatedCount = executeHealthEvaluation();

            // Step 2: 对高风险设备进行故障预测
            int predictionCount = executeFaultPrediction();

            // Step 3: 生成维护建议
            int suggestionCount = executeMaintenanceSuggestion();

            // Step 4: （可选）高优先级建议自动触发
            // TODO: Phase 2 - 自动创建领用单和工单
            // executeAutoActions();

            long endTime = System.currentTimeMillis();
            long duration = (endTime - startTime) / 1000;

            log.info("[PHM编排] ============================================");
            log.info("[PHM编排] PHM每日流程执行完成");
            log.info("[PHM编排] 健康评估: {} 台设备", healthEvaluatedCount);
            log.info("[PHM编排] 故障预测: {} 台设备", predictionCount);
            log.info("[PHM编排] 维护建议: {} 条", suggestionCount);
            log.info("[PHM编排] 总耗时: {} 秒", duration);
            log.info("[PHM编排] ============================================");

        } catch (Exception e) {
            log.error("[PHM编排] PHM流程执行异常", e);
        } finally {
            // 重置运行标志
            isRunning.set(false);
        }
    }

    // ================================================================
    // 2. 核心流程步骤
    // ================================================================

    /**
     * Step 1: 批量评估所有设备的健康状况
     *
     * @return 成功评估的设备数量
     */
    private int executeHealthEvaluation() {
        log.info("[PHM编排] Step 1: 开始批量健康评估...");
        long stepStart = System.currentTimeMillis();

        try {
            int count = deviceHealthService.batchEvaluateAllDevices();
            long stepEnd = System.currentTimeMillis();

            log.info("[PHM编排] Step 1: 健康评估完成，共评估 {} 台设备，耗时 {} 秒",
                    count, (stepEnd - stepStart) / 1000);
            return count;
        } catch (Exception e) {
            log.error("[PHM编排] Step 1: 健康评估失败", e);
            return 0;
        }
    }

    /**
     * Step 2: 对高风险设备进行故障预测
     *
     * 策略：
     *   - 查询所有设备的最新健康评估记录
     *   - 筛选出风险等级为 HIGH 或 CRITICAL 的设备
     *   - 对这些设备进行故障预测（预测窗口90天）
     *
     * @return 成功预测的设备数量
     */
    private int executeFaultPrediction() {
        log.info("[PHM编排] Step 2: 开始故障预测（仅高风险设备）...");
        long stepStart = System.currentTimeMillis();

        try {
            // 查询高风险设备列表（CRITICAL 和 HIGH）
            List<DeviceHealth> criticalDevices = deviceHealthService.getRiskRanking("CRITICAL", 0);
            List<DeviceHealth> highRiskDevices = deviceHealthService.getRiskRanking("HIGH", 0);

            // 合并设备ID列表
            List<Long> riskDeviceIds = criticalDevices.stream()
                    .map(DeviceHealth::getDeviceId)
                    .collect(Collectors.toList());
            riskDeviceIds.addAll(
                    highRiskDevices.stream()
                            .map(DeviceHealth::getDeviceId)
                            .collect(Collectors.toList())
            );

            if (riskDeviceIds.isEmpty()) {
                log.info("[PHM编排] Step 2: 无高风险设备，跳过故障预测");
                return 0;
            }

            log.info("[PHM编排] Step 2: 发现 {} 台高风险设备，开始预测...", riskDeviceIds.size());

            // 批量预测
            int count = faultPredictionService.batchPredict(riskDeviceIds, PREDICTION_WINDOW_DAYS);

            long stepEnd = System.currentTimeMillis();
            log.info("[PHM编排] Step 2: 故障预测完成，共预测 {} 台设备，耗时 {} 秒",
                    count, (stepEnd - stepStart) / 1000);
            return count;
        } catch (Exception e) {
            log.error("[PHM编排] Step 2: 故障预测失败", e);
            return 0;
        }
    }

    /**
     * Step 3: 生成维护建议
     *
     * 策略：
     *   - 对所有设备生成维护建议（自动筛选需要建议的设备）
     *   - MaintenanceSuggestionService 内部会判断是否需要生成建议
     *
     * @return 生成的建议数量
     */
    private int executeMaintenanceSuggestion() {
        log.info("[PHM编排] Step 3: 开始生成维护建议...");
        long stepStart = System.currentTimeMillis();

        try {
            // 对所有设备生成建议（Service内部会筛选需要建议的设备）
            int count = suggestionService.generateSuggestions(null);

            long stepEnd = System.currentTimeMillis();
            log.info("[PHM编排] Step 3: 维护建议生成完成，共生成 {} 条建议，耗时 {} 秒",
                    count, (stepEnd - stepStart) / 1000);
            return count;
        } catch (Exception e) {
            log.error("[PHM编排] Step 3: 维护建议生成失败", e);
            return 0;
        }
    }

    /**
     * Step 4: （可选）高优先级建议自动触发
     *
     * Phase 2 功能：
     *   - 查询高优先级待处理建议
     *   - 自动创建领用单草稿
     *   - 自动创建预防性维护工单
     *
     * @return 自动触发的建议数量
     */
    private int executeAutoActions() {
        log.info("[PHM编排] Step 4: 开始自动化触发（高优先级建议）...");
        long stepStart = System.currentTimeMillis();

        try {
            // TODO: Phase 2 - 实现自动化触发逻辑
            // 1. 查询高优先级待处理建议
            // List<MaintenanceSuggestion> highPrioritySuggestions =
            //     suggestionService.getPendingHighPriority(10);
            //
            // 2. 对每个建议自动创建领用单和工单
            // for (MaintenanceSuggestion suggestion : highPrioritySuggestions) {
            //     suggestionService.approveSuggestion(suggestion.getId(), SYSTEM_USER_ID);
            // }

            long stepEnd = System.currentTimeMillis();
            log.info("[PHM编排] Step 4: 自动化触发完成，耗时 {} 秒", (stepEnd - stepStart) / 1000);
            return 0;
        } catch (Exception e) {
            log.error("[PHM编排] Step 4: 自动化触发失败", e);
            return 0;
        }
    }

    // ================================================================
    // 3. 手动触发接口（供 Controller 调用）
    // ================================================================

    /**
     * 手动触发完整PHM流程
     * 由 Controller 调用，供管理员手动触发
     */
    public void triggerManualPhmFlow() {
        log.info("[PHM编排] 手动触发PHM流程");
        runPhmFlow();
    }

    /**
     * 手动触发健康评估
     *
     * @return 评估成功的设备数量
     */
    public int triggerHealthEvaluation() {
        log.info("[PHM编排] 手动触发健康评估");
        return executeHealthEvaluation();
    }

    /**
     * 手动触发故障预测
     *
     * @return 预测成功的设备数量
     */
    public int triggerFaultPrediction() {
        log.info("[PHM编排] 手动触发故障预测");
        return executeFaultPrediction();
    }

    /**
     * 手动触发维护建议生成
     *
     * @return 生成的建议数量
     */
    public int triggerMaintenanceSuggestion() {
        log.info("[PHM编排] 手动触发维护建议生成");
        return executeMaintenanceSuggestion();
    }

    // ================================================================
    // 4. 状态查询
    // ================================================================

    /**
     * 查询PHM流程是否正在运行
     *
     * @return true表示正在运行，false表示未运行
     */
    public boolean isFlowRunning() {
        return isRunning.get();
    }
}
