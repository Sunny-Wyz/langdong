package com.langdong.spare.controller;

import com.langdong.spare.service.WarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/warnings")
public class WarningController {

    @Autowired
    private WarningService warningService;

    @GetMapping
    @PreAuthorize("hasAuthority('report:warning:view')")
    public ResponseEntity<Map<String, Object>> getAllWarnings() {
        return ResponseEntity.ok(warningService.getAllWarnings());
    }
}
