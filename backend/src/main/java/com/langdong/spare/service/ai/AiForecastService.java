package com.langdong.spare.service.ai;

import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.forecast.util.ForecastTargetMonths;
import com.langdong.spare.mapper.AiForecastResultMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 需求预测查询与异步回调落库服务。
 *
 * <p>月度训练/预测主链路已统一到两阶段 Hurdle-Gamma
 * （{@code MonthlyForecastScheduler} + {@code StockThresholdService}）。
 * 本类不再承载 RF/SBA 全量重算。</p>
 */
@Service
public class AiForecastService {

    private static final Logger log = LoggerFactory.getLogger(AiForecastService.class);

    private final AiForecastResultMapper aiForecastResultMapper;
    private final SparePartMapper sparePartMapper;

    public AiForecastService(AiForecastResultMapper aiForecastResultMapper,
                             SparePartMapper sparePartMapper) {
        this.aiForecastResultMapper = aiForecastResultMapper;
        this.sparePartMapper = sparePartMapper;
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

        String defaultMonth = ForecastTargetMonths.defaultTargetMonth();

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
        log.info("[AI预测] 已应用异步任务覆盖结果: months={}, count={}（库存阈值请走两阶段 Hurdle-Gamma 重算）",
                monthPartCodes.keySet(), mapped.size());
        return mapped.size();
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
        if ("RF".equals(normalized) || "SBA".equals(normalized) || "FALLBACK".equals(normalized)
                || "TWO_STAGE".equals(normalized) || "HURDLE_GAMMA".equals(normalized)) {
            return "HURDLE_GAMMA".equals(normalized) ? "TWO_STAGE" : normalized;
        }
        String method = predictedDemand == null ? null : asText(predictedDemand.get("method"));
        if (method == null) {
            return "TWO_STAGE";
        }
        String m = method.trim().toLowerCase(Locale.ROOT);
        if (m.contains("hurdle") || m.contains("gamma") || m.contains("two")) {
            return "TWO_STAGE";
        }
        if (m.contains("sba")) {
            return "SBA";
        }
        if (m.contains("rf") || m.contains("forest") || m.contains("lstm")) {
            return "RF";
        }
        return "TWO_STAGE";
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
}
