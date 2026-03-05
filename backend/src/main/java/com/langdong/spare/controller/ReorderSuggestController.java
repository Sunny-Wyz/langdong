package com.langdong.spare.controller;

import com.langdong.spare.entity.ReorderSuggest;
import com.langdong.spare.mapper.ReorderSuggestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/reorder-suggests")
@CrossOrigin(origins = "*")
public class ReorderSuggestController {

    @Autowired
    private ReorderSuggestMapper reorderSuggestMapper;

    @GetMapping
    public ResponseEntity<List<ReorderSuggest>> getList(
            @RequestParam(required = false, defaultValue = "待处理") String status) {
        // 兜底：待处理建议为空时自动生成一批演示建议，避免页面无数据
        if ("待处理".equals(status) && reorderSuggestMapper.countByStatus("待处理") == 0) {
            String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            reorderSuggestMapper.bootstrapPendingSuggestions(month);
        }
        return ResponseEntity.ok(reorderSuggestMapper.findByStatus(status));
    }

    @PutMapping("/{id}/ignore")
    public ResponseEntity<?> ignore(@PathVariable Long id) {
        reorderSuggestMapper.updateStatus(id, "已忽略");
        return ResponseEntity.ok().build();
    }
}
