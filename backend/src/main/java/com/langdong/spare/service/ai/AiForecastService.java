package com.langdong.spare.service.ai;

import com.langdong.spare.dto.PredictContextDTO;
import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.mapper.AiForecastResultMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 需求预测核心调度服务
 *
 * 负责串联：
 * 1. 特征工程 (AiFeatureService)
 * 2. 模型分配与预测 (SBA / RF)
 * 3. 结果保存入库
 * 4. 阈值与补货联动 (StockThresholdService)
 */
@Service
public class AiForecastService {

    private static final Logger log = LoggerFactory.getLogger(AiForecastService.class);
    private final AtomicBoolean forecasting = new AtomicBoolean(false);
    private final AtomicLong runSequence = new AtomicLong(0);
    private volatile ForecastRunStatus latestRunStatus = ForecastRunStatus.idle();

    @Autowired
    private AiFeatureService aiFeatureService;

    @Autowired
    private RandomForestServiceImpl randomForestService;

    @Autowired
    private SbaForecastServiceImpl sbaForecastService;

    @Autowired
    private StockThresholdService stockThresholdService;

    @Autowired
    private AiForecastResultMapper aiForecastResultMapper;

    @Autowired
    private SparePartMapper sparePartMapper;

    /**
     * 定时任务：每月 1 日凌晨 2 点运行
     * (比分类模块的 1 点晚，确保能拿到最新的 ABC 分类结果)
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void scheduledForecast() {
        log.info("[AI预测] 每月定时任务触发...");
        runFullForecast();
    }

    /**
     * 全量执行 AI 分析预测流水线（异步防止前端阻塞）
     */
    @Async
    public void runFullForecast() {
        if (!forecasting.compareAndSet(false, true)) {
            log.warn("[AI预测] 检测到已有预测任务运行中，忽略本次触发");
            latestRunStatus = latestRunStatus.withMessage("已有预测任务运行中");
            return;
        }
        log.info("[AI预测] 开始挂起全部备件的预测任务");

        // 目标月份（下个月）
        String targetMonth = LocalDate.now().plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String runId = "forecast-" + runSequence.incrementAndGet();
        latestRunStatus = ForecastRunStatus.running(runId, targetMonth, "FEATURE_BUILD", 0, 0, 0,
                "正在构建预测上下文");

        try {
            // 第一步：特征工程，组装所有备件的上下文数据
            List<PredictContextDTO> contexts = aiFeatureService.buildAllContexts(targetMonth);
            latestRunStatus = ForecastRunStatus.running(runId, targetMonth, "PREDICTING", contexts.size(), 0, 0,
                    "正在执行预测");
            if (contexts.isEmpty()) {
                log.warn("[AI预测] 没有得到任何可预测的上下文，任务结束");
                latestRunStatus = ForecastRunStatus.success(runId, targetMonth, 0, 0, 0, "无可预测备件");
                return;
            }

            // 第二步：执行分型预测
            List<AiForecastResult> results = new ArrayList<>(contexts.size());
            int processed = 0;
            int failed = 0;
            for (PredictContextDTO ctx : contexts) {
                AiForecastResult res;
                if ("SBA".equals(ctx.getAlgoType())) {
                    res = sbaForecastService.predict(ctx);
                } else if ("RF".equals(ctx.getAlgoType())) {
                    res = randomForestService.predict(ctx);
                } else {
                    res = randomForestService.buildFallbackResult(ctx);
                }
                results.add(res);
                processed++;
                if (res == null) {
                    failed++;
                }
                if (processed % 10 == 0 || processed == contexts.size()) {
                    latestRunStatus = ForecastRunStatus.running(runId, targetMonth, "PREDICTING", contexts.size(),
                            processed, failed, "正在执行预测");
                }
            }

            // 第三步：批量保存预测结果
            latestRunStatus = ForecastRunStatus.running(runId, targetMonth, "PERSISTING", contexts.size(), processed,
                    failed, "正在写入预测结果");
            if (!results.isEmpty()) {
                Set<String> partCodeSet = new LinkedHashSet<>();
                for (AiForecastResult item : results) {
                    if (item != null && item.getPartCode() != null && !item.getPartCode().trim().isEmpty()) {
                        partCodeSet.add(item.getPartCode().trim());
                    }
                }
                if (!partCodeSet.isEmpty()) {
                    int deleted = aiForecastResultMapper.deleteByMonthAndPartCodes(targetMonth,
                            new ArrayList<>(partCodeSet));
                    log.info("[AI预测] 已清理目标月份旧结果 {} 条（month={}, parts={}）", deleted, targetMonth, partCodeSet.size());
                }
                aiForecastResultMapper.insertBatch(results);
                log.info("[AI预测] 成功写入全量预测结果 {} 条", results.size());
            }

            // 第四步：触发安全库存重算与补货联动
            latestRunStatus = ForecastRunStatus.running(runId, targetMonth, "THRESHOLD", contexts.size(), processed,
                    failed, "正在重算安全库存与补货建议");
            stockThresholdService.recalcAndPush(results, contexts);
            latestRunStatus = ForecastRunStatus.success(runId, targetMonth, contexts.size(), processed, failed,
                    "预测任务完成");

        } catch (Exception e) {
            log.error("[AI预测] 核心流水线执行异常", e);
            latestRunStatus = ForecastRunStatus.failed(runId, targetMonth, latestRunStatus.total,
                    latestRunStatus.processed, latestRunStatus.failed,
                    "预测任务失败: " + e.getMessage());
        } finally {
            forecasting.set(false);
        }
    }

