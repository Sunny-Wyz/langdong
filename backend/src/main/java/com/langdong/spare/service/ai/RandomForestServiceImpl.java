package com.langdong.spare.service.ai;

import com.langdong.spare.dto.PredictContextDTO;
import com.langdong.spare.entity.AiForecastResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import smile.regression.RandomForest;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.vector.IntVector;
import smile.data.vector.DoubleVector;

import java.util.ArrayList;
import java.util.List;

/**
 * 随机森林 (Random Forest) 预测算法实现
 * 基于 Java Smile ML 库实现，适用于规律型需求。
 */
@Service
public class RandomForestServiceImpl extends AbstractForecastAlgorithm {

    private static final Logger log = LoggerFactory.getLogger(RandomForestServiceImpl.class);

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

            // 构建训练用 DataFrame
            DataFrame trainData = DataFrame.of(
                    IntVector.of("lag1", lag1),
                    IntVector.of("lag2", lag2),
                    DoubleVector.of("roll3", roll3),
                    DoubleVector.of("y", y));

            // 2. 训练随机森林模型
            // ntrees = 50, max_depth = 5 (参数避免过拟合，数据量少时需保持简单)
            Formula formula = Formula.lhs("y");
            RandomForest model = RandomForest.fit(formula, trainData);

            // 3. 构建预测下一期（Month t+1）的特征
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

            // 4. 计算 MASE
            // 预测一下训练集以计算 MAE
            double[] trainPreds = model.predict(trainData);
            List<Double> predictedHistory = new ArrayList<>();
            List<Integer> evalActuals = new ArrayList<>();
            for (int i = 0; i < sampleCount; i++) {
                predictedHistory.add(Math.max(0, trainPreds[i]));
                evalActuals.add((int) y[i]);
            }

            // 5. 置信区间估计 (RF 对此一般用多棵树的预测方差，这里简化为残差标准差近视)
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
}
