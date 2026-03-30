package com.langdong.spare.controller;

import com.langdong.spare.entity.AiTrainDataRecord;
import com.langdong.spare.mapper.AiTrainDataMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/train-data")
public class AiTrainDataController {

    private final AiTrainDataMapper aiTrainDataMapper;

    public AiTrainDataController(AiTrainDataMapper aiTrainDataMapper) {
        this.aiTrainDataMapper = aiTrainDataMapper;
    }

    @GetMapping("/list")
    @PreAuthorize("hasAuthority('ai:train-data:list')")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String partCode,
            @RequestParam(required = false) String sourceLevel,
            @RequestParam(required = false) Integer isImputed,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String normalizedStartDate = normalizeDate(startDate);
        String normalizedEndDate = normalizeDate(endDate);
        if ((startDate != null && normalizedStartDate == null) || (endDate != null && normalizedEndDate == null)) {
            return ResponseEntity.badRequest().body(error(400, "Date format must be yyyy-MM-dd"));
        }

        String normalizedPartCode = normalizeText(partCode);
        String normalizedSourceLevel = normalizeText(sourceLevel);

        if (normalizedSourceLevel != null) {
            normalizedSourceLevel = normalizedSourceLevel.toUpperCase();
            if (!normalizedSourceLevel.equals("TRACE")
                    && !normalizedSourceLevel.equals("REQ_OUT")
                    && !normalizedSourceLevel.equals("TRACE_REQ")
                    && !normalizedSourceLevel.equals("NONE")) {
                return ResponseEntity.badRequest().body(error(400, "sourceLevel must be TRACE/REQ_OUT/TRACE_REQ/NONE"));
            }
        }

        if (isImputed != null && isImputed != 0 && isImputed != 1) {
            return ResponseEntity.badRequest().body(error(400, "isImputed must be 0 or 1"));
        }

        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }

        int offset = (page - 1) * size;
        List<AiTrainDataRecord> list = aiTrainDataMapper.findByPage(
                normalizedStartDate,
                normalizedEndDate,
                normalizedPartCode,
                normalizedSourceLevel,
                isImputed,
                offset,
                size
        );
        long total = aiTrainDataMapper.countByPage(
                normalizedStartDate,
                normalizedEndDate,
                normalizedPartCode,
                normalizedSourceLevel,
                isImputed
        );

        Map<String, Object> resp = new HashMap<>();
        resp.put("total", total);
        resp.put("list", list);
        resp.put("page", page);
        resp.put("size", size);
        return ResponseEntity.ok(resp);
    }

    private String normalizeText(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeDate(String dateText) {
        String value = normalizeText(dateText);
        if (value == null) {
            return null;
        }
        return value.matches("\\d{4}-\\d{2}-\\d{2}") ? value : null;
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", code);
        resp.put("message", message);
        return resp;
    }
}
