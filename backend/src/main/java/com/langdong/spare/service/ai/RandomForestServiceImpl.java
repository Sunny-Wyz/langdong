package com.langdong.spare.service.ai;

import com.langdong.spare.dto.PredictContextDTO;
import com.langdong.spare.entity.AiForecastResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import smile.regression.RandomForest;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;
import smile.data.vector.DoubleVector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机森林 (Random Forest) 预测算法实现
 * 基于 Java Smile ML 库实现，适用于规律型需求。
 * 使用按时间距离的指数衰减加权采样，使近期样本对模型影响更大。
 */
@Service
public class RandomForestServiceImpl extends AbstractForecastAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(RandomForestServiceImpl.class);

    /** 默认半衰期（月）：3个月前的样本权重衰减为一半 */
    private static final double DEFAULT_HALF_LIFE = 3.0;

    @Override
    public AiForecastResult predict(PredictContextDTO ctx) {
        List<Integer> demands = ctx.getDemandHistory();
        if (demands == null || demands.stream().filter(d -> d > 0).count() < AiFeatureService.MIN_DATA_POINTS) {
            return buildFallbackResult(ctx);
        }

        try {
            // 1. 构造特征矩阵
            // 我们用 lag1, lag2, 和 roll_mean_3 预测当前期 t
            // 为了有意义的训练集，我们需要至少能构造出一些样本
            // 如果只有 12 个历史数据点:
            // i=3: lag1=d[2], lag2=d[1], roll3=avg(d[0..2]), y=d[3]
            // ...以此类推，共得到 12-3 = 9 个样本用于训练
            int n = demands.size();
            int window = 3;
            if (n <= window) {
                return buildFallbackResult(ctx);
            }

            int sampleCount = n - window;
            int[] lag1 = new int[sampleCount];
            int[] lag2 = new int[sampleCount];
            double[] roll3 = new double[sampleCount];
            double[] y = new double[sampleCount];

            for (int i = window; i < n; i++) {
                int idx = i - window;
                lag1[idx] = demands.get(i - 1);
                lag2[idx] = demands.get(i - 2);
                roll3[idx] = (demands.get(i - 1) + demands.get(i - 2) + demands.get(i - 3)) / 3.0;
                y[idx] = demands.get(i);
            }

            // 2. 计算指数衰减权重（按时间距离，近期样本权重更高）
            // Smile 3.1.0 RandomForest.fit() 不支持权重参数，通过样本复制模拟加权采样
            double[] rawWeight = calcTimeDecayWeights(sampleCount, DEFAULT_HALF_LIFE);
            log.debug("[RF预测] 原始权重 (半衰期={}月): {}", DEFAULT_HALF_LIFE, java.util.Arrays.toString(rawWeight));

            // 3. 按权重复制样本，构建加权训练集
            List<Integer> wLag1 = new ArrayList<>();
            List<Integer> wLag2 = new ArrayList<>();
            List<Double> wRoll3 = new ArrayList<>();
            List<Double> wY = new ArrayList<>();

            for (int i = 0; i < sampleCount; i++) {
                int copies = (int) rawWeight[i];
                double frac = rawWeight[i] - copies;
                // 概率复制：小数部分决定是否多复制一份
                if (ThreadLocalRandom.current().nextDouble() < frac) {
                    copies++;
                }
                for (int c = 0; c < copies; c++) {
                    wLag1.add(lag1[i]);
                    wLag2.add(lag2[i]);
                    wRoll3.add(roll3[i]);
                    wY.add(y[i]);
                }
            }

            int weightedSize = wY.size();
            int[] wLag1Arr = wLag1.stream().mapToInt(Integer::intValue).toArray();
            int[] wLag2Arr = wLag2.stream().mapToInt(Integer::intValue).toArray();
            double[] wRoll3Arr = wRoll3.stream().mapToDouble(Double::doubleValue).toArray();
            double[] wYArr = wY.stream().mapToDouble(Double::doubleValue).toArray();

            DataFrame trainData = DataFrame.of(
                    IntVector.of("lag1", wLag1Arr),
                    IntVector.of("lag2", wLag2Arr),
                    DoubleVector.of("roll3", wRoll3Arr),
                    DoubleVector.of("y", wYArr));

            log.debug("[RF预测] 加权训练集: 原始样本={}, 加权后样本={}", sampleCount, weightedSize);

            // 4. 训练随机森林模型
            Formula formula = Formula.lhs("y");
            RandomForest model = RandomForest.fit(formula, trainData);

            // 5. 构建预测下一期（Month t+1）的特征
            double nextLag1 = demands.get(n - 1);
            double nextLag2 = demands.get(n - 2);
            double nextRoll3 = (demands.get(n - 1) + demands.get(n - 2) + demands.get(n - 3)) / 3.0;

            DataFrame predictData = DataFrame.of(
                    DoubleVector.of("lag1", new double[] { nextLag1 }),
                    DoubleVector.of("lag2", new double[] { nextLag2 }),
                    DoubleVector.of("roll3", new double[] { nextRoll3 }));

            // 预测下一期值
            double[] predictions = model.predict(predictData);
            double nextForecast = Math.max(0, predictions[0]); // 需求不能为负

            // 6. 计算 MASE（用原始样本评估，不用加权后的）
            DataFrame origTrainData = DataFrame.of(
                    IntVector.of("lag1", lag1),
                    IntVector.of("lag2", lag2),
                    DoubleVector.of("roll3", roll3),
                    DoubleVector.of("y", y));
            double[] trainPreds = model.predict(origTrainData);
            List<Double> predictedHistory = new ArrayList<>();
            List<Integer> evalActuals = new ArrayList<>();
            for (int i = 0; i < sampleCount; i++) {
                predictedHistory.add(Math.max(0, trainPreds[i]));
                evalActuals.add((int) y[i]);
            }

            // 7. 置信区间估计 (简化为残差标准差近似)
            double mse = 0;
            for (int i = 0; i < sampleCount; i++) {
                mse += Math.pow(y[i] - trainPreds[i], 2);
            }
            double rmse = Math.sqrt(mse / sampleCount);
            // 90% 区间 Z = 1.645
            double lower = Math.max(0, nextForecast - 1.645 * rmse);
            double upper = nextForecast + 1.645 * rmse;

            AiForecastResult result = new AiForecastResult();
            result.setPartCode(ctx.getPartCode());
            result.setForecastMonth(ctx.getForecastMonth());
            result.setPredictQty(bd(nextForecast, 2));
            result.setLowerBound(bd(lower, 2));
            result.setUpperBound(bd(upper, 2));
            result.setAlgoType("RF");
            result.setMase(calcMASE(evalActuals, predictedHistory));
            result.setModelVersion(MODEL_VERSION);

            return result;

        } catch (Exception e) {
            log.error("[RF预测] 算法训练或执行异常, code: {}", ctx.getPartCode(), e);
            // 发生异常时退化为 Fallback
            return buildFallbackResult(ctx);
        }
    }

    /**
     * 计算指数衰减权重数组（已归一化到均值=1）
     * <p>
     * 样本索引 i ∈ [0, sampleCount-1]，i=0 为最旧，i=sampleCount-1 为最新。
     * 原始权重 w(i) = exp(-λ * Δt)，其中 Δt = (sampleCount - 1) - i，λ = ln(2) / halfLife。
     * 归一化：w'(i) = w(i) / mean(w)，使均值 = 1。
     * </p>
     *
     * @param sampleCount 样本数量
     * @param halfLife    半衰期（权重衰减到一半所需的时间单位数）
     * @return 归一化后的权重数组，长度 = sampleCount
     */
    static double[] calcTimeDecayWeights(int sampleCount, double halfLife) {
        double lambda = Math.log(2) / halfLife;
        double[] weights = new double[sampleCount];
        double sum = 0;
        for (int i = 0; i < sampleCount; i++) {
            double deltaT = (sampleCount - 1) - i;
            weights[i] = Math.exp(-lambda * deltaT);
            sum += weights[i];
        }
        // 归一化到均值 = 1
        double mean = sum / sampleCount;
        for (int i = 0; i < sampleCount; i++) {
            weights[i] /= mean;
        }
        return weights;
    }
}
