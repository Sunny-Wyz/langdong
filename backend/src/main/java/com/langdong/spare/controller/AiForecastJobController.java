package com.langdong.spare.controller;

import com.langdong.spare.entity.SparePart;
import com.langdong.spare.forecast.service.HurdleGammaJobService;
import com.langdong.spare.mapper.SparePartMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 任务中心接口：补货任务走两阶段 Hurdle-Gamma（Java 异步 + Python 算法微服务）。
 */
@RestController
@RequestMapping("/api/ai/forecast/jobs")
public class AiForecastJobController {

    private static final java.util.regex.Pattern TASK_ID_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

    private final HurdleGammaJobService hurdleGammaJobService;
    private final SparePartMapper sparePartMapper;

    public AiForecastJobController(HurdleGammaJobService hurdleGammaJobService,
                                   SparePartMapper sparePartMapper) {
        this.hurdleGammaJobService = hurdleGammaJobService;
        this.sparePartMapper = sparePartMapper;
    }

    @PostMapping("/replenishment")
    @PreAuthorize("hasAuthority('ai:forecast:trigger')")
    public ResponseEntity<Map<String, Object>> submitReplenishmentJob(
            @RequestBody Map<String, Object> body
    ) {
        List<Integer> sparePartIds = extractSparePartIds(body);
        if (sparePartIds == null) {
            return ResponseEntity.badRequest().body(error(400, "spare_part_ids must be valid IDs or part codes"));
        }
        if (sparePartIds.isEmpty()) {
            return ResponseEntity.badRequest().body(error(400, "spare_part_ids is required"));
        }

        try {
            Map<String, Object> response = hurdleGammaJobService.submit(sparePartIds);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error(400, ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error(500, "两阶段 Hurdle-Gamma 任务提交失败: " + safeMsg(ex)));
        }
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasAuthority('ai:forecast:list')")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(error(400, "taskId is required"));
        }
        String normalizedTaskId = taskId.trim();
        if (!TASK_ID_PATTERN.matcher(normalizedTaskId).matches()) {
            return ResponseEntity.badRequest().body(error(400, "taskId format is invalid"));
        }

        Map<String, Object> response = hurdleGammaJobService.getStatus(normalizedTaskId);
        if (response == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(error(404, "任务不存在或服务已重启导致内存任务丢失"));
        }
        return ResponseEntity.ok(response);
    }

    private List<Integer> extractSparePartIds(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object value = body.get("spare_part_ids");
        if (value == null) {
            value = body.get("spare_parts");
        }
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }

        List<Integer> ids = new java.util.ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof Number numberValue) {
                double raw = numberValue.doubleValue();
                if (raw <= 0 || raw != Math.floor(raw)) {
                    return null;
                }
                ids.add((int) raw);
                continue;
            }

            if (!(item instanceof String strValue)) {
                return null;
            }

            String token = strValue.trim();
            if (token.isEmpty()) {
                return null;
            }

            if (token.matches("^\\d+$")) {
                try {
                    int numericId = Integer.parseInt(token);
                    if (numericId <= 0) {
                        return null;
                    }
                    ids.add(numericId);
                    continue;
                } catch (NumberFormatException ex) {
                    return null;
                }
            }

            SparePart sparePart = sparePartMapper.findByCode(token);
            if (sparePart == null || sparePart.getId() == null) {
                // 兼容大小写
                sparePart = sparePartMapper.findByCode(token.toUpperCase(java.util.Locale.ROOT));
            }
            if (sparePart == null || sparePart.getId() == null) {
                return null;
            }
            ids.add(sparePart.getId().intValue());
        }

        return ids.stream().distinct().toList();
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", code);
        resp.put("message", message == null || message.isBlank() ? "Request failed" : message);
        return resp;
    }

    private String safeMsg(Exception ex) {
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }
}
