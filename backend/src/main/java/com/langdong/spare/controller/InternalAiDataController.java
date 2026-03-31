package com.langdong.spare.controller;

import com.langdong.spare.mapper.InternalAiDataMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部 AI 数据 API
 * <p>
 * 仅供 Python AI 服务调用，不走 JWT 鉴权，使用 X-Internal-Token 验证。
 * 用于替代 Python 直连数据库的方式，解除部署耦合。
 */
@RestController
@RequestMapping("/internal/ai")
public class InternalAiDataController {

    private final InternalAiDataMapper dataMapper;

    @Value("${ai.python.internal-token:${ai.python.callback-token}}")
    private String internalToken;

    public InternalAiDataController(InternalAiDataMapper dataMapper) {
        this.dataMapper = dataMapper;
    }

    // ==================== 传感器数据 ====================

    @GetMapping("/spare-parts/{id}/sensor-data")
    public ResponseEntity<Map<String, Object>> getSensorData(
            @PathVariable("id") int sparePartId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        ResponseEntity<Map<String, Object>> authError = checkToken(token);
        if (authError != null) return authError;

        List<Map<String, Object>> data = dataMapper.findSensorData(sparePartId);
        return ok(data);
    }

    // ==================== 备件信息 ====================

    @GetMapping("/spare-parts/{id}/info")
    public ResponseEntity<Map<String, Object>> getSparePartInfo(
            @PathVariable("id") int sparePartId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        ResponseEntity<Map<String, Object>> authError = checkToken(token);
        if (authError != null) return authError;

        Map<String, Object> info = dataMapper.findSparePartInfo(sparePartId);
        if (info == null) {
            return ResponseEntity.status(404).body(error(404, "Spare part not found"));
        }
        return ok(info);
    }

    // ==================== 月度消耗 ====================

    @GetMapping("/spare-parts/{id}/consumption")
    public ResponseEntity<Map<String, Object>> getConsumptionData(
            @PathVariable("id") int sparePartId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        ResponseEntity<Map<String, Object>> authError = checkToken(token);
        if (authError != null) return authError;

        // 优先从业务表聚合
        List<Map<String, Object>> business = dataMapper.findConsumptionFromBusiness(sparePartId);
        if (business != null && !business.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("source", "business");
            result.put("data", business);
            return ok(result);
        }

        // 回退到日志表
        List<Map<String, Object>> log = dataMapper.findConsumptionLog(sparePartId);
        Map<String, Object> result = new HashMap<>();
        result.put("source", "log");
        result.put("data", log != null ? log : List.of());
        return ok(result);
    }

    // ==================== 动态月需求估算 ====================

    @GetMapping("/spare-parts/{id}/demand-estimate")
    public ResponseEntity<Map<String, Object>> getDemandEstimate(
            @PathVariable("id") int sparePartId,
            @RequestParam(value = "categoryId", required = false) Integer categoryId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        ResponseEntity<Map<String, Object>> authError = checkToken(token);
        if (authError != null) return authError;

        // 备件级别
        Double partEst = dataMapper.estimatePartMonthlyDemand(sparePartId);
        if (partEst != null && partEst > 0) {
            return ok(Map.of("estimate", Math.round(partEst * 10.0) / 10.0, "source", "PART_HISTORY"));
        }

        // 类目级别
        if (categoryId != null) {
            Double catEst = dataMapper.estimateCategoryMonthlyDemand(categoryId);
            if (catEst != null && catEst > 0) {
                return ok(Map.of("estimate", Math.round(catEst * 10.0) / 10.0, "source", "CATEGORY"));
            }
        }

        // 全局级别
        Double globalEst = dataMapper.estimateGlobalMonthlyDemand();
        if (globalEst != null && globalEst > 0) {
            return ok(Map.of("estimate", Math.round(globalEst * 10.0) / 10.0, "source", "GLOBAL"));
        }

        return ok(Map.of("estimate", 1.0, "source", "MIN_GUARD"));
    }

    // ==================== 供应商绩效 ====================

    @GetMapping("/spare-parts/{id}/supplier-performance")
    public ResponseEntity<Map<String, Object>> getSupplierPerformance(
            @PathVariable("id") int sparePartId,
            @RequestHeader(value = "X-Internal-Token", required = false) String token
    ) {
        ResponseEntity<Map<String, Object>> authError = checkToken(token);
        if (authError != null) return authError;

        List<Map<String, Object>> data = dataMapper.findSupplierPerformance(sparePartId);
        return ok(data);
    }

    // ==================== 工具方法 ====================

    private ResponseEntity<Map<String, Object>> checkToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            return ResponseEntity.status(401).body(error(401, "Unauthorized"));
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("data", data);
        return ResponseEntity.ok(resp);
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", code);
        resp.put("message", message);
        return resp;
    }
}
