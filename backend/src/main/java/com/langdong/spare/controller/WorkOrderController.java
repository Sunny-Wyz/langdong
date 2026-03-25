package com.langdong.spare.controller;

import com.langdong.spare.dto.WorkOrderAssignDTO;
import com.langdong.spare.dto.WorkOrderCompleteDTO;
import com.langdong.spare.dto.WorkOrderProcessDTO;
import com.langdong.spare.dto.WorkOrderReportDTO;
import com.langdong.spare.entity.User;
import com.langdong.spare.entity.WorkOrder;
import com.langdong.spare.mapper.UserMapper;
import com.langdong.spare.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/work-orders")
public class WorkOrderController {

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private UserMapper userMapper;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        return user != null ? user.getId() : null;
    }

    @PostMapping("/report")
    @PreAuthorize("hasAuthority('wo:report:add')")
    public ResponseEntity<?> report(@RequestBody WorkOrderReportDTO dto) {
        workOrderService.report(dto, getCurrentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('wo:query:list')")
    public ResponseEntity<List<WorkOrder>> getList(
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) String faultLevel,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ResponseEntity.ok(workOrderService.getList(orderStatus, deviceId, faultLevel, startTime, endTime));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wo:query:list')")
    public ResponseEntity<Map<String, Object>> getDetail(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("info", workOrderService.getDetail(id));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('wo:assign:edit')")
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
    @PreAuthorize("hasAuthority('wo:process:edit')")
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
    @PreAuthorize("hasAuthority('wo:complete:edit')")
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
