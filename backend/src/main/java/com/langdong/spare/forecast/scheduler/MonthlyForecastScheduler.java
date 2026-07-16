package com.langdong.spare.forecast.scheduler;

import com.langdong.spare.forecast.config.ForecastThreadPoolConfig;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.forecast.service.ReplenishmentService;
import com.langdong.spare.forecast.service.StockThresholdService;
import com.langdong.spare.forecast.util.ForecastTargetMonths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 月度智能预测与补货建议触发调度器（模块 G & 前后端进度适配）。
 *
 * <p>提供定时和手动重算两阶段 Hurdle-Gamma 模型的调度，并通过线程安全的最新状态机
 * 暴露任务实时进度百分比及处理阶段，供前端进行轮询渲染。</p>
 */
@Component
public class MonthlyForecastScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyForecastScheduler.class);

    private final StockThresholdService stockThresholdService;
    private final ReplenishmentService replenishmentService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ForecastRunStatus latestRunStatus = ForecastRunStatus.idle();

    public MonthlyForecastScheduler(StockThresholdService stockThresholdService,
                                    ReplenishmentService replenishmentService) {
        this.stockThresholdService = stockThresholdService;
        this.replenishmentService = replenishmentService;
    }

    /**
     * 每月 1 号凌晨 1:00 自动触发全量预测重算与补货决策链。
     */
    @Scheduled(cron = "${forecast.scheduler.cron:0 0 1 1 * ?}")
    public void scheduleMonthlyForecast() {
        // 与手动重算/任务中心统一：预测下个月
        String targetMonth = ForecastTargetMonths.defaultTargetMonth();
        log.info("[定时任务] 月初调度两阶段 Hurdle-Gamma 重算启动，目标属期(下月): {}", targetMonth);
        triggerForecastPipeline(targetMonth);
    }

    /**
     * 异步拉起整个需求预测 + 安全库存重核 + 智能补货建议生成流水线，带状态机实时反馈。
     *
     * @param targetMonth 目标预测属期月份（yyyy-MM）
     */
    @Async(ForecastThreadPoolConfig.FORECAST_EXECUTOR)
    public void triggerForecastPipeline(String targetMonth) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[重算任务] 检测到已有重算流水线处于运行中，忽略本次触发");
            latestRunStatus = latestRunStatus.withMessage("当前已有运行中的重算任务，请勿重复触发");
            return;
        }

        String runId = "hurdle-gamma-forecast-" + System.currentTimeMillis();
        long start = System.currentTimeMillis();
        latestRunStatus = ForecastRunStatus.running(runId, targetMonth, "FEATURE_BUILD", 0, 0, 0,
                "正在加载特征工程并构建预测上下文");

        try {
            log.info("[重算任务] [异步流水线] 开始计算: 月份={}, 线程={}", targetMonth, Thread.currentThread().getName());

            // 1. 跑 ABC×XYZ 分类 -> 两阶段 Hurdle-Gamma 训练 -> 蒙特卡洛安全库存计算（传递进度监听器）
            List<ForecastResult> forecasts = stockThresholdService.executeForecastAndStockThreshold(
                    targetMonth,
                    update -> {
                        String currentStage = update.processed > (update.total / 2) ? "SIMULATING" : "TRAINING";
                        String currentMessage = "SIMULATING".equals(currentStage)
                                ? "正在通过蒙特卡洛仿真计算提前期安全库存水位: " + update.processed + "/" + update.total
                                : "正在执行分类与两阶段 Hurdle-Gamma 模型拟合: " + update.processed + "/" + update.total;

                        latestRunStatus = ForecastRunStatus.running(
                                runId, targetMonth, currentStage, update.total, update.processed, update.failed, currentMessage
                        );
                    }
            );

            // 2. 比对库存生成补货建议（事务落库）
            latestRunStatus = ForecastRunStatus.running(runId, targetMonth, "REPLENISHING", forecasts.size(), forecasts.size(), 0,
                    "正在比对当前可用库存并生成智能采购补货建议...");
            replenishmentService.generateReplenishmentSuggestions(targetMonth, forecasts);

            // 3. 标识成功
            latestRunStatus = ForecastRunStatus.success(runId, targetMonth, forecasts.size(), forecasts.size(), 0,
                    "两阶段 Hurdle-Gamma 预测重算与安全库存分析任务已完成！");

            log.info("[重算任务] [异步流水线] 运行成功！目标月份: {}, 总耗时: {} ms",
                    targetMonth, (System.currentTimeMillis() - start));
        } catch (Exception e) {
            log.error("[重算任务] [异步流水线] 运行出现严重异常！目标月份: {}, 原因: {}", targetMonth, e.getMessage(), e);
            latestRunStatus = ForecastRunStatus.failed(runId, targetMonth, latestRunStatus.total,
                    latestRunStatus.processed, latestRunStatus.failed, "任务异常终止: " + e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /**
     * 获取最新运行状态的 Map 表达，提供给 Controller 转化为 JSON 返回。
     */
    public Map<String, Object> getRunStatus() {
        return latestRunStatus.toMap();
    }

    /**
     * 前后端适配的重算任务运行状态 model。
     */
    public static final class ForecastRunStatus {
        private final String runId;
        private final String status;
        private final String stage;
        private final String targetMonth;
        private final int total;
        private final int processed;
        private final int failed;
        private final String message;

        private ForecastRunStatus(String runId, String status, String stage, String targetMonth,
                                  int total, int processed, int failed, String message) {
            this.runId = runId;
            this.status = status;
            this.stage = stage;
            this.targetMonth = targetMonth;
            this.total = total;
            this.processed = processed;
            this.failed = failed;
            this.message = message;
        }

        static ForecastRunStatus idle() {
            return new ForecastRunStatus(null, "IDLE", "NONE", null, 0, 0, 0, "暂无运行中的重算任务");
        }

        static ForecastRunStatus running(String runId, String targetMonth, String stage,
                                         int total, int processed, int failed, String message) {
            return new ForecastRunStatus(runId, "RUNNING", stage, targetMonth, total, processed, failed, message);
        }

        static ForecastRunStatus success(String runId, String targetMonth,
                                         int total, int processed, int failed, String message) {
            return new ForecastRunStatus(runId, "SUCCESS", "DONE", targetMonth, total, processed, failed, message);
        }

        static ForecastRunStatus failed(String runId, String targetMonth,
                                        int total, int processed, int failed, String message) {
            return new ForecastRunStatus(runId, "FAILED", "ERROR", targetMonth, total, processed, failed, message);
        }

        ForecastRunStatus withMessage(String message) {
            return new ForecastRunStatus(runId, status, stage, targetMonth, total, processed, failed, message);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("runId", runId);
            map.put("status", status);
            map.put("stage", stage);
            map.put("targetMonth", targetMonth);
            map.put("total", total);
            map.put("processed", processed);
            map.put("failed", failed);
            map.put("message", message);

            int percent;
            if ("SUCCESS".equals(status)) {
                percent = 100;
            } else if (total > 0) {
                percent = Math.min(99, (int) Math.floor((processed * 100.0) / total));
            } else {
                percent = "RUNNING".equals(status) ? 1 : 0;
            }
            map.put("percent", percent);
            return map;
        }
    }
}
