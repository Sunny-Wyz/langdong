package com.langdong.spare.service.ai;

import com.langdong.spare.dto.PredictContextDTO;
import com.langdong.spare.entity.AiForecastResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * SBA (Syntetos-Boylan Approximation) 预测算法实现
 * 专用于间断型需求（Intermittent Demand），通过分别平滑非零需求大小和需求间隔来更新。
 */
@Service
public class SbaForecastServiceImpl extends AbstractForecastAlgorithm {

    // 平滑常数（经验值，由于无需动态调参，在此硬编码）
    private static final double ALPHA = 0.15; // 需求大小平滑常数
    private static final double BETA = 0.10; // 需求间隔平滑常数

    @Override
    public AiForecastResult predict(PredictContextDTO ctx) {
        List<Integer> demands = ctx.getDemandHistory();
        if (demands == null || demands.stream().filter(d -> d > 0).count() < AiFeatureService.MIN_DATA_POINTS) {
            return buildFallbackResult(ctx);
        }

        // 1. 初始化 SBA 参数
        // 找到第一个非零需求
        int firstNonZeroIdx = -1;
        for (int i = 0; i < demands.size(); i++) {
            if (demands.get(i) > 0) {
                firstNonZeroIdx = i;
                break;
            }
        }

        double zMean = demands.get(firstNonZeroIdx); // 需求大小平滑值
        double pMean = 1.0; // 需求间隔平滑值（初始假定间隔为1）
        int q = 1; // 距离上次需求的间隔

        List<Double> predictedHistory = new ArrayList<>(demands.size());

        // 2. 遍历历史进行指数平滑
        for (int i = 0; i < demands.size(); i++) {
            int d = demands.get(i);

            // 预测当前期（SBA 调整系数为 1 - BETA/2）
            double forecast = (1.0 - BETA / 2.0) * (zMean / pMean);
            predictedHistory.add(forecast);

            if (d > 0) {
                // 更新需求大小
                zMean = ALPHA * d + (1 - ALPHA) * zMean;
                // 更新需求间隔
                pMean = BETA * q + (1 - BETA) * pMean;
                q = 1; // 重置间隔
            } else {
                q++; // 间隔加1
            }
        }

        // 3. 预测下一期（Month t+1）
        double nextForecast = (1.0 - BETA / 2.0) * (zMean / pMean);

        // 4. 计算 MASE
        // 由于 SBA 处理的是间断需求，朴素预测 MAE 可能极小甚至为0，此处跳过前2个月的热身期计算 MASE
        List<Integer> evalActuals = demands.subList(2, demands.size());
        List<Double> evalPredicts = predictedHistory.subList(2, predictedHistory.size());

        // 5. 置信区间估计（间断需求常近似为泊松或负二项分布，这里用简单的泊松方差近似）
        // 泊松分布下方差约等于均值，90%置信区间 Z ≈ 1.645
        double stddev = Math.sqrt(nextForecast);
        double lower = Math.max(0, nextForecast - 1.645 * stddev);
        double upper = nextForecast + 1.645 * stddev;

        AiForecastResult result = new AiForecastResult();
        result.setPartCode(ctx.getPartCode());
        result.setForecastMonth(ctx.getForecastMonth());
        result.setPredictQty(bd(nextForecast, 2));
        result.setLowerBound(bd(lower, 2));
        result.setUpperBound(bd(upper, 2));
        result.setAlgoType("SBA");
        result.setMase(calcMASE(evalActuals, evalPredicts));
        result.setModelVersion(MODEL_VERSION);

        return result;
    }
}
