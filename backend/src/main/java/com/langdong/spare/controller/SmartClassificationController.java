package com.langdong.spare.controller;

import com.langdong.spare.entity.ClassificationStrategy;
import com.langdong.spare.entity.SparePartClassification;
import com.langdong.spare.mapper.ClassificationStrategyMapper;
import com.langdong.spare.mapper.SparePartClassificationMapper;
import com.langdong.spare.service.SmartClassificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/smart-classification")
@CrossOrigin(origins = "*")
public class SmartClassificationController {

    @Autowired
    private SmartClassificationService classificationService;

    @Autowired
    private ClassificationStrategyMapper strategyMapper;

    @Autowired
    private SparePartClassificationMapper classificationMapper;

    // --- 策略配置 API ---

    @GetMapping("/strategies")
    public List<ClassificationStrategy> getAllStrategies() {
        return strategyMapper.findAll();
    }

    @PutMapping("/strategies/{id}")
    public ResponseEntity<ClassificationStrategy> updateStrategy(@PathVariable Long id, @RequestBody ClassificationStrategy strategy) {
        strategy.setId(id);
        strategyMapper.update(strategy);
        return ResponseEntity.ok(strategy);
    }

    // --- 控制与查询 API ---

    @PostMapping("/trigger")
    public ResponseEntity<String> triggerCalculation() {
        classificationService.calculateAllClassifications();
        return ResponseEntity.ok("分类重算任务已成功触发");
    }

    @GetMapping("/results")
    public List<SparePartClassification> getAllResults() {
        return classificationMapper.findAll();
    }

    // --- 人工调整与审批 API ---

    @PostMapping("/adjust")
    public ResponseEntity<String> submitAdjustment(@RequestBody java.util.Map<String, Object> body) {
        Long sparePartId = Long.valueOf(body.get("sparePartId").toString());
        String newCombination = body.get("newCombination").toString();
        String reason = body.get("reason").toString();
        Long applicantId = Long.valueOf(body.get("applicantId").toString());
        classificationService.submitAdjustment(sparePartId, newCombination, reason, applicantId);
        return ResponseEntity.ok("调整申请已提交");
    }

    @Autowired
    private com.langdong.spare.mapper.ClassificationAdjustmentRecordMapper adjustmentRecordMapper;

    @GetMapping("/adjustments")
    public List<com.langdong.spare.entity.ClassificationAdjustmentRecord> getAdjustments(@RequestParam(required = false) String status) {
        if (status != null && !status.isEmpty()) {
            return adjustmentRecordMapper.findByStatus(status);
        }
        return adjustmentRecordMapper.findAll();
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<String> approveAdjustment(@PathVariable Long id, @RequestBody java.util.Map<String, Object> body) {
        boolean isApproved = Boolean.parseBoolean(body.get("isApproved").toString());
        String remark = body.getOrDefault("remark", "").toString();
        Long approverId = Long.valueOf(body.get("approverId").toString());
        classificationService.approveAdjustment(id, isApproved, remark, approverId);
        return ResponseEntity.ok("审批完成");
    }
}
