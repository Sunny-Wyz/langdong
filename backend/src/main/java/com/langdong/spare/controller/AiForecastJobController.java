package com.langdong.spare.controller;

import com.langdong.spare.entity.SparePart;
import com.langdong.spare.mapper.SparePartMapper;
import com.langdong.spare.service.ai.PythonModelClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/forecast/jobs")
public class AiForecastJobController {

    private static final java.util.regex.Pattern TASK_ID_PATTERN = java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

    private final PythonModelClient pythonModelClient;
    private final SparePartMapper sparePartMapper;

    public AiForecastJobController(PythonModelClient pythonModelClient, SparePartMapper sparePartMapper) {
        this.pythonModelClient = pythonModelClient;
        this.sparePartMapper = sparePartMapper;
    }

    @PostMapping("/replenishment")
    @PreAuthorize("hasAuthority('ai:forecast:trigger')")
    public ResponseEntity<Map<String, Object>> submitReplenishmentJob(
            @RequestBody Map<String, Object> body
    ) {
        List<Integer> sparePartIds = extractSparePartIds(body);
        if (sparePartIds == null) {
            return ResponseEntity.badRequest().body(error(400, "spare_part_ids must be positive integers"));
        }
        if (sparePartIds.isEmpty()) {
            return ResponseEntity.badRequest().body(error(400, "spare_part_ids is required"));
        }

        try {
            Map<String, Object> response = pythonModelClient.submitReplenishmentJob(sparePartIds);
            return ResponseEntity.ok(response);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(error(ex.getStatusCode().value(), upstreamMessage(ex)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(502, "Python job submit failed"));
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

        try {
            Map<String, Object> response = pythonModelClient.queryJobStatus(normalizedTaskId);
            return ResponseEntity.ok(response);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(error(ex.getStatusCode().value(), upstreamMessage(ex)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(502, "Python job status query failed"));
        }
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

    private String upstreamMessage(HttpStatusCodeException ex) {
        if (ex.getStatusCode().is4xxClientError()) {
            return "Python service request rejected";
        }
        if (ex.getStatusCode().is5xxServerError()) {
            return "Python service temporarily unavailable";
        }
        return "Python service request failed";
    }
}
