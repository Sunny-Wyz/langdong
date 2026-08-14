package com.langdong.spare.forecast.evaluation;

import com.langdong.spare.dto.MonthlyConsumptionVO;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.forecast.config.ForecastThreadPoolConfig;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 真实实验按论文 3.2.3 / 3.3.3：36 件九组合、E01、2026-01～06。
 */
@Service
public class RealExperimentService {

    private static final Logger log = LoggerFactory.getLogger(RealExperimentService.class);

    private final SparePartMapper sparePartMapper;
    private final RestTemplate restTemplate;
    private final AsyncTaskExecutor forecastExecutor;

    @Value("${ai.python.base-url:http://localhost:8000}")
    private String pythonBaseUrl;

    private final AtomicReference<Map<String, Object>> status = new AtomicReference<>(idleStatus());
    private final AtomicReference<Map<String, Object>> latestResult = new AtomicReference<>(null);

    public RealExperimentService(SparePartMapper sparePartMapper,
                                 RestTemplate pythonRestTemplate,
                                 @Qualifier(ForecastThreadPoolConfig.FORECAST_EXECUTOR) AsyncTaskExecutor forecastExecutor) {
        this.sparePartMapper = sparePartMapper;
        this.restTemplate = pythonRestTemplate;
        this.forecastExecutor = forecastExecutor;
    }

    public Map<String, Object> getStatus() {
        return new LinkedHashMap<>(status.get());
    }

    public Map<String, Object> getLatestResult() {
        Map<String, Object> r = latestResult.get();
        return r == null ? Map.of("available", false) : r;
    }

    public Map<String, Object> start(int testMonths, int maxParts) {
        Map<String, Object> cur = status.get();
        if ("RUNNING".equals(String.valueOf(cur.get("status")))) {
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("accepted", false);
            resp.put("message", "已有真实实验在运行中");
            resp.put("status", cur);
            return resp;
        }
        forecastExecutor.execute(this::runInternal);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("accepted", true);
        resp.put("message", "真实实验已启动：论文口径 36 件滚动回测");
        resp.put("testMonths", 6);
        resp.put("maxParts", 36);
        resp.put("protocol", "thesis");
        return resp;
    }

