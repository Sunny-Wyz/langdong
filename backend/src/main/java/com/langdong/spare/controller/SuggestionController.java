package com.langdong.spare.controller;

import com.langdong.spare.dto.SuggestionActionDTO;
import com.langdong.spare.entity.MaintenanceSuggestion;
import com.langdong.spare.service.MaintenanceSuggestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 维护建议管理 Controller
 *
 * 提供维护建议相关API:
 *   GET  /api/phm/suggestion/list             查询维护建议列表
 *   GET  /api/phm/suggestion/{id}             查询建议详情
 *   POST /api/phm/suggestion/{id}/approve     采纳建议
 *   POST /api/phm/suggestion/{id}/reject      拒绝建议
 *   GET  /api/phm/suggestion/pending-count    待处理建议数量
 *   GET  /api/phm/suggestion/dashboard        建议统计数据
 */
@RestController
@RequestMapping("/api/phm/suggestion")
public class SuggestionController {

    @Autowired
    private MaintenanceSuggestionService suggestionService;

    // ================================================================
    // GET /api/phm/suggestion/list — 查询维护建议列表(分页)
    // ================================================================

    /**
     * 分页查询维护建议列表
     *
     * @param status          状态过滤(PENDING/ACCEPTED/REJECTED/COMPLETED,可选)
     * @param priorityLevel   优先级过滤(HIGH/MEDIUM/LOW,可选)
     * @param maintenanceType 维护类型过滤(PREVENTIVE/PREDICTIVE/EMERGENCY,可选)
     * @param deviceCode      设备编码关键词(可选)
     * @param page            页码(从1开始,默认1)
     * @param pageSize        每页条数(默认20)
     * @return 分页结果
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('phm:suggestion:view')")
    public ResponseEntity<Map<String, Object>> getSuggestionList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priorityLevel,
            @RequestParam(required = false) String maintenanceType,
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

        Map<String, Object> result = suggestionService.getSuggestionsByPage(
                status, priorityLevel, maintenanceType, deviceCode, page, pageSize);

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
    // GET /api/phm/suggestion/{id} — 查询建议详情
    // ================================================================

    /**
     * 查询指定建议的详细信息
     *
     * @param id 建议ID
     * @return 建议详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('phm:suggestion:view')")
    public ResponseEntity<Map<String, Object>> getSuggestionDetail(@PathVariable Long id) {
        if (id == null || id <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "建议ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        MaintenanceSuggestion suggestion = suggestionService.getSuggestionById(id);

        Map<String, Object> response = new HashMap<>();
        if (suggestion == null) {
            response.put("code", 404);
            response.put("message", "未找到该建议记录");
            return ResponseEntity.status(404).body(response);
        }

        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", suggestion);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // POST /api/phm/suggestion/{id}/approve — 采纳建议
    // ================================================================

    /**
     * 采纳维护建议（自动创建工单和领用单）
     * 权限: phm:suggestion:approve
     *
     * @param id        建议ID
     * @param handledBy 处理人ID(从JWT token获取或前端传入)
     * @return 处理结果
     */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('phm:suggestion:approve')")
    public ResponseEntity<Map<String, Object>> approveSuggestion(
            @PathVariable Long id,
            @RequestParam Long handledBy
    ) {
        if (id == null || id <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "建议ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        if (handledBy == null || handledBy <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "处理人ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            Map<String, Long> result = suggestionService.approveSuggestion(id, handledBy);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "建议已采纳，已自动创建工单和领用单");
            response.put("workorderId", result.get("workorderId"));
            response.put("requisitionId", result.get("requisitionId"));
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "采纳失败，请检查建议状态后重试");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", "系统错误，请稍后重试");
            return ResponseEntity.status(500).body(error);
        }
    }

    // ================================================================
    // POST /api/phm/suggestion/{id}/reject — 拒绝建议
    // ================================================================

    /**
     * 拒绝维护建议
     * 权限: phm:suggestion:reject
     *
     * @param id           建议ID
     * @param rejectReason 拒绝原因
     * @param handledBy    处理人ID
     * @return 处理结果
     */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('phm:suggestion:reject')")
    public ResponseEntity<Map<String, Object>> rejectSuggestion(
            @PathVariable Long id,
            @RequestParam String rejectReason,
            @RequestParam Long handledBy
    ) {
        if (id == null || id <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "建议ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        if (rejectReason == null || rejectReason.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "拒绝原因不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        if (handledBy == null || handledBy <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "处理人ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        try {
            suggestionService.rejectSuggestion(id, rejectReason, handledBy);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "建议已拒绝");
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "拒绝失败，请检查建议状态后重试");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", "系统错误，请稍后重试");
            return ResponseEntity.status(500).body(error);
        }
    }

    // ================================================================
    // GET /api/phm/suggestion/pending-count — 待处理建议数量
    // ================================================================

    /**
     * 查询待处理建议数量（用于首页红点提示）
     *
     * @return 待处理建议数
     */
    @GetMapping("/pending-count")
    @PreAuthorize("hasAuthority('phm:suggestion:view')")
    public ResponseEntity<Map<String, Object>> getPendingCount() {
        long pendingCount = suggestionService.countPending();

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("pendingCount", pendingCount);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/suggestion/dashboard — 建议统计数据
    // ================================================================

    /**
     * 获取建议统计数据（用于Dashboard展示）
     *
     * @return 统计数据
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('phm:suggestion:view')")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> dashboardData = suggestionService.getDashboardData();

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", dashboardData);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/suggestion/device/{deviceId} — 查询设备的所有建议
    // ================================================================

    /**
     * 查询指定设备的所有维护建议（按日期降序）
     *
     * @param deviceId 设备ID
     * @return 建议列表
     */
    @GetMapping("/device/{deviceId}")
    @PreAuthorize("hasAuthority('phm:suggestion:view')")
    public ResponseEntity<Map<String, Object>> getSuggestionsByDevice(@PathVariable Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 400);
            error.put("message", "设备ID无效");
            return ResponseEntity.badRequest().body(error);
        }

        List<MaintenanceSuggestion> suggestions = suggestionService.getSuggestionsByDevice(deviceId);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", suggestions);
        response.put("total", suggestions.size());
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GET /api/phm/suggestion/high-priority — 查询高优先级待处理建议
    // ================================================================

    /**
     * 查询高优先级待处理建议列表
     *
     * @param limit 返回数量限制(默认10)
     * @return 建议列表
     */
    @GetMapping("/high-priority")
    @PreAuthorize("hasAuthority('phm:suggestion:view')")
    public ResponseEntity<Map<String, Object>> getHighPrioritySuggestions(
            @RequestParam(defaultValue = "10") int limit
    ) {
        if (limit < 1 || limit > 100) {
            limit = 10;
        }

        List<MaintenanceSuggestion> suggestions = suggestionService.getPendingHighPriority(limit);

        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", "查询成功");
        response.put("data", suggestions);
        response.put("total", suggestions.size());
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // POST /api/phm/suggestion/generate — 手动触发维护建议生成
    // ================================================================

    /**
     * 手动触发维护建议生成（临时端点用于测试）
     *
     * @return 生成的建议数量
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('phm:suggestion:approve')")
    public ResponseEntity<Map<String, Object>> generateSuggestions() {
        try {
            int generatedCount = suggestionService.generateSuggestions(null);

            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "维护建议生成完成");
            response.put("generatedCount", generatedCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", 500);
            error.put("message", "生成失败，请稍后重试");
            return ResponseEntity.status(500).body(error);
        }
    }
}
