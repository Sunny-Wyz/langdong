package com.langdong.spare.controller;

import com.langdong.spare.entity.ReorderSuggest;
import com.langdong.spare.mapper.ReorderSuggestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/reorder-suggests")
public class ReorderSuggestController {

    @Autowired
    private ReorderSuggestMapper reorderSuggestMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('po:suggest:list')")
    public ResponseEntity<List<ReorderSuggest>> getList(
            @RequestParam(required = false, defaultValue = "待处理") String status) {
        if ("待处理".equals(status) && reorderSuggestMapper.countByStatus("待处理") == 0) {
            String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            reorderSuggestMapper.bootstrapPendingSuggestions(month);
        }
        return ResponseEntity.ok(reorderSuggestMapper.findByStatus(status));
    }

    @PutMapping("/{id}/ignore")
    @PreAuthorize("hasAuthority('po:suggest:list')")
    public ResponseEntity<?> ignore(@PathVariable Long id) {
        reorderSuggestMapper.updateStatus(id, "已忽略");
        return ResponseEntity.ok().build();
    }
}
