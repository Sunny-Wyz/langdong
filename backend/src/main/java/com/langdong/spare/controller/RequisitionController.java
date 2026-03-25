package com.langdong.spare.controller;

import com.langdong.spare.dto.*;
import com.langdong.spare.entity.Requisition;
import com.langdong.spare.entity.User;
import com.langdong.spare.mapper.UserMapper;
import com.langdong.spare.service.RequisitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requisitions")
public class RequisitionController {

    @Autowired
    private RequisitionService requisitionService;

    @Autowired
    private UserMapper userMapper;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        return user != null ? user.getId() : null;
    }

    @PostMapping("/apply")
    @PreAuthorize("hasAuthority('req:apply:add')")
    public ResponseEntity<?> apply(@RequestBody RequisitionApplyDTO dto) {
        requisitionService.apply(dto, getCurrentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('req:record:list')")
    public ResponseEntity<List<Requisition>> getList(@RequestParam(required = false) String status,
            @RequestParam(required = false) Long applicantId) {
        return ResponseEntity.ok(requisitionService.getList(status, applicantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('req:record:list')")
    public ResponseEntity<Map<String, Object>> getDetail(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("info", requisitionService.getDetail(id));
        result.put("items", requisitionService.getItems(id));
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('req:approve:list')")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody RequisitionApproveDTO dto) {
        requisitionService.approve(id, dto, getCurrentUserId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/outbound")
    @PreAuthorize("hasAuthority('req:outbound:confirm')")
    public ResponseEntity<?> outbound(@PathVariable Long id, @RequestBody RequisitionOutboundDTO dto) {
        requisitionService.outbound(id, dto);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/install")
    @PreAuthorize("hasAuthority('req:install:edit')")
    public ResponseEntity<?> install(@PathVariable Long id, @RequestBody RequisitionInstallDTO dto) {
        requisitionService.install(id, dto, getCurrentUserId());
        return ResponseEntity.ok().build();
    }
}
