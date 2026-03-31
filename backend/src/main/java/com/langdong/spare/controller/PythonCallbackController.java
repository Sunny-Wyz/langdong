package com.langdong.spare.controller;

import com.langdong.spare.dto.PythonCallbackPayload;
import com.langdong.spare.service.ai.AiForecastService;
import com.langdong.spare.service.ai.PythonCallbackStoreService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/forecast/callback/python")
public class PythonCallbackController {

    private final PythonCallbackStoreService callbackStoreService;
    private final AiForecastService aiForecastService;

    @Value("${ai.python.callback-token}")
    private String callbackToken;

    public PythonCallbackController(PythonCallbackStoreService callbackStoreService,
                                    AiForecastService aiForecastService) {
        this.callbackStoreService = callbackStoreService;
        this.aiForecastService = aiForecastService;
    }

    @PostMapping("/replenishment")
    public ResponseEntity<Map<String, Object>> receiveReplenishmentCallback(
            @RequestHeader(value = "X-Callback-Token", required = false) String token,
            @RequestBody PythonCallbackPayload payload
    ) {
        if (token == null || !token.equals(callbackToken)) {
            Map<String, Object> unauthorized = new HashMap<>();
            unauthorized.put("code", 401);
            unauthorized.put("message", "Unauthorized callback token");
            return ResponseEntity.status(401).body(unauthorized);
        }

        if (payload == null || payload.task_id() == null || payload.task_id().isBlank()) {
            Map<String, Object> bad = new HashMap<>();
            bad.put("code", 400);
            bad.put("message", "task_id is required");
            return ResponseEntity.badRequest().body(bad);
        }

        String status = payload.status() == null ? "" : payload.status().trim().toUpperCase(Locale.ROOT);
        if (!"SUCCESS".equals(status) && !"FAILURE".equals(status)) {
            Map<String, Object> bad = new HashMap<>();
            bad.put("code", 400);
            bad.put("message", "status must be SUCCESS or FAILURE");
            return ResponseEntity.badRequest().body(bad);
        }

        Map<String, Object> normalized = new HashMap<>();
        normalized.put("task_id", payload.task_id());
        normalized.put("status", status);
        normalized.put("result", payload.result());
        normalized.put("error", payload.error());

        if ("SUCCESS".equals(status)) {
            try {
                aiForecastService.applyAsyncForecastResult(payload.result());
            } catch (Exception ex) {
                normalized.put("status", "FAILURE");
                normalized.put("error", "forecast overwrite failed: " + ex.getMessage());
                callbackStoreService.save(payload.task_id(), normalized);

                Map<String, Object> failed = new HashMap<>();
                failed.put("code", 500);
                failed.put("message", "Callback accepted but forecast overwrite failed");
                return ResponseEntity.status(500).body(failed);
            }
        }

        callbackStoreService.save(payload.task_id(), normalized);

        Map<String, Object> ok = new HashMap<>();
        ok.put("code", 200);
        ok.put("message", "Callback accepted");
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/replenishment/{taskId}")
    @PreAuthorize("hasAuthority('ai:forecast:list')")
    public ResponseEntity<Map<String, Object>> getReplenishmentCallback(@PathVariable String taskId) {
        Map<String, Object> payload = callbackStoreService.get(taskId);
        if (payload == null) {
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("code", 404);
            notFound.put("message", "Callback result not found");
            return ResponseEntity.status(404).body(notFound);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "ok");
        resp.put("data", payload);
        return ResponseEntity.ok(resp);
    }
}
