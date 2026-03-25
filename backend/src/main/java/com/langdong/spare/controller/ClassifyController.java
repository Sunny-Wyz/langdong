package com.langdong.spare.controller;

import com.langdong.spare.entity.PartClassify;
import com.langdong.spare.service.ClassifyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 备件智能分类模块 Controller
 *
 * 提供4个API：
 *   POST /api/classify/trigger       手动触发全量重算（ADMIN权限）
 *   GET  /api/classify/result        查询最新分类结果（分页）
 *   GET  /api/classify/result/{code} 查询指定备件的分类历史
 *   GET  /api/classify/matrix        查询ABC×XYZ 9格矩阵分布
 */
@RestController
@RequestMapping("/api/classify")
public class ClassifyController {

    @Autowired
    private ClassifyService classifyService;

    /**
     * 手动触发全量分类重算（ADMIN专属）
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('classify:trigger:run')")
    public ResponseEntity<Map<String, Object>> triggerClassify() {
        classifyService.runFullClassify();

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "重算任务已启动，请稍后刷新查看最新分类结果");
        return ResponseEntity.ok(resp);
    }

    /**
     * 查询最新分类结果列表（分页）
     */
    @GetMapping("/result")
    @PreAuthorize("hasAuthority('classify:result:list')")
    public ResponseEntity<Map<String, Object>> queryResult(
            @RequestParam(required = false) String abcClass,
            @RequestParam(required = false) String xyzClass,
            @RequestParam(required = false) String partCode,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        if (month != null && !month.matches("\\d{4}-\\d{2}")) {
            return ResponseEntity.badRequest().build();
        }
        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > 200) {
            pageSize = 20;
        }

        Map<String, Object> result = classifyService.queryResult(
                abcClass, xyzClass, partCode, month, page, pageSize);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询指定备件的全部历史分类记录（按月份升序）
     */
    @GetMapping("/result/{partCode}")
    @PreAuthorize("hasAuthority('classify:result:list')")
    public ResponseEntity<List<PartClassify>> queryHistory(@PathVariable String partCode) {
        if (partCode == null || partCode.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<PartClassify> history = classifyService.queryHistory(partCode);
        return ResponseEntity.ok(history);
    }

    /**
     * 查询最新月份的 ABC×XYZ 9格矩阵备件数量分布
     */
    @GetMapping("/matrix")
    @PreAuthorize("hasAuthority('classify:result:list')")
    public ResponseEntity<Map<String, Long>> queryMatrix() {
        Map<String, Long> matrix = classifyService.queryMatrix();
        return ResponseEntity.ok(matrix);
    }
}