    public boolean isForecasting() {
        return forecasting.get();
    }

    public Map<String, Object> getRunStatus() {
        return latestRunStatus.toMap();
    }

    /**
     * 分页查询预测结果
     */
    public Map<String, Object> queryResult(String month, String partCode, int page, int size) {
        String mon = (month != null && !month.trim().isEmpty()) ? month.trim() : null;
        String code = (partCode != null && !partCode.trim().isEmpty()) ? partCode.trim() : null;
        int offset = (page - 1) * size;

        List<AiForecastResult> list = aiForecastResultMapper.findByPage(mon, code, offset, size);
        long total = aiForecastResultMapper.countByPage(mon, code);

        Map<String, List<AiForecastResult>> historyCache = new HashMap<>();
        for (AiForecastResult item : list) {
            if (item == null || item.getPartCode() == null || item.getForecastMonth() == null) {
                continue;
            }
            List<AiForecastResult> history = historyCache.computeIfAbsent(
                    item.getPartCode(),
                    this::queryHistory
            );
            item.setDemand3Months(sumNextThreeMonthsDemand(history, item.getForecastMonth()));
        }

        log.info("[AI查询] 结果: month={}, partCode={}, page={}, size={}, 实际页数据条数={}, 总条数={}",
                mon, code, page, size, list.size(), total);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }

    private BigDecimal sumNextThreeMonthsDemand(List<AiForecastResult> history, String startMonth) {
        if (history == null || history.isEmpty() || startMonth == null || startMonth.isBlank()) {
            return BigDecimal.ZERO;
        }
        YearMonth start;
        try {
            start = YearMonth.parse(startMonth);
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }

        Map<String, BigDecimal> demandByMonth = new LinkedHashMap<>();
        for (AiForecastResult row : history) {
            if (row == null || row.getForecastMonth() == null || row.getPredictQty() == null) {
                continue;
            }
            demandByMonth.merge(row.getForecastMonth(), row.getPredictQty(), BigDecimal::add);
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < 3; i++) {
            String month = start.plusMonths(i).toString();
            BigDecimal qty = demandByMonth.get(month);
            if (qty != null) {
                sum = sum.add(qty);
            }
        }
        return sum;
    }

    /**
     * 获取指定备件的历史预测
     */
    public List<AiForecastResult> queryHistory(String partCode) {
        return aiForecastResultMapper.findByPartCode(partCode);
    }

