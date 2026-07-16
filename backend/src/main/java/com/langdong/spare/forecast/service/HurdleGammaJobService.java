package com.langdong.spare.forecast.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langdong.spare.entity.AiTaskResult;
import com.langdong.spare.entity.ReorderSuggest;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.forecast.config.ForecastThreadPoolConfig;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.forecast.util.ForecastTargetMonths;
import com.langdong.spare.mapper.AiTaskResultMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 任务中心：两阶段 Hurdle-Gamma 异步补货任务。
 *
 * <p>替代旧 Celery + smart_replenishment(LSTM/TensorFlow) 链路，
 * 直接复用 {@link StockThresholdService} + {@link ReplenishmentService}。</p>
 */
@Service
public class HurdleGammaJobService {

    private static final Logger log = LoggerFactory.getLogger(HurdleGammaJobService.class);
    private static final int MAX_JOBS = 200;

    private final StockThresholdService stockThresholdService;
    private final ReplenishmentService replenishmentService;
    private final SparePartMapper sparePartMapper;
    private final AiTaskResultMapper aiTaskResultMapper;
    private final ObjectMapper objectMapper;
    private final AsyncTaskExecutor forecastExecutor;

    private final ConcurrentHashMap<String, JobRecord> jobs = new ConcurrentHashMap<>();

    public HurdleGammaJobService(StockThresholdService stockThresholdService,
                                 ReplenishmentService replenishmentService,
                                 SparePartMapper sparePartMapper,
                                 AiTaskResultMapper aiTaskResultMapper,
                                 ObjectMapper objectMapper,
                                 @Qualifier(ForecastThreadPoolConfig.FORECAST_EXECUTOR) AsyncTaskExecutor forecastExecutor) {
        this.stockThresholdService = stockThresholdService;
        this.replenishmentService = replenishmentService;
        this.sparePartMapper = sparePartMapper;
        this.aiTaskResultMapper = aiTaskResultMapper;
        this.objectMapper = objectMapper;
        this.forecastExecutor = forecastExecutor;
    }

    /**
     * 提交异步任务，立即返回 task_id / PENDING。
     */
    public Map<String, Object> submit(List<Integer> sparePartIds) {
        if (sparePartIds == null || sparePartIds.isEmpty()) {
            throw new IllegalArgumentException("spare_part_ids is required");
        }

        String taskId = "hg-" + UUID.randomUUID().toString().replace("-", "");
        String targetMonth = ForecastTargetMonths.defaultTargetMonth();

        JobRecord record = JobRecord.pending(taskId, targetMonth, sparePartIds);
        jobs.put(taskId, record);
        persist(record);
        trimJobHistory();

        List<Integer> idsCopy = new ArrayList<>(sparePartIds);
        forecastExecutor.execute(() -> executeJob(taskId, idsCopy, targetMonth));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("task_id", taskId);
        resp.put("status", "PENDING");
        resp.put("algo", "TWO_STAGE");
        resp.put("algo_name", "两阶段 Hurdle-Gamma");
        resp.put("target_month", targetMonth);
        return resp;
    }

    public Map<String, Object> getStatus(String taskId) {
        JobRecord record = jobs.get(taskId);
        if (record != null) {
            return record.toResponse();
        }
        return loadFromDb(taskId);
    }

    private Map<String, Object> loadFromDb(String taskId) {
        AiTaskResult stored = aiTaskResultMapper.findByTaskId(taskId);
        if (stored == null) {
            return null;
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("task_id", stored.getTaskId());
        resp.put("status", stored.getStatus());
        resp.put("algo", "TWO_STAGE");
        resp.put("algo_name", "两阶段 Hurdle-Gamma");
        if (stored.getErrorMsg() != null) {
            resp.put("error", stored.getErrorMsg());
        }
        if (stored.getPayload() != null && !stored.getPayload().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(stored.getPayload(), Map.class);
                resp.put("payload", payload);
                if (payload.get("target_month") != null) {
                    resp.put("target_month", payload.get("target_month"));
                }
                if (payload.get("message") != null) {
                    resp.put("message", payload.get("message"));
                }
            } catch (Exception ex) {
                resp.put("payload", Map.of("raw", stored.getPayload()));
            }
        }
        return resp;
    }

    private void persist(JobRecord record) {
        try {
            AiTaskResult row = new AiTaskResult();
            row.setTaskId(record.taskId);
            row.setStatus(record.status);
            Map<String, Object> body = record.toResponse();
            row.setPayload(objectMapper.writeValueAsString(body));
            row.setErrorMsg(record.error);
            aiTaskResultMapper.insertOrUpdate(row);
        } catch (Exception ex) {
            log.warn("[任务中心] 任务状态落库失败 taskId={}: {}", record.taskId, ex.getMessage());
        }
    }

