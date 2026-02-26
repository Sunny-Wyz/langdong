package com.langdong.spare.service;

import com.langdong.spare.entity.ClassificationStrategy;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.entity.SparePartClassification;
import com.langdong.spare.mapper.ClassificationStrategyMapper;
import com.langdong.spare.mapper.SparePartClassificationMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class SmartClassificationService {

    @Autowired
    private SparePartMapper sparePartMapper;

    @Autowired
    private SparePartClassificationMapper classificationMapper;

    @Autowired
    private ClassificationStrategyMapper strategyMapper;

    @Autowired
    private DemandPredictor demandPredictor;

    /**
     * 触发全量备件分类重算任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void calculateAllClassifications() {
        List<SparePart> allSpareParts = sparePartMapper.findAll();
        
        for (SparePart part : allSpareParts) {
            calculateSinglePart(part);
        }
    }

    private void calculateSinglePart(SparePart part) {
        // 1. 获取历史消耗数据（这里使用模拟数据代替真实的领用记录查询）
        List<Integer> historicalDemands = mockHistoricalDemands();
        
        // 2. 预测下月需求并计算XYZ变异系数
        BigDecimal predictedDemand = demandPredictor.predictNextMonthDemand(historicalDemands);
        BigDecimal xyzCv = demandPredictor.calculateCV(historicalDemands);
        
        // 判定XYZ分类
        String xyzCategory = determineXyzCategory(xyzCv);
        
        // 3. 计算 ABC 综合得分
        // (40% 年消耗金额, 30% 设备关键度, 20% 采购提前期, 10% 替代难度)
        
        // 3.1 消耗金额得分 (假设基于预测需求 * 单价标准化到 0-100)
        BigDecimal costScore = calculateCostScore(predictedDemand, part.getPrice());
        
        // 3.2 关键度得分 (HIGH=100, MEDIUM=50, LOW=10)
        BigDecimal criticalScore = mapToScore(part.getCriticality(), 100, 50, 10);
        
        // 3.3 采购提前期得分 (天数越长得分越高, 假设 Max=90 天)
        BigDecimal leadTimeScore = calculateLeadTimeScore(part.getLeadTime());
        
        // 3.4 替代难度得分 (HIGH=100, MEDIUM=50, LOW=10)
        BigDecimal difficultyScore = mapToScore(part.getSubstitutionDifficulty(), 100, 50, 10);
        
        // 综合得分
        BigDecimal abcScore = costScore.multiply(new BigDecimal("0.4"))
                .add(criticalScore.multiply(new BigDecimal("0.3")))
                .add(leadTimeScore.multiply(new BigDecimal("0.2")))
                .add(difficultyScore.multiply(new BigDecimal("0.1")))
                .setScale(2, RoundingMode.HALF_UP);
                
        // 判定ABC分类
        String abcCategory = determineAbcCategory(abcScore);
        
        // 4. 组装结果并保存
        String combinationCode = abcCategory + xyzCategory;
        
        SparePartClassification existing = classificationMapper.findBySparePartId(part.getId());
        if (existing == null) {
            SparePartClassification classification = new SparePartClassification();
            classification.setSparePartId(part.getId());
            classification.setAbcCategory(abcCategory);
            classification.setXyzCategory(xyzCategory);
            classification.setCombinationCode(combinationCode);
            classification.setAbcScore(abcScore);
            classification.setXyzCv(xyzCv);
            classification.setPredictedDemand(predictedDemand);
            classification.setCostScore(costScore);
            classification.setCriticalScore(criticalScore);
            classification.setLeadTimeScore(leadTimeScore);
            classification.setDifficultyScore(difficultyScore);
            classification.setIsManualAdjusted(0);
            classificationMapper.insert(classification);
        } else {
            // 如果已经被人工调整过，则跳过覆盖组合代码，仅更新预测数据
            if (existing.getIsManualAdjusted() == 0) {
                existing.setAbcCategory(abcCategory);
                existing.setXyzCategory(xyzCategory);
                existing.setCombinationCode(combinationCode);
            }
            existing.setAbcScore(abcScore);
            existing.setXyzCv(xyzCv);
            existing.setPredictedDemand(predictedDemand);
            existing.setCostScore(costScore);
            existing.setCriticalScore(criticalScore);
            existing.setLeadTimeScore(leadTimeScore);
            existing.setDifficultyScore(difficultyScore);
            classificationMapper.update(existing);
        }
    }

    private String determineXyzCategory(BigDecimal cv) {
        if (cv.compareTo(new BigDecimal("0.5")) < 0) {
            return "X"; // 稳定
        } else if (cv.compareTo(new BigDecimal("1.0")) <= 0) {
            return "Y"; // 波动
        } else {
            return "Z"; // 随机
        }
    }

    private String determineAbcCategory(BigDecimal score) {
        if (score.compareTo(new BigDecimal("70")) >= 0) {
            return "A"; // 核心/高价值
        } else if (score.compareTo(new BigDecimal("40")) >= 0) {
            return "B"; // 重要/中等价值
        } else {
            return "C"; // 一般/低价值
        }
    }

    private BigDecimal calculateCostScore(BigDecimal predictedDemand, BigDecimal price) {
        if (price == null) price = BigDecimal.ZERO;
        BigDecimal annualCost = predictedDemand.multiply(new BigDecimal("12")).multiply(price);
        // 此处为简化，假设年消耗 100,000 为满分 100 分
        BigDecimal score = annualCost.divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
        return score.min(new BigDecimal("100"));
    }

    private BigDecimal mapToScore(String level, int high, int medium, int low) {
        if ("HIGH".equalsIgnoreCase(level)) return new BigDecimal(high);
        if ("LOW".equalsIgnoreCase(level)) return new BigDecimal(low);
        return new BigDecimal(medium);
    }
    
    private BigDecimal calculateLeadTimeScore(Integer leadTime) {
        if (leadTime == null) return new BigDecimal("10");
        // 假设 90 天及以上为 100 分
        double score = (leadTime.doubleValue() / 90.0) * 100;
        return BigDecimal.valueOf(Math.min(score, 100.0)).setScale(2, RoundingMode.HALF_UP);
    }

    // mock 过去 6 个月的需求数据
    private List<Integer> mockHistoricalDemands() {
        List<Integer> demands = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            demands.add(random.nextInt(50));
        }
        return demands;
    }

    // --- 人工调整与审批逻辑 ---

    @Autowired
    private com.langdong.spare.mapper.ClassificationAdjustmentRecordMapper adjustmentRecordMapper;

    @Transactional(rollbackFor = Exception.class)
    public void submitAdjustment(Long sparePartId, String newCombination, String reason, Long applicantId) {
        SparePartClassification classification = classificationMapper.findBySparePartId(sparePartId);
        if (classification == null) throw new RuntimeException("该备件尚未进行智能分类");

        com.langdong.spare.entity.ClassificationAdjustmentRecord record = new com.langdong.spare.entity.ClassificationAdjustmentRecord();
        record.setSparePartId(sparePartId);
        record.setOriginalCombination(classification.getCombinationCode());
        record.setNewCombination(newCombination);
        record.setReason(reason);
        record.setApplicantId(applicantId);
        record.setStatus("PENDING");
        adjustmentRecordMapper.insert(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void approveAdjustment(Long recordId, boolean isApproved, String remark, Long approverId) {
        com.langdong.spare.entity.ClassificationAdjustmentRecord record = adjustmentRecordMapper.findById(recordId);
        if (record == null || !"PENDING".equals(record.getStatus())) {
            throw new RuntimeException("审批记录不存在或已处理");
        }

        record.setStatus(isApproved ? "APPROVED" : "REJECTED");
        record.setApproverId(approverId);
        record.setApprovalRemark(remark);
        adjustmentRecordMapper.updateStatus(record);

        if (isApproved) {
            SparePartClassification classification = classificationMapper.findBySparePartId(record.getSparePartId());
            if (classification != null) {
                // 解析新的 ABC 和 XYZ
                classification.setAbcCategory(record.getNewCombination().substring(0, 1));
                classification.setXyzCategory(record.getNewCombination().substring(1, 2));
                classification.setCombinationCode(record.getNewCombination());
                classification.setIsManualAdjusted(1);
                classificationMapper.update(classification);
            }
        }
    }
}