    /**
     * 将 Python 异步回调结果覆盖写入 ai_forecast_result（同月份同编码幂等覆盖）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int applyAsyncForecastResult(Object callbackResult) {
        if (!(callbackResult instanceof List<?> rawList) || rawList.isEmpty()) {
            throw new IllegalArgumentException("callback result is empty or invalid");
        }

        String defaultMonth = LocalDate.now().plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        List<AiForecastResult> mapped = new ArrayList<>();
        int rowIndex = 0;
        for (Object item : rawList) {
            rowIndex++;
            if (!(item instanceof Map<?, ?> rawMap)) {
                throw new IllegalArgumentException("invalid callback row at index " + rowIndex);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) rawMap;
            mapped.addAll(mapFromAsyncRow(row, defaultMonth));
        }

        Map<String, Set<String>> monthPartCodes = new HashMap<>();
        for (AiForecastResult item : mapped) {
            if (item.getPartCode() != null && !item.getPartCode().isBlank()) {
                String month = item.getForecastMonth();
                monthPartCodes.computeIfAbsent(month, k -> new LinkedHashSet<>()).add(item.getPartCode());
            }
        }
        if (monthPartCodes.isEmpty()) {
            throw new IllegalArgumentException("callback result has no valid partCode");
        }

        for (Map.Entry<String, Set<String>> entry : monthPartCodes.entrySet()) {
            aiForecastResultMapper.deleteByMonthAndPartCodes(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        aiForecastResultMapper.insertBatch(mapped);
        recalcThresholdsForAsyncOverwrite(mapped, monthPartCodes);
        log.info("[AI预测] 已应用异步任务覆盖结果: months={}, count={}", monthPartCodes.keySet(), mapped.size());
        return mapped.size();
    }

    private void recalcThresholdsForAsyncOverwrite(List<AiForecastResult> mapped,
                                                   Map<String, Set<String>> monthPartCodes) {
        for (Map.Entry<String, Set<String>> monthEntry : monthPartCodes.entrySet()) {
            String month = monthEntry.getKey();
            Set<String> partCodes = monthEntry.getValue();
            List<PredictContextDTO> monthContexts = aiFeatureService.buildAllContexts(month);
            Map<String, PredictContextDTO> contextByCode = new HashMap<>();
            for (PredictContextDTO ctx : monthContexts) {
                if (ctx != null && ctx.getPartCode() != null) {
                    contextByCode.putIfAbsent(ctx.getPartCode(), ctx);
                }
            }

            List<AiForecastResult> forecasts = new ArrayList<>();
            List<PredictContextDTO> contexts = new ArrayList<>();
            for (AiForecastResult result : mapped) {
                if (result == null || !month.equals(result.getForecastMonth())) {
                    continue;
                }
                String partCode = result.getPartCode();
                if (partCode == null || !partCodes.contains(partCode)) {
                    continue;
                }
                PredictContextDTO ctx = contextByCode.get(partCode);
                if (ctx == null) {
                    log.warn("[AI预测] 异步覆盖后阈值重算缺少上下文，跳过: month={}, partCode={}", month, partCode);
                    continue;
                }
                forecasts.add(result);
                contexts.add(ctx);
            }

            if (!forecasts.isEmpty()) {
                stockThresholdService.recalcAndPush(forecasts, contexts);
            }
        }
    }

    private List<AiForecastResult> mapFromAsyncRow(Map<String, Object> row, String defaultMonth) {
        String partCode = asText(row.get("part_code"));
        if (partCode == null) {
            partCode = asText(row.get("spare_part_code"));
        }
        if (partCode == null) {
            Integer sparePartId = asInteger(row.get("spare_part_id"));
            if (sparePartId != null) {
                SparePart sparePart = sparePartMapper.findById(sparePartId.longValue());
                if (sparePart != null) {
                    partCode = sparePart.getCode();
                }
            }
        }
        if (partCode == null || partCode.isBlank()) {
            throw new IllegalArgumentException("missing partCode in callback row");
        }

        Map<String, Object> predictedDemand = asMap(row.get("predicted_demand"));
        BigDecimal totalDemand = predictedDemand == null ? null : asBigDecimal(predictedDemand.get("total"));
        List<BigDecimal> monthlyDetail = predictedDemand == null
                ? Collections.emptyList()
                : asBigDecimalList(predictedDemand.get("monthly_detail"));

        BigDecimal lowerBound = asBigDecimal(row.get("lower_bound"));
        BigDecimal upperBound = asBigDecimal(row.get("upper_bound"));
        List<BigDecimal> lowerArr = Collections.emptyList();
        List<BigDecimal> upperArr = Collections.emptyList();
        if ((lowerBound == null || upperBound == null) && predictedDemand != null) {
            Map<String, Object> ci = asMap(predictedDemand.get("confidence_interval"));
            if (ci != null) {
                lowerArr = asBigDecimalList(ci.get("lower"));
                upperArr = asBigDecimalList(ci.get("upper"));
                if (lowerBound == null && !lowerArr.isEmpty()) {
                    lowerBound = lowerArr.get(0);
                }
                if (upperBound == null && !upperArr.isEmpty()) {
                    upperBound = upperArr.get(0);
                }
            }
        }

        String algoType = normalizeAlgoType(asText(row.get("algo_type")), predictedDemand);
        String forecastMonth = normalizeForecastMonth(asText(row.get("forecast_month")), defaultMonth);
        BigDecimal mase = asBigDecimal(row.get("mase"));
        String modelVersion = asText(row.get("model_version"));
        String normalizedModelVersion = modelVersion == null ? "py-async" : modelVersion;

        List<AiForecastResult> results = new ArrayList<>();
        if (!monthlyDetail.isEmpty()) {
            YearMonth baseMonth = YearMonth.parse(forecastMonth);
            for (int i = 0; i < monthlyDetail.size(); i++) {
                BigDecimal monthlyQty = monthlyDetail.get(i);
                if (monthlyQty == null) {
                    continue;
                }
                BigDecimal monthlyLower = (i < lowerArr.size() && lowerArr.get(i) != null) ? lowerArr.get(i) : lowerBound;
                BigDecimal monthlyUpper = (i < upperArr.size() && upperArr.get(i) != null) ? upperArr.get(i) : upperBound;
                if (monthlyLower == null) {
                    monthlyLower = monthlyQty;
                }
                if (monthlyUpper == null) {
                    monthlyUpper = monthlyQty;
                }

                AiForecastResult result = new AiForecastResult();
                result.setPartCode(partCode.trim());
                result.setForecastMonth(baseMonth.plusMonths(i).toString());
                result.setPredictQty(monthlyQty);
                result.setLowerBound(monthlyLower);
                result.setUpperBound(monthlyUpper);
                result.setAlgoType(algoType);
                result.setMase(mase);
                result.setModelVersion(normalizedModelVersion);
                results.add(result);
            }
            if (!results.isEmpty()) {
                return results;
            }
        }

        BigDecimal predictQty = totalDemand;
        if (predictQty == null) {
            predictQty = asBigDecimal(row.get("predict_qty"));
        }
        if (predictQty == null && !monthlyDetail.isEmpty()) {
            predictQty = monthlyDetail.get(0);
        }
        if (predictQty == null) {
            throw new IllegalArgumentException("missing predictQty in callback row");
        }
        if (lowerBound == null) {
            lowerBound = predictQty;
        }
        if (upperBound == null) {
            upperBound = predictQty;
        }

        AiForecastResult result = new AiForecastResult();
        result.setPartCode(partCode.trim());
        result.setForecastMonth(forecastMonth);
        result.setPredictQty(predictQty);
        result.setLowerBound(lowerBound);
        result.setUpperBound(upperBound);
        result.setAlgoType(algoType);
        result.setMase(mase);
        result.setModelVersion(normalizedModelVersion);
        results.add(result);
        return results;
    }

    private String normalizeForecastMonth(String candidate, String defaultMonth) {
        if (candidate != null && candidate.matches("\\d{4}-\\d{2}")) {
            try {
                return YearMonth.parse(candidate).toString();
            } catch (Exception ignored) {
                // Fall through to default month for invalid values like 2026-13.
            }
        }
        return defaultMonth;
    }

    private String normalizeAlgoType(String algoType, Map<String, Object> predictedDemand) {
        String normalized = algoType == null ? "" : algoType.trim().toUpperCase(Locale.ROOT);
        if ("RF".equals(normalized) || "SBA".equals(normalized) || "FALLBACK".equals(normalized)) {
            return normalized;
        }
        String method = predictedDemand == null ? null : asText(predictedDemand.get("method"));
        if (method == null) {
            return "FALLBACK";
        }
        String m = method.trim().toLowerCase(Locale.ROOT);
        if (m.contains("sba")) {
            return "SBA";
        }
        if (m.contains("rf") || m.contains("forest") || m.contains("lstm")) {
            return "RF";
        }
        return "FALLBACK";
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> converted = (Map<String, Object>) map;
            return converted;
        }
        return null;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer asInteger(Object value) {
        try {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null) {
                return Integer.parseInt(String.valueOf(value).trim());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private BigDecimal asBigDecimal(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof Number number) {
                return BigDecimal.valueOf(number.doubleValue());
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return null;
            }
            return new BigDecimal(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<BigDecimal> asBigDecimalList(Object value) {
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<BigDecimal> result = new ArrayList<>();
        for (Object item : list) {
            BigDecimal converted = asBigDecimal(item);
            if (converted != null) {
                result.add(converted);
            }
        }
        return result;
    }

    private static final class ForecastRunStatus {
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

        Map<String, Object> toMap() {
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
