package com.langdong.spare.forecast.montecarlo;

import com.langdong.spare.forecast.config.ForecastProperties;
import com.langdong.spare.forecast.model.SafetyStockResult;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 提前期需求蒙特卡洛模拟器（算法 3-2）。
 *
 * <p>基于预测的发生概率、均值、区间上下界推导标准差，并通过蒙特卡洛模拟
 * 生成提前期内的累计需求经验分布，结合服务水平分位数，计算出补货点（ROP）与安全库存（SS）。</p>
 */
@Component
public class LeadTimeDemandSimulator {

    private static final Logger log = LoggerFactory.getLogger(LeadTimeDemandSimulator.class);

    private final ForecastProperties forecastProperties;
    private final TruncatedNormalSampler sampler;

    public LeadTimeDemandSimulator(ForecastProperties forecastProperties) {
        this.forecastProperties = forecastProperties;
        this.sampler = new TruncatedNormalSampler();
    }

    /**
     * 根据两阶段预测的参数计算安全库存与补货点。
     *
     * @param occurrenceProb 发生概率 p_t
     * @param positiveQty    正需求均值 ŷ
     * @param lowerBound     区间下界 L
     * @param upperBound     区间上界 U
     * @param leadTime       采购提前期 L（天）
     * @param serviceLevel   服务水平 α（例如：0.99 / 0.95 / 0.90）
     * @return 安全库存与补货点计算结果
     */
    public SafetyStockResult calculateSafetyStock(double occurrenceProb, double positiveQty,
                                                  double lowerBound, double upperBound,
                                                  int leadTime, double serviceLevel) {
        // 1. 参数校验
        if (occurrenceProb < 0.0 || occurrenceProb > 1.0) {
            throw new IllegalArgumentException("需求发生概率必须在 [0, 1] 之间: " + occurrenceProb);
        }
        if (leadTime < 0) {
            throw new IllegalArgumentException("采购提前期不能为负数: " + leadTime);
        }
        if (serviceLevel < 0.0 || serviceLevel > 1.0) {
            throw new IllegalArgumentException("服务水平必须在 [0, 1] 之间: " + serviceLevel);
        }

        ForecastProperties.MonteCarlo mcConfig = forecastProperties.getMonteCarlo();
        int simulations = mcConfig.getSimulations();

        // 边界保护：若提前期为 0，则不需要备库，直接返回零
        if (leadTime == 0) {
            return new SafetyStockResult(0, 0, serviceLevel, 0.0, 0.0);
        }

        // 调用模拟核心方法
        double[] samples = simulateLeadTimeDemandSamples(occurrenceProb, positiveQty, lowerBound, upperBound, leadTime, simulations);

        // 计算统计指标与分位数
        double sampleMean = 0.0;
        for (double val : samples) {
            sampleMean += val;
        }
        sampleMean /= simulations;

        // 计算服务水平 α 对应的经验分位数
        Percentile percentile = new Percentile();
        percentile.setData(samples);
        double leadTimeDemandQuantile = percentile.evaluate(serviceLevel * 100.0);

        // 补货点 ROP = ceil( Quantile(samples, α) )
        int reorderPoint = (int) Math.ceil(leadTimeDemandQuantile);

        // 安全库存 SS = ROP − ceil( Mean(samples) )
        int meanCeil = (int) Math.ceil(sampleMean);
        int safetyStock = reorderPoint - meanCeil;

        // 诊断日志
        log.debug("[蒙特卡洛] 计算完成: p={}, μ={}, sd={}, L={}, α={}, ROP={}, SS={}, 样本均值={}",
                occurrenceProb, positiveQty, (upperBound - lowerBound) / (2.0 * mcConfig.getIntervalZ()), leadTime, serviceLevel, reorderPoint, safetyStock, sampleMean);

        return new SafetyStockResult(reorderPoint, safetyStock, serviceLevel, sampleMean, leadTimeDemandQuantile);
    }

    /**
     * 导出模拟样本集合，用于评估计算（如 CRPS）。
     */
    public double[] simulateLeadTimeDemandSamples(double occurrenceProb, double positiveQty,
                                                  double lowerBound, double upperBound,
                                                  int leadTime, int simulations) {
        ForecastProperties.MonteCarlo mcConfig = forecastProperties.getMonteCarlo();
        int workingDays = mcConfig.getWorkingDays();
        double intervalZ = mcConfig.getIntervalZ();
        long seed = mcConfig.getSeed();

        if (workingDays <= 0) {
            throw new IllegalArgumentException("月工作天数必须大于 0: " + workingDays);
        }
        if (simulations <= 0) {
            throw new IllegalArgumentException("模拟次数必须大于 0: " + simulations);
        }

        // 由 90% 区间反推条件标准差： σ_t = (U_t* - L_t*) / (2 * 1.645)
        double sd = (upperBound - lowerBound) / (2.0 * intervalZ);
        if (sd < 0) {
            sd = 0.0;
        }

        // 初始化共享种子的随机数生成器，保证 100% 可复现
        JDKRandomGenerator rng = new JDKRandomGenerator();
        rng.setSeed(seed);

        double[] samples = new double[simulations];

        // 蒙特卡洛模拟循环
        for (int m = 0; m < simulations; m++) {
            // 触发日：均匀分布整数 s ∈ [1, W]
            int s = rng.nextInt(workingDays) + 1;

            double accumulatedDemand = 0.0;
            int remainingDays = leadTime;
            int daysInCurrentMonth = workingDays - s + 1;

            // 跨月/多月通用采样逻辑
            while (remainingDays > 0) {
                int daysToTake = Math.min(remainingDays, daysInCurrentMonth);

                // 采样发生状态 I ~ Bernoulli(p_t)
                int i = (rng.nextDouble() < occurrenceProb) ? 1 : 0;

                // 采样正需求量 Y ~ TruncatedNormal(mean, sd, 0)
                double y = sampler.sample(rng, positiveQty, sd);

                double dMonth = i * y;
                accumulatedDemand += (daysToTake / (double) workingDays) * dMonth;

                // 扣减剩余提前期，并将下一个月的可用工作日重置为整个月工作日 W
                remainingDays -= daysToTake;
                daysInCurrentMonth = workingDays;
            }

            samples[m] = accumulatedDemand;
        }

        return samples;
    }
}