    private void executeJob(String taskId, List<Integer> sparePartIds, String targetMonth) {
        JobRecord record = jobs.get(taskId);
        if (record == null) {
            return;
        }
        record.markStarted("正在解析备件并启动两阶段 Hurdle-Gamma 流水线");
        persist(record);

        try {
            Map<Integer, SparePart> partsById = resolveParts(sparePartIds);
            if (partsById.isEmpty()) {
                record.markFailed("未找到有效备件（请检查 ID/编码映射）");
                persist(record);
                return;
            }

            Set<String> partCodes = new LinkedHashSet<>();
            for (SparePart part : partsById.values()) {
                if (part.getCode() != null && !part.getCode().isBlank()) {
                    partCodes.add(part.getCode().trim());
                }
            }

            record.markStarted("正在执行两阶段 Hurdle-Gamma 训练/推理与安全库存计算");
            persist(record);
            List<ForecastResult> forecasts = stockThresholdService.executeForecastAndStockThreshold(
                    targetMonth,
                    update -> {
                        record.markProgress(
                                update.stage,
                                update.message,
                                update.total,
                                update.processed,
                                update.failed
                        );
                        // 进度阶段不每次落库，避免写放大
                    },
                    partCodes
            );

            record.markStarted("正在生成补货建议");
            persist(record);
            List<ReorderSuggest> suggests = replenishmentService.generateReplenishmentSuggestions(targetMonth, forecasts);

            List<Map<String, Object>> rows = buildResultRows(partsById, forecasts, suggests, targetMonth);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("algo", "TWO_STAGE");
            payload.put("algo_name", "两阶段 Hurdle-Gamma");
            payload.put("target_month", targetMonth);
            payload.put("result", rows);
            payload.put("message", "两阶段 Hurdle-Gamma 补货任务完成，共 " + rows.size() + " 条结果");

            record.markSuccess(payload, "两阶段 Hurdle-Gamma 补货任务完成，共 " + rows.size() + " 条结果");
            persist(record);
            log.info("[任务中心] Hurdle-Gamma 任务成功 taskId={}, parts={}, results={}",
                    taskId, partCodes.size(), rows.size());
        } catch (Exception ex) {
            log.error("[任务中心] Hurdle-Gamma 任务失败 taskId={}", taskId, ex);
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (msg.length() > 400) {
                msg = msg.substring(0, 400);
            }
            record.markFailed(msg);
            persist(record);
        }
    }

    private Map<Integer, SparePart> resolveParts(List<Integer> sparePartIds) {
        Map<Integer, SparePart> map = new LinkedHashMap<>();
        for (Integer id : sparePartIds) {
            if (id == null || id <= 0) {
                continue;
            }
            SparePart part = sparePartMapper.findById(id.longValue());
            if (part != null && part.getId() != null) {
                map.put(part.getId().intValue(), part);
            }
        }
        return map;
    }

