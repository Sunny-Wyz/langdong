package com.langdong.spare.controller;

import com.langdong.spare.entity.ReorderSuggest;
import com.langdong.spare.mapper.ReorderSuggestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        return ResponseEntity.ok(reorderSuggestMapper.findByStatus(status));
    }

    @PutMapping("/{id}/ignore")
    public ResponseEntity<?> ignore(@PathVariable Long id) {
        reorderSuggestMapper.updateStatus(id, "已忽略");
        return ResponseEntity.ok().build();
    }
}
