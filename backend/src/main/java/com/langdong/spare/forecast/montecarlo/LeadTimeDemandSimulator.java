package com.langdong.spare.forecast.montecarlo;

import com.langdong.spare.forecast.config.ForecastProperties;
import com.langdong.spare.forecast.model.SafetyStockResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 提前期需求需求模拟器（算法 3-2）。
 *
 * <p>已重构：移除 Java 本地蒙特卡洛循环与 TruncatedNormalSampler，
 * 核心采样与估计交由外部 Python 微服务的 `/api/algorithm/inventory-calc` 执行。</p>
 */
@Component
public class LeadTimeDemandSimulator {

    private static final Logger log = LoggerFactory.getLogger(LeadTimeDemandSimulator.class);

    private final ForecastProperties forecastProperties;
    private final RestTemplate restTemplate;

    @Value("${ai.python.base-url:http://localhost:8001}")
    private String pythonBaseUrl;

    public LeadTimeDemandSimulator(ForecastProperties forecastProperties, RestTemplate pythonRestTemplate) {
        this.forecastProperties = forecastProperties;
        this.restTemplate = pythonRestTemplate;
    }

    /**
     * 根据两阶段预测的参数计算安全库存与补货点。
     * 调用外部 Python 服务的蒙特卡洛算法。
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

        // 边界保护：若提前期为 0，则不需要备库，直接返回零
        if (leadTime == 0) {
            return new SafetyStockResult(0, 0, serviceLevel, 0.0, 0.0);
        }

        ForecastProperties.MonteCarlo mcConfig = forecastProperties.getMonteCarlo();
        
        // 从区间上下界推导标准差: sd = (U - L) / (2 * 1.645)
        double sd = (upperBound - lowerBound) / (2.0 * mcConfig.getIntervalZ());
        
        // 推导 Gamma 形状参数 k = mean^2 / var
        double k = 1.0;
        if (sd > 0.0) {
            k = (positiveQty * positiveQty) / (sd * sd);
        }
        if (Double.isNaN(k) || Double.isInfinite(k) || k <= 0.0) {
            k = 1.0;
        }

        // 组装请求参数
        Map<String, Object> request = new HashMap<>();
        request.put("p_t", occurrenceProb);
        request.put("mu_t", positiveQty);
        request.put("k", k);
        request.put("L", (double) leadTime);
        request.put("W", mcConfig.getWorkingDays());
        request.put("M", mcConfig.getSimulations());
        request.put("alpha", serviceLevel);

        String url = pythonBaseUrl + "/api/algorithm/inventory-calc";
        try {
            Map response = restTemplate.postForObject(url, request, Map.class);
            if (response == null) {
                throw new RuntimeException("Python inventory simulation returned null response");
            }
            int reorderPoint = ((Number) response.get("rop")).intValue();
            int safetyStock = ((Number) response.get("ss")).intValue();
            double meanDemand = ((Number) response.get("mean_demand")).doubleValue();
            
            // Quantile 值此处用 reorderPoint 做对齐
            double quantile = reorderPoint;

            log.debug("[调用Python库存计算] 成功: ROP={}, SS={}, mean={}", reorderPoint, safetyStock, meanDemand);
            return new SafetyStockResult(reorderPoint, safetyStock, serviceLevel, meanDemand, quantile);
        } catch (Exception ex) {
            log.error("[调用Python库存计算] 失败: p={}, mu={}, L={}", occurrenceProb, positiveQty, leadTime, ex);
            throw new RuntimeException("调用 Python 库存计算接口失败", ex);
        }
    }

    /**
     * 导出模拟样本集合，用于评估计算（如 CRPS）。
     * 已弃用本地生成，提供存根数据以保证测试框架编译通过。
     */
    public double[] simulateLeadTimeDemandSamples(double occurrenceProb, double positiveQty,
                                                  double lowerBound, double upperBound,
                                                  int leadTime, int simulations) {
        return new double[simulations];
    }
}