    private List<Map<String, Object>> buildResultRows(Map<Integer, SparePart> partsById,
                                                      List<ForecastResult> forecasts,
                                                      List<ReorderSuggest> suggests,
                                                      String targetMonth) {
        Map<String, ForecastResult> forecastByCode = new HashMap<>();
        if (forecasts != null) {
            for (ForecastResult fr : forecasts) {
                if (fr != null && fr.getPartCode() != null) {
                    forecastByCode.putIfAbsent(fr.getPartCode().trim(), fr);
                }
            }
        }

        Map<String, ReorderSuggest> suggestByCode = new HashMap<>();
        if (suggests != null) {
            for (ReorderSuggest s : suggests) {
                if (s != null && s.getPartCode() != null) {
                    suggestByCode.putIfAbsent(s.getPartCode().trim(), s);
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Integer, SparePart> entry : partsById.entrySet()) {
            SparePart part = entry.getValue();
            String code = part.getCode() == null ? "" : part.getCode().trim();
            ForecastResult fr = forecastByCode.get(code);
            ReorderSuggest suggest = suggestByCode.get(code);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("spare_part_id", entry.getKey());
            row.put("spare_part_code", code);
            row.put("spare_part_name", part.getName());
            row.put("algo_type", "TWO_STAGE");
            row.put("algo_name", "两阶段 Hurdle-Gamma");
            row.put("forecast_month", targetMonth);

            if (fr == null) {
                row.put("error", "未生成预测结果");
                row.put("alert_message", "未生成预测结果");
                row.put("priority", "LOW");
                rows.add(row);
                continue;
            }

            if (fr.isDataInsufficient()) {
                row.put("error", fr.getNote() == null ? "数据不足" : fr.getNote());
                row.put("alert_message", fr.getNote() == null ? "历史数据不足，无法完成两阶段预测" : fr.getNote());
                row.put("priority", "LOW");
                rows.add(row);
                continue;
            }

            Map<String, Object> predictedDemand = new LinkedHashMap<>();
            predictedDemand.put("total", round2(fr.getDemandHat()));
            predictedDemand.put("method", "Hurdle-Gamma");
            row.put("predicted_demand", predictedDemand);
            row.put("occurrence_prob", round4(fr.getOccurrenceProb()));
            row.put("positive_qty", round2(fr.getPositiveQty()));
            row.put("lower_bound", round2(fr.getLowerBound()));
            row.put("upper_bound", round2(fr.getUpperBound()));
            row.put("safety_stock", fr.getSafetyStock());
            row.put("reorder_point", fr.getReorderPoint());

            if (suggest != null) {
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("suggested_qty", suggest.getSuggestQty());
                suggestion.put("current_stock", suggest.getCurrentStock());
                suggestion.put("reorder_point", suggest.getReorderPoint());
                suggestion.put("urgency", suggest.getUrgency());
                row.put("suggestion", suggestion);
                boolean urgent = suggest.getUrgency() != null && suggest.getUrgency().contains("紧急");
                row.put("priority", urgent ? "HIGH" : "MEDIUM");
                row.put("alert_message", String.format(Locale.ROOT,
                        "两阶段 Hurdle-Gamma：当前库存 %s ≤ ROP %s，建议采购 %s",
                        suggest.getCurrentStock(), suggest.getReorderPoint(), suggest.getSuggestQty()));
            } else {
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("suggested_qty", 0);
                suggestion.put("reorder_point", fr.getReorderPoint());
                row.put("suggestion", suggestion);
                row.put("priority", "LOW");
                row.put("alert_message", "库存高于补货点，暂无需补货（两阶段 Hurdle-Gamma）");
            }
            rows.add(row);
        }
        return rows;
    }

    private void trimJobHistory() {
        if (jobs.size() <= MAX_JOBS) {
            return;
        }
        jobs.entrySet().stream()
                .sorted((a, b) -> Long.compare(a.getValue().createdAtMs, b.getValue().createdAtMs))
                .limit(Math.max(0, jobs.size() - MAX_JOBS))
                .map(Map.Entry::getKey)
                .forEach(jobs::remove);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static final class JobRecord {
        private final String taskId;
        private final String targetMonth;
        private final List<Integer> sparePartIds;
        private final long createdAtMs;
        private volatile String status;
        private volatile String stage;
        private volatile String message;
        private volatile String error;
        private volatile Map<String, Object> payload;
        private volatile int total;
        private volatile int processed;
        private volatile int failed;

        private JobRecord(String taskId, String targetMonth, List<Integer> sparePartIds) {
            this.taskId = taskId;
            this.targetMonth = targetMonth;
            this.sparePartIds = sparePartIds;
            this.createdAtMs = System.currentTimeMillis();
            this.status = "PENDING";
            this.stage = "QUEUED";
            this.message = "任务已入队";
        }

        static JobRecord pending(String taskId, String targetMonth, List<Integer> sparePartIds) {
            return new JobRecord(taskId, targetMonth, sparePartIds);
        }

        synchronized void markStarted(String message) {
            this.status = "STARTED";
            this.stage = "RUNNING";
            this.message = message;
            this.error = null;
        }

        synchronized void markProgress(String stage, String message, int total, int processed, int failed) {
            this.status = "STARTED";
            this.stage = stage == null ? "RUNNING" : stage;
            this.message = message;
            this.total = total;
            this.processed = processed;
            this.failed = failed;
        }

        synchronized void markSuccess(Map<String, Object> payload, String message) {
            this.status = "SUCCESS";
            this.stage = "DONE";
            this.payload = payload;
            this.message = message;
            this.error = null;
        }

        synchronized void markFailed(String error) {
            this.status = "FAILURE";
            this.stage = "ERROR";
            this.error = error;
            this.message = "任务失败";
        }

        synchronized Map<String, Object> toResponse() {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("task_id", taskId);
            resp.put("status", status);
            resp.put("stage", stage);
            resp.put("message", message);
            resp.put("target_month", targetMonth);
            resp.put("spare_part_ids", sparePartIds);
            resp.put("algo", "TWO_STAGE");
            resp.put("algo_name", "两阶段 Hurdle-Gamma");
            resp.put("total", total);
            resp.put("processed", processed);
            resp.put("failed", failed);
            if (payload != null) {
                resp.put("payload", payload);
            }
            if (error != null) {
                resp.put("error", error);
            }
            return resp;
        }
    }
}
