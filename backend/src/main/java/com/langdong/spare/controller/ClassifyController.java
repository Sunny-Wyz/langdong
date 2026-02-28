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
@CrossOrigin(origins = "*")
public class ClassifyController {

    @Autowired
    private ClassifyService classifyService;

    // ================================================================
    // POST /api/classify/trigger — 手动触发全量重算（ADMIN专属）
    // ================================================================

    /**
     * 手动触发全量分类重算
     * 重算为异步执行，接口立即返回，不等待计算完成
     * 权限：仅拥有 classify:trigger:run 权限的角色（即 ADMIN）可调用
     */
    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('classify:trigger:run')")
    public ResponseEntity<Map<String, Object>> triggerClassify() {
        // 异步触发，不阻塞
        classifyService.runFullClassify();

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "重算任务已启动，请稍后刷新查看最新分类结果");
        return ResponseEntity.ok(resp);
    }

    // ================================================================
    // GET /api/classify/result — 分页查询最新分类结果
    // ================================================================

    /**
     * 查询最新分类结果列表（分页）
     *
     * @param abcClass  ABC分类过滤（可选，A/B/C）
     * @param xyzClass  XYZ分类过滤（可选，X/Y/Z）
     * @param partCode  备件编码关键词（可选，模糊匹配）
     * @param month     分类月份（可选，格式yyyy-MM，默认取最新月份）
     * @param page      页码（从1开始，默认1）
     * @param pageSize  每页条数（默认20）
     */
    @GetMapping("/result")
    public ResponseEntity<Map<String, Object>> queryResult(
            @RequestParam(required = false) String abcClass,
            @RequestParam(required = false) String xyzClass,
            @RequestParam(required = false) String partCode,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        // 参数合法性校验
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

    // ================================================================
    // GET /api/classify/result/{partCode} — 查询指定备件的分类历史
    // ================================================================

    /**
     * 查询指定备件的全部历史分类记录（按月份升序）
     *
     * @param partCode 备件编码（URL路径参数）
     */
    @GetMapping("/result/{partCode}")
    public ResponseEntity<List<PartClassify>> queryHistory(@PathVariable String partCode) {
        if (partCode == null || partCode.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<PartClassify> history = classifyService.queryHistory(partCode);
        return ResponseEntity.ok(history);
    }

    // ================================================================
    // GET /api/classify/matrix — 查询ABC×XYZ 9格矩阵分布
    // ================================================================

    /**
     * 查询最新月份的 ABC×XYZ 9格矩阵备件数量分布
     * 响应格式：{ "AX": 12, "AY": 5, "AZ": 2, "BX": 30, "BY": 8, "BZ": 3, "CX": 45, "CY": 20, "CZ": 15 }
     */
    @GetMapping("/matrix")
    public ResponseEntity<Map<String, Long>> queryMatrix() {
        Map<String, Long> matrix = classifyService.queryMatrix();
        return ResponseEntity.ok(matrix);
    }
}
