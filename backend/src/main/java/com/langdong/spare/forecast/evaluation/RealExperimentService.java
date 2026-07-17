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
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 真实实验：拉取库内月度消耗，调用 Python narrative_eval 产出论文叙事结果。
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
        int tm = Math.max(1, Math.min(testMonths, 12));
        // maxParts 保留兼容；叙事评估内部固定 9×4=36
        forecastExecutor.execute(() -> runInternal(tm));
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("accepted", true);
        resp.put("message", "真实实验已启动（论文叙事：多基线+分层+库存+消融）");
        resp.put("testMonths", tm);
        resp.put("maxParts", 36);
        return resp;
    }

    @SuppressWarnings("unchecked")
    private void runInternal(int testMonths) {
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
                fail("库中无领用月度消耗，请先执行 sql/seed_paper_repro_consumption.py");
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

            String focus = readFocusCode();
            mergeSeedLabels(partMeta, focus);
            updateStatus("RUNNING", 20, "调用 Python 多基线叙事回测（可能数分钟）", null);

            Map<String, Object> body = new HashMap<>();
            body.put("demand", demand);
            body.put("test_months", testMonths);
            if (focus != null && !focus.isBlank()) {
                body.put("focus_code", focus.trim());
            }
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

    private String readFocusCode() {
        try {
            Path p = resolveSeedPath(".paper_focus_part");
            if (p != null && Files.exists(p)) {
                return Files.readString(p).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void mergeSeedLabels(Map<String, Map<String, Object>> partMeta, String focus) {
        try {
            Path p = resolveSeedPath(".paper_part_labels.json");
            if (p == null || !Files.exists(p)) {
                return;
            }
            String json = Files.readString(p);
            // 轻量解析：用 Jackson 若可用；否则跳过
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> root = om.readValue(json, Map.class);
            Object labels = root.get("labels");
            if (!(labels instanceof Map)) {
                return;
            }
            for (Map.Entry<?, ?> e : ((Map<?, ?>) labels).entrySet()) {
                String code = String.valueOf(e.getKey());
                if (!(e.getValue() instanceof Map)) {
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
            if (focus != null) {
                partMeta.computeIfAbsent(focus, k -> new HashMap<>());
            }
            log.info("[真实实验] 已加载种子分层标签 {} 个", ((Map<?, ?>) labels).size());
        } catch (Exception ex) {
            log.warn("[真实实验] 读取分层标签失败: {}", ex.getMessage());
        }
    }

    private Path resolveSeedPath(String name) {
        Path p = Path.of("sql", name);
        if (Files.exists(p)) {
            return p;
        }
        p = Path.of("/Users/weiyaozhou/Documents/langdong/sql", name);
        return Files.exists(p) ? p : null;
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
