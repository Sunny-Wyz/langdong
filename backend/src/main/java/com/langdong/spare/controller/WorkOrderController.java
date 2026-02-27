package com.langdong.spare.controller;

import com.langdong.spare.dto.WorkOrderAssignDTO;
import com.langdong.spare.dto.WorkOrderCompleteDTO;
import com.langdong.spare.dto.WorkOrderProcessDTO;
import com.langdong.spare.dto.WorkOrderReportDTO;
import com.langdong.spare.entity.WorkOrder;
import com.langdong.spare.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-orders")
@CrossOrigin(origins = "*")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    // Hardcoded user ID for demo purposes (与领用模块保持一致)
    private Long getCurrentUserId() {
        return 1L;
    }

    @PostMapping("/report")
    public ResponseEntity<?> report(@RequestBody WorkOrderReportDTO dto) {
        workOrderService.report(dto, getCurrentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<WorkOrder>> getList(
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String faultLevel,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ResponseEntity.ok(workOrderService.getList(orderStatus, deviceId, faultLevel, startTime, endTime));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDetail(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("info", workOrderService.getDetail(id));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/assign")
    public ResponseEntity<?> assign(@PathVariable Long id, @RequestBody WorkOrderAssignDTO dto) {
        try {
            workOrderService.assign(id, dto);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/process")
    public ResponseEntity<?> process(@PathVariable Long id, @RequestBody WorkOrderProcessDTO dto) {
        try {
            workOrderService.process(id, dto);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<?> complete(@PathVariable Long id, @RequestBody WorkOrderCompleteDTO dto) {
        try {
            workOrderService.complete(id, dto);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
