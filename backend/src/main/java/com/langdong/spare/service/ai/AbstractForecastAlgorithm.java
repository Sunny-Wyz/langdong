package com.langdong.spare.service.ai;

import com.langdong.spare.dto.PredictContextDTO;
import com.langdong.spare.entity.AiForecastResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 预测算法抽象基类
 *
 * 所有预测算法（RF、SBA、Fallback）继承此类，实现 predict() 方法。
 * 提供 MASE 评估指标计算的公共实现。
 */
public abstract class AbstractForecastAlgorithm {

    protected static final String MODEL_VERSION = "v1.0";

    /**
     * 对一个备件执行预测，返回填充好字段的 AiForecastResult
     *
     * @param ctx 预测上下文（含历史消耗序列、算法类型、提前期等）
     * @return 预测结果实体（不含 id 和 createTime，由 Mapper 插入时填充）
     */
    public abstract AiForecastResult predict(PredictContextDTO ctx);

    /**
     * 计算 MASE（平均绝对比例误差）
     * <p>
     * MASE = MAE(预测) / MAE(朴素预测)
     * 朴素预测：用 t-1 期预测 t 期
     * </p>
     *
     * @param actuals   实际消耗量列表（历史部分，用于评估）
     * @param predicted 模型对历史各期的预测值（剔除最后一期，留作评估）
     * @return MASE 值，若无法计算（历史过短）则返回 null
     */
    protected BigDecimal calcMASE(List<Integer> actuals, List<Double> predicted) {
        if (actuals == null || predicted == null || actuals.size() < 2 || predicted.size() < 1) {
            return null;
        }
        int n = Math.min(actuals.size(), predicted.size());

        // MAE of model
        double maeModel = IntStream.range(0, n)
                .mapToDouble(i -> Math.abs(actuals.get(i) - predicted.get(i)))
                .average().orElse(0.0);

        // MAE of naive (lag-1 forecast): 从第2期开始
        double maeNaive = IntStream.range(1, actuals.size())
                .mapToDouble(i -> Math.abs(actuals.get(i) - actuals.get(i - 1)))
                .average().orElse(0.0);

        if (maeNaive == 0)
            return null; // 避免除零

        double mase = maeModel / maeNaive;
        return BigDecimal.valueOf(mase).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 构造一个空的（Fallback）预测结果
     * 当历史数据不足时，使用最近1个月的均值作为预测值，置信区间设为均值 ± 均值*50%
     */
    protected AiForecastResult buildFallbackResult(PredictContextDTO ctx) {
        List<Integer> demands = ctx.getDemandHistory();
        // 取最近一个非零月或0
        double recent = (demands == null || demands.isEmpty()) ? 0.0
                : demands.stream().filter(d -> d > 0)
                        .mapToInt(Integer::intValue).average().orElse(0.0);

        AiForecastResult result = new AiForecastResult();
        result.setPartCode(ctx.getPartCode());
        result.setForecastMonth(ctx.getForecastMonth());
        result.setPredictQty(bd(recent, 2));
        result.setLowerBound(bd(recent * 0.5, 2));
        result.setUpperBound(bd(recent * 1.5, 2));
        result.setAlgoType("FALLBACK");
        result.setMase(null);
        result.setModelVersion(MODEL_VERSION);
        return result;
    }

    protected static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
