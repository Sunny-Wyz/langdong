package com.langdong.spare.controller;

import com.langdong.spare.entity.AiWeeklyForecast;
import com.langdong.spare.mapper.AiWeeklyForecastMapper;
import com.langdong.spare.service.ai.PythonModelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 周粒度深度学习需求预测 API
 * <p>
 * GET  /api/ai/weekly/list    — 分页查询预测结果
 * GET  /api/ai/weekly/{code}  — 查询某备件未来 12 周预测
 * POST /api/python/callback/weekly — Python 回调写入结果（内部 token）
 */
@RestController
public class WeeklyForecastController {

    private final AiWeeklyForecastMapper forecastMapper;
    private final PythonModelClient pythonModelClient;

    @Value("${ai.python.callback-token:}")
    private String callbackToken;

    public WeeklyForecastController(AiWeeklyForecastMapper forecastMapper, PythonModelClient pythonModelClient) {
        this.forecastMapper = forecastMapper;
        this.pythonModelClient = pythonModelClient;
    }

    // ==================== 前端查询 ====================

    @GetMapping("/api/ai/weekly/list")
    @PreAuthorize("hasAuthority('ai:weekly:list')")
    public ResponseEntity<Map<String, Object>> listForecast(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String partCode,
            @RequestParam(required = false) String weekStart,
            @RequestParam(required = false) String algoType
    ) {
        int offset = (page - 1) * pageSize;
        List<AiWeeklyForecast> data = forecastMapper.findPage(partCode, weekStart, algoType, offset, pageSize);
        int total = forecastMapper.countPage(partCode, weekStart, algoType);
        return ok(data, total, page, pageSize);
    }

    @GetMapping("/api/ai/weekly/{partCode}")
    @PreAuthorize("hasAuthority('ai:weekly:list')")
    public ResponseEntity<Map<String, Object>> getForecastByPart(
            @PathVariable("partCode") String partCode,
            @RequestParam(defaultValue = "12") int weeks
    ) {
        List<AiWeeklyForecast> data = forecastMapper.findByPartCode(partCode, weeks);
        return ok(data, data.size(), 1, weeks);
    }

    @PostMapping("/api/ai/weekly/train")
    @PreAuthorize("hasAuthority('ai:weekly:list')")
    public ResponseEntity<Map<String, Object>> triggerWeeklyTraining(
            @RequestBody(required = false) Map<String, Object> body
    ) {
        try {
            Map<String, Object> request = body == null ? new HashMap<>() : new HashMap<>(body);
            request.putIfAbsent("use_synthetic", true);
            return ResponseEntity.ok(pythonModelClient.submitWeeklyTraining(request));
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(error(ex.getStatusCode().value(), weeklyUpstreamMessage(ex)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(502, "AI服务未连接，请检查 Python 服务"));
        }
    }

    @GetMapping("/api/ai/weekly/train/status")
    @PreAuthorize("hasAuthority('ai:weekly:list')")
    public ResponseEntity<Map<String, Object>> getWeeklyTrainingStatus() {
        try {
            return ResponseEntity.ok(pythonModelClient.queryWeeklyTrainingStatus());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(error(ex.getStatusCode().value(), weeklyUpstreamMessage(ex)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(502, "AI服务未连接，请检查 Python 服务"));
        }
    }

    @PostMapping("/api/ai/weekly/predict")
    @PreAuthorize("hasAuthority('ai:weekly:list')")
    public ResponseEntity<Map<String, Object>> triggerWeeklyPredict(
            @RequestBody(required = false) Map<String, Object> body
    ) {
        try {
            return ResponseEntity.ok(pythonModelClient.triggerWeeklyPredict(body == null ? new HashMap<>() : body));
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(error(ex.getStatusCode().value(), weeklyUpstreamMessage(ex)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(502, "AI服务未连接，请检查 Python 服务"));
        }
    }

    // ==================== Python 回调接收 ====================

    @PostMapping("/api/python/callback/weekly")
    public ResponseEntity<Map<String, Object>> receiveWeeklyForecast(
            @RequestHeader(value = "X-Callback-Token", required = false) String token,
            @RequestBody Map<String, Object> payload
    ) {
        if (token == null || !token.equals(callbackToken)) {
            return ResponseEntity.status(401).body(error(401, "Unauthorized"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("forecasts");
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body(error(400, "forecasts 列表不能为空"));
        }

        List<AiWeeklyForecast> entities = items.stream().map(this::mapToEntity).toList();
        int inserted = forecastMapper.insertBatch(entities);

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "写入成功");
        resp.put("inserted", inserted);
        return ResponseEntity.ok(resp);
    }

    // ==================== 工具方法 ====================

    private AiWeeklyForecast mapToEntity(Map<String, Object> m) {
        AiWeeklyForecast e = new AiWeeklyForecast();
        e.setPartCode((String) m.get("part_code"));
        e.setWeekStart(LocalDate.parse((String) m.get("week_start")));
        e.setPredictQty(toBD(m.get("predict_qty")));
        e.setP10(toBD(m.get("p10")));
        e.setP25(toBD(m.get("p25")));
        e.setP75(toBD(m.get("p75")));
        e.setP90(toBD(m.get("p90")));
        e.setDistMu(toBD(m.get("dist_mu")));
        e.setDistSigma(toBD(m.get("dist_sigma")));
        e.setAlgoType(m.getOrDefault("algo_type", "TFT").toString());
        e.setModelVersion(m.getOrDefault("model_version", "2.0.0").toString());
        e.setAdi(toBD(m.get("adi")));
        e.setCv2(toBD(m.get("cv2")));
        return e;
    }

    private BigDecimal toBD(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }

    private ResponseEntity<Map<String, Object>> ok(Object data, int total, int page, int pageSize) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", data);
        resp.put("total", total);
        resp.put("page", page);
        resp.put("pageSize", pageSize);
        return ResponseEntity.ok(resp);
    }

    private Map<String, Object> error(int code, String msg) {
        Map<String, Object> r = new HashMap<>();
        r.put("code", code);
        r.put("message", msg);
        return r;
    }

    private String weeklyUpstreamMessage(HttpStatusCodeException ex) {
        if (ex.getStatusCode().value() == 409) {
            return "已有训练正在进行，请稍后再试";
        }
        if (ex.getStatusCode().value() == 503) {
            return "模型尚未训练，请先完成训练";
        }
        if (ex.getStatusCode().is4xxClientError()) {
            return "训练请求参数不正确";
        }
        return "AI服务暂时不可用，请稍后重试";
    }
}