    @SuppressWarnings("unchecked")
    private void runInternal() {
        long t0 = System.currentTimeMillis();
        updateStatus("RUNNING", 5, "加载月度消耗", null);
        try {
            List<MonthlyConsumptionVO> rows = sparePartMapper.findAllMonthlyConsumption("2020-01-01");
            Map<String, Map<String, Double>> demand = new HashMap<>();
            if (rows != null) {
                for (MonthlyConsumptionVO vo : rows) {
                    if (vo.getPartCode() == null || vo.getMonth() == null) {
                        continue;
                    }
                    double qty = vo.getQty() == null ? 0.0 : vo.getQty().doubleValue();
                    demand.computeIfAbsent(vo.getPartCode(), k -> new HashMap<>())
                            .merge(vo.getMonth(), qty, Double::sum);
                }
            }
            if (demand.isEmpty()) {
                fail("库中无已出库/已安装领用月度消耗，请先产生真实出库记录");
                return;
            }

            Map<String, Map<String, Object>> partMeta = new HashMap<>();
            List<SparePart> parts = sparePartMapper.findAllForClassify();
            if (parts != null) {
                for (SparePart p : parts) {
                    if (p.getCode() == null) {
                        continue;
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("leadTime", p.getLeadTime() != null ? p.getLeadTime() : 14);
                    partMeta.put(p.getCode(), m);
                }
            }

            updateStatus("RUNNING", 20, "调用 Python 滚动回测（可能数分钟）", null);

            mergeThesisLabels(partMeta);
            retainThesisCodes(demand, partMeta);
            Map<String, Object> body = new HashMap<>();
            body.put("demand", demand);
            body.put("test_months", 6);
            body.put("max_parts", 36);
            body.put("focus_code", "C0070003");
            body.put("protocol", "thesis");
            body.put("part_meta", partMeta);

            String url = pythonBaseUrl + "/api/algorithm/narrative_eval";
            Map result = restTemplate.postForObject(url, body, Map.class);
            if (result == null || !Boolean.TRUE.equals(result.get("available"))) {
                fail("Python narrative_eval 未返回 available=true");
                return;
            }

            // 兼容前端旧字段
            result.putIfAbsent("finishedAt", LocalDateTime.now().toString());
            result.put("elapsedMs", System.currentTimeMillis() - t0);
            result.put("pythonBaseUrl", pythonBaseUrl);

            // 扁平 overall 字段供旧 UI
            Object overall = result.get("overall");
            if (overall instanceof Map) {
                Map om = (Map) overall;
                if (om.get("wmapeTwoStage") == null && result.get("overallMethods") instanceof Map) {
                    Map methods = (Map) result.get("overallMethods");
                    om.put("wmapeTwoStage", methods.get("two_stage"));
                    om.put("wmapeSma3", methods.get("sma3"));
                }
            }

            latestResult.set(result);
            Object adv = result.get("advantageOverSma");
            Object w = result.get("overall") instanceof Map
                    ? ((Map) result.get("overall")).get("wmapeTwoStage") : null;
            updateStatus("SUCCESS", 100,
                    "完成：样本 " + result.get("sampleCount")
                            + "，两阶段 wMAPE=" + w
                            + "%，优于 SMA " + adv + " 个百分点",
                    null);
            log.info("[真实实验] narrative_eval 完成 elapsed={}ms advantageOverSma={}",
                    System.currentTimeMillis() - t0, adv);
        } catch (Exception e) {
            log.error("[真实实验] 失败", e);
            fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadThesisRoot() {
        try (InputStream in = new ClassPathResource("thesis/thesis_36.json").getInputStream()) {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            return om.readValue(in, Map.class);
        } catch (Exception ex) {
            log.warn("[真实实验] 加载 thesis_36.json 失败: {}", ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeThesisLabels(Map<String, Map<String, Object>> partMeta) {
        Map<String, Object> root = loadThesisRoot();
        Object labels = root.get("labels");
        if (!(labels instanceof Map)) {
            return;
        }
        Set<String> allow = thesisCodes(root);
        for (Map.Entry<?, ?> e : ((Map<?, ?>) labels).entrySet()) {
            if (!(e.getValue() instanceof Map)) {
                continue;
            }
            String code = String.valueOf(e.getKey());
            if (!allow.isEmpty() && !allow.contains(code)) {
                continue;
            }
            Map<?, ?> lab = (Map<?, ?>) e.getValue();
            Map<String, Object> m = partMeta.computeIfAbsent(code, k -> new HashMap<>());
            if (lab.get("abc") != null) {
                m.put("abc", String.valueOf(lab.get("abc")));
            }
            if (lab.get("xyz") != null) {
                m.put("xyz", String.valueOf(lab.get("xyz")));
            }
        }
        log.info("[真实实验] 已加载论文分层标签 allow={}", allow.size());
    }

    private void retainThesisCodes(Map<String, Map<String, Double>> demand,
                                   Map<String, Map<String, Object>> partMeta) {
        Set<String> allow = thesisCodes(loadThesisRoot());
        if (allow.isEmpty()) {
            return;
        }
        demand.keySet().retainAll(allow);
        partMeta.keySet().retainAll(allow);
        log.info("[真实实验] 消耗已限制为论文 36 件，实际 {} 件", demand.size());
    }

    private static Set<String> thesisCodes(Map<String, Object> root) {
        Object codes = root.get("codes");
        if (!(codes instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> allow = new LinkedHashSet<>();
        for (Object c : list) {
            if (c != null) {
                allow.add(String.valueOf(c));
            }
        }
        return allow;
    }

    private void updateStatus(String st, int percent, String message, String extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", st);
        m.put("percent", percent);
        m.put("message", message);
        m.put("updatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (extra != null) {
            m.put("extra", extra);
        }
        status.set(m);
    }

    private void fail(String msg) {
        updateStatus("FAILED", 0, msg, null);
    }

    private static Map<String, Object> idleStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "IDLE");
        m.put("percent", 0);
        m.put("message", "尚未运行真实实验");
        return m;
    }
}
