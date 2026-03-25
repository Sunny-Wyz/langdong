package com.langdong.spare.controller;

import com.langdong.spare.entity.FaultPrediction;
import com.langdong.spare.service.FaultPredictionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 故障预测分析 Controller
 *
 * 提供故障预测相关API:
 *   GET  /api/phm/prediction/device/{deviceId}      查询设备预测结果
 *   GET  /api/phm/prediction/high-risk              查询高风险设备列表
 *   POST /api/phm/prediction/predict/{deviceId}     手动触发单设备预测
 *   GET  /api/phm/prediction/history/{deviceId}     查询设备预测历史
 *   GET  /api/phm/prediction/accuracy               查询预测准确率统计
 */
@RestController
@RequestMapping("/api/phm/prediction")
public class PredictionController {

    @Autowired
    private FaultPredictionService faultPredictionService;

    // ================================================================
    // GET /api/phm/prediction/device/{deviceId} — 查询设备预测结果
    // ================================================================

    /**
     * 查询指定设备的最新预测结果
     *
     * @param deviceId 设备ID
     * @return 预测记录
     */
    @GetMapping("/device/{deviceId}")
    @PreAuthorize("hasAuthority('phm:prediction:view')")
    public ResponseEntity<Map<String, Object>> getDevicePrediction(@PathVariable Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "设备ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        FaultPrediction prediction = faultPredictionService.getLatestPrediction(deviceId);

        Map<String, Object> response = new HashMap<>();
        if (prediction == null) {
            response.put("code", 404);
            response.put("message", "未找到该设备的预测记录");
            return ResponseEntity.status(404).body(response);
        }

        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", prediction);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/prediction/high-risk — 查询高风险设备列表
    // ================================================================

    /**
     * 查询高风险设备列表（按故障概率降序）
     *
     * @param threshold 故障概率阈值(默认0.5)
     * @param limit     返回数量限制(默认20)
     * @return 高风险设备预测列表
     */
    @GetMapping("/high-risk")
    @PreAuthorize("hasAuthority('phm:prediction:view')")
    public ResponseEntity<Map<String, Object>> getHighRiskDevices(
            @RequestParam(defaultValue = "0.5") double threshold,
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (threshold < 0 || threshold > 1) {
            threshold = 0.5;
        }
        if (limit < 1 || limit > 200) {
            limit = 20;
        }

        List<FaultPrediction> highRiskDevices = faultPredictionService.getHighRiskDevices(threshold, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", highRiskDevices);
        response.put("total", highRiskDevices.size());
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // POST /api/phm/prediction/predict/{deviceId} — 手动触发单设备预测
    // ================================================================

    /**
     * 手动触发单个设备的故障预测
     * 权限: phm:prediction:predict
     *
     * @param deviceId       设备ID
     * @param predictionDays 预测窗口天数(默认90天)
     * @return 预测记录ID
     */
    @PostMapping("/predict/{deviceId}")
    @PreAuthorize("hasAuthority('phm:prediction:predict')")
    public ResponseEntity<Map<String, Object>> triggerPrediction(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "90") int predictionDays
    ) {
        if (deviceId == null || deviceId <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "设备ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        if (predictionDays < 1 || predictionDays > 365) {
            predictionDays = 90;
        }

        try {
            Long predictionId = faultPredictionService.predictSingleDevice(deviceId, predictionDays);

            Map<String, Object> response = new HashMap<>();
            if (predictionId == null) {
                response.put("code", 400);
                response.put("message", "预测失败：设备历史数据不足（需至少6个月数据）");
                return ResponseEntity.badRequest().body(response);
            }

            response.put("code", 200);
            response.put("message", "预测完成");
            response.put("predictionId", predictionId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", "预测失败，请稍后重试");
            return ResponseEntity.status(500).body(error);
        }
    }

    // ================================================================
    // GET /api/phm/prediction/history/{deviceId} — 查询设备预测历史
    // ================================================================

    /**
     * 查询指定设备的预测历史（最近N个月）
     *
     * @param deviceId 设备ID
     * @param months   月数(默认12个月)
     * @return 预测记录列表
     */
    @GetMapping("/history/{deviceId}")
    @PreAuthorize("hasAuthority('phm:prediction:view')")
    public ResponseEntity<Map<String, Object>> getPredictionHistory(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "12") int months
    ) {
        if (deviceId == null || deviceId <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "设备ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        if (months < 1 || months > 36) {
            months = 12;
        }

        List<FaultPrediction> history = faultPredictionService.getHistory(deviceId, months);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", history);
        response.put("total", history.size());
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/prediction/accuracy — 查询预测准确率统计
    // ================================================================

    /**
     * 计算历史预测的准确率统计
     *
     * @param months 统计最近N个月(默认6个月)
     * @return 准确率统计列表
     */
    @GetMapping("/accuracy")
    @PreAuthorize("hasAuthority('phm:prediction:view')")
    public ResponseEntity<Map<String, Object>> getPredictionAccuracy(
            @RequestParam(defaultValue = "6") int months
    ) {
        if (months < 1 || months > 24) {
            months = 6;
        }

        List<Map<String, Object>> accuracyStats = faultPredictionService.getPredictionAccuracy(months);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", accuracyStats);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/prediction/latest — 查询所有设备最新预测(分页)
    // ================================================================

    /**
     * 分页查询所有设备的最新预测结果
     *
     * @param deviceCode     设备编码关键词(可选)
     * @param minProbability 最小故障概率过滤(可选)
     * @param page           页码(从1开始,默认1)
     * @param pageSize       每页条数(默认20)
     * @return 分页结果
     */
    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('phm:prediction:view')")
    public ResponseEntity<Map<String, Object>> getLatestAll(
            @RequestParam(required = false) String deviceCode,
            @RequestParam(required = false) Double minProbability,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > 200) {
            pageSize = 20;
        }

        Map<String, Object> result = faultPredictionService.getLatestAllDevices(
                deviceCode, minProbability, page, pageSize);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", result.get("records"));
        response.put("total", result.get("total"));
        response.put("page", page);
        response.put("pageSize", pageSize);
        return ResponseEntity.ok(response);
    }
}
