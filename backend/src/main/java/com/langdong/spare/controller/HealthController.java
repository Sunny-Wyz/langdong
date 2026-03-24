package com.langdong.spare.controller;

import com.langdong.spare.dto.DeviceHealthVO;
import com.langdong.spare.dto.HealthDashboardVO;
import com.langdong.spare.entity.DeviceHealth;
import com.langdong.spare.service.DeviceHealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备健康监控 Controller
 *
 * 提供设备健康评估相关API:
 *   GET  /api/phm/health/trend/{deviceId}       查询设备健康趋势
 *   GET  /api/phm/health/ranking                风险设备排行榜
 *   GET  /api/phm/health/dashboard              健康监控仪表盘
 *   POST /api/phm/health/batch-evaluate         手动触发批量评估
 */
@RestController
@RequestMapping("/api/phm/health")
@CrossOrigin(origins = "*")
public class HealthController {

    @Autowired
    private DeviceHealthService deviceHealthService;

    // ================================================================
    // GET /api/phm/health/trend/{deviceId} — 查询设备健康趋势
    // ================================================================

    /**
     * 查询指定设备的健康趋势数据
     *
     * @param deviceId  设备ID
     * @param startDate 开始日期(可选)
     * @param endDate   结束日期(可选)
     * @return 健康记录列表
     */
    @GetMapping("/trend/{deviceId}")
    public ResponseEntity<Map<String, Object>> getHealthTrend(
            @PathVariable Long deviceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        if (deviceId == null || deviceId <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "设备ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        // 如果未指定日期范围,默认查询最近3个月
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(3);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        List<DeviceHealth> trendData = deviceHealthService.getHealthByDateRange(
                deviceId, startDate, endDate);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", trendData);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/health/ranking — 风险设备排行榜
    // ================================================================

    /**
     * 查询风险设备排行榜
     *
     * @param riskLevel 风险等级过滤(HIGH/CRITICAL,可选)
     * @param limit     返回数量限制(默认20)
     * @return 风险设备列表(按健康分升序)
     */
    @GetMapping("/ranking")
    public ResponseEntity<Map<String, Object>> getRiskRanking(
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (limit < 1 || limit > 200) {
            limit = 20;
        }

        List<DeviceHealth> riskDevices = deviceHealthService.getRiskRanking(riskLevel, limit);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", riskDevices);
        response.put("total", riskDevices.size());
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/health/dashboard — 健康监控仪表盘
    // ================================================================

    /**
     * 获取健康监控仪表盘统计数据
     *
     * @return 仪表盘数据(设备总数、风险分布、平均健康分等)
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboardData = deviceHealthService.getDashboardData();

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", dashboardData);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // POST /api/phm/health/batch-evaluate — 手动触发批量评估
    // ================================================================

    /**
     * 手动触发批量健康评估
     * 异步执行,接口立即返回,不等待评估完成
     * 权限: phm:health:evaluate
     */
    @PostMapping("/batch-evaluate")
    @PreAuthorize("hasAuthority('phm:health:evaluate')")
    public ResponseEntity<Map<String, Object>> triggerBatchEvaluation() {
        // 异步触发,不阻塞
        int triggeredCount = deviceHealthService.batchEvaluateAllDevices();

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "批量评估任务已启动,共" + triggeredCount + "台设备,请稍后刷新查看最新评估结果");
        response.put("triggeredCount", triggeredCount);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/health/latest — 查询最新评估结果(分页)
    // ================================================================

    /**
     * 查询所有设备的最新健康评估结果(分页)
     *
     * @param riskLevel  风险等级过滤(可选)
     * @param deviceCode 设备编码关键词(可选,模糊匹配)
     * @param page       页码(从1开始,默认1)
     * @param pageSize   每页条数(默认20)
     * @return 分页结果
     */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> getLatestAll(
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String deviceCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > 200) {
            pageSize = 20;
        }

        Map<String, Object> result = deviceHealthService.getLatestAllDevices(
                riskLevel, deviceCode, page, pageSize);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", result.get("records"));
        response.put("total", result.get("total"));
        response.put("page", page);
        response.put("pageSize", pageSize);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/health/device/{deviceId}/latest — 查询设备最新健康评估
    // ================================================================

    /**
     * 查询指定设备的最新健康评估记录
     *
     * @param deviceId 设备ID
     * @return 健康记录
     */
    @GetMapping("/device/{deviceId}/latest")
    public ResponseEntity<Map<String, Object>> getLatestByDevice(@PathVariable Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "设备ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        DeviceHealth latestHealth = deviceHealthService.getLatestByDevice(deviceId);

        Map<String, Object> response = new HashMap<>();
        if (latestHealth == null) {
            response.put("code", 404);
            response.put("message", "未找到该设备的健康评估记录");
            return ResponseEntity.status(404).body(response);
        }

        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", latestHealth);
        return ResponseEntity.ok(response);
    }
}
