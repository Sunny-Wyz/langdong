package com.langdong.spare.controller;

import com.langdong.spare.service.WarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/warnings")
@CrossOrigin(origins = "*")
public class WarningController {

    @Autowired
    private WarningService warningService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllWarnings() {
        return ResponseEntity.ok(warningService.getAllWarnings());
    }
}
