package com.langdong.spare.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
public class DemandPredictor {

    /**
     * 这里使用简单的指数平滑法(Exponential Smoothing) 或 移动平均法预测下月需求。
     * 为简化，假设我们根据前N个月的历史消耗量进行预测。
     * 如果没有数据，返回默认值。
     *
     * @param historicalDemands 过去几个月的消耗数量列表，按时间正序排列
     * @return 预测的下个月需求量
     */
    public BigDecimal predictNextMonthDemand(List<Integer> historicalDemands) {
        if (historicalDemands == null || historicalDemands.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // 简单移动平均 (Simple Moving Average)
        double sum = 0;
        for (Integer demand : historicalDemands) {
            sum += demand;
        }
        
        double average = sum / historicalDemands.size();
        return BigDecimal.valueOf(average).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * 计算需求变异系数 (Coefficient of Variation, CV = Standard Deviation / Mean)
     * 用于 XYZ 分类：
     * X类: 需求极稳定, CV < 0.5
     * Y类: 需求不稳定, 0.5 <= CV <= 1.0
     * Z类: 需求随机, CV > 1.0
     */
    public BigDecimal calculateCV(List<Integer> historicalDemands) {
        if (historicalDemands == null || historicalDemands.size() <= 1) {
            // 数据不足，无法计算标准差，默认为高度不稳定的Z类（CV=1.1）
            return new BigDecimal("1.10");
        }
        
        double sum = 0;
        for (Integer demand : historicalDemands) {
            sum += demand;
        }
        double mean = sum / historicalDemands.size();
        
        if (mean == 0) {
            return new BigDecimal("1.10"); // 平均需求为0，变异系数无穷大，归为Z类
        }
        
        double sumOfSquares = 0;
        for (Integer demand : historicalDemands) {
            sumOfSquares += Math.pow(demand - mean, 2);
        }
        double variance = sumOfSquares / (historicalDemands.size() - 1); // 样本方差
        double standardDeviation = Math.sqrt(variance);
        
        double cv = standardDeviation / mean;
        return BigDecimal.valueOf(cv).setScale(4, java.math.RoundingMode.HALF_UP);
    }
}
