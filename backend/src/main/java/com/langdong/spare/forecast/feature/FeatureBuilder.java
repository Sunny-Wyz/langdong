package com.langdong.spare.forecast.feature;

import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.TrainingSample;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 特征构造器（论文表 3-2，模块 B）。
 *
 * <p><b>防泄露铁律：</b>为目标月 t 构造特征时，只读取「严格早于 t」的历史数据；月份季节编码
 * 使用 t 的日历月份（提前可确定，非泄露）。任何读取 ≥ t 月消耗/设备数据的行为都视为严重 bug，
 * 由 {@code FeatureBuilderLeakTest} 断言防守。</p>
 *
 * <p>纯逻辑、无 DB 依赖（数据经 {@link PartFeatureContext} 注入，分类码经
 * {@link MonthlyClassCodeProvider} 注入），既可被在线推理复用，也可离线批量构造训练矩阵。</p>
 */
@Component
public class FeatureBuilder {

    /** 近 N 个月零需求占比的窗口长度。 */
    private static final int ZERO_RATIO_WINDOW = 6;
    /** 近 N 个月滞后统计（均值/标准差）的窗口长度。 */
    private static final int LAG_WINDOW = 3;
    /** 正需求滞后均值取最近 N 次正需求。 */
    private static final int POS_LAG_WINDOW = 3;

    /**
     * 为目标月构造单条推理特征向量。
     *
     * @param ctx           备件历史数据
     * @param targetMonth   预测目标月（yyyy-MM）
     * @param codeProvider  分类码提供者
     * @param includeStage2 是否填充阶段二 2 维正需求滞后特征
     * @return 特征向量；若目标月前无任何历史，返回标记 {@code dataInsufficient=true} 的向量
     */
    public FeatureVector buildInferenceVector(PartFeatureContext ctx, String targetMonth,
                                              MonthlyClassCodeProvider codeProvider, boolean includeStage2) {
        FeatureVector fv = new FeatureVector();
        fv.setPartCode(ctx.getPartCode());
        fv.setTargetMonth(targetMonth);

        YearMonth target = YearMonth.parse(targetMonth);

        // 防泄露 + 数据不足判定：目标月之前必须至少有一个消耗历史月
        if (!hasHistoryBefore(ctx, target)) {
            fv.setDataInsufficient(true);
            fv.setInsufficientReason("目标月 " + targetMonth + " 之前无任何消耗历史（疑似新备件）");
            return fv;
        }

        String prev1 = target.minusMonths(1).toString();

        // #1 lag_1
        fv.setLag1(ctx.demandOf(prev1));

        // #2/#3 近3月均值与样本标准差
        double[] lag3 = new double[LAG_WINDOW];
        for (int i = 0; i < LAG_WINDOW; i++) {
            lag3[i] = ctx.demandOf(target.minusMonths(i + 1L).toString());
        }
        fv.setLag3Mean(mean(lag3));
        fv.setLag3Std(sampleStd(lag3));

        // #4 近6月零需求占比
        int zeroCount = 0;
        for (int i = 0; i < ZERO_RATIO_WINDOW; i++) {
            if (ctx.demandOf(target.minusMonths(i + 1L).toString()) == 0.0) {
                zeroCount++;
            }
        }
        fv.setZeroRatio6((double) zeroCount / ZERO_RATIO_WINDOW);

        // #5/#6 上月设备运行时长 / 维修工单数
        fv.setEquipHr(ctx.equipHrOf(prev1));
        fv.setRepairCnt(ctx.repairCntOf(prev1));

        // #7 月份季节编码（整数，⚠️默认）
        fv.setMonth(target.getMonthValue());

        // #8/#9 ABC_code / XYZ_code（取目标月前一月的分类结果）
        int[] codes = safeCodes(codeProvider, ctx.getPartCode(), prev1);
        fv.setAbcCode(codes[0]);
        fv.setXyzCode(codes[1]);

        // #10/#11 阶段二正需求滞后
        if (includeStage2) {
            fillPositiveLags(ctx, target, fv);
        }

        return fv;
    }

    /**
     * 构造该备件的全部训练样本（滚动，每个可用历史月一条）。
     *
     * <p>为每个「其之前存在历史」的月 t 生成一条样本：特征仅用 &lt; t 数据，标签为 t 月实际消耗。
     * 阶段二正需求滞后同样填充，训练阶段二时再按 {@code demand>0} 筛子集。</p>
     *
     * @param ctx           备件历史数据
     * @param labelMonths   需要生成标签的月份列表（yyyy-MM，通常为该备件有数据覆盖的月份，升序）
     * @param codeProvider  分类码提供者
     * @return 训练样本列表（按 labelMonths 顺序）
     */
    public List<TrainingSample> buildTrainingSamples(PartFeatureContext ctx, List<String> labelMonths,
                                                     MonthlyClassCodeProvider codeProvider) {
        List<TrainingSample> samples = new ArrayList<>();
        if (labelMonths == null) {
            return samples;
        }
        for (String month : labelMonths) {
            YearMonth target = YearMonth.parse(month);
            if (!hasHistoryBefore(ctx, target)) {
                continue; // 该月之前无历史，无法构造无泄露特征，跳过
            }
            FeatureVector fv = buildInferenceVector(ctx, month, codeProvider, true);
            if (fv.isDataInsufficient()) {
                continue;
            }
            samples.add(new TrainingSample(fv, ctx.demandOf(month)));
        }
        return samples;
    }

    // ================================================================
    // 内部工具
    // ================================================================

    /** 目标月之前是否存在任一消耗历史月。 */
    private boolean hasHistoryBefore(PartFeatureContext ctx, YearMonth target) {
        for (String m : ctx.getMonthlyDemand().keySet()) {
            if (YearMonth.parse(m).isBefore(target)) {
                return true;
            }
        }
        return false;
    }

    /** 填充 #10 pos_lag_1 与 #11 pos_lag_3_mean（仅回看严格早于目标月的正需求）。 */
    private void fillPositiveLags(PartFeatureContext ctx, YearMonth target, FeatureVector fv) {
        List<Double> recentPositives = new ArrayList<>();
        // 从前一月起向前回看，最多回看一个合理窗口（历史跨度）
        YearMonth cursor = target.minusMonths(1);
        // 回看上限：不早于最早历史月；用一个较大的安全上限防止死循环
        int maxLookback = 600; // 50 年，足够覆盖任何真实历史
        for (int i = 0; i < maxLookback && recentPositives.size() < POS_LAG_WINDOW; i++) {
            double d = ctx.demandOf(cursor.toString());
            if (d > 0) {
                recentPositives.add(d);
            }
            cursor = cursor.minusMonths(1);
        }
        if (!recentPositives.isEmpty()) {
            fv.setPosLag1(recentPositives.get(0));
            double sum = 0;
            for (double v : recentPositives) {
                sum += v;
            }
            fv.setPosLag3Mean(sum / recentPositives.size());
        } else {
            fv.setPosLag1(0.0);
            fv.setPosLag3Mean(0.0);
        }
    }

    private int[] safeCodes(MonthlyClassCodeProvider provider, String partCode, String asOfMonth) {
        if (provider == null) {
            return new int[]{1, 3}; // 默认 C/Z
        }
        int[] codes = provider.codesAsOf(partCode, asOfMonth);
        if (codes == null || codes.length < 2) {
            return new int[]{1, 3};
        }
        return codes;
    }

    private static double mean(double[] xs) {
        if (xs.length == 0) {
            return 0.0;
        }
        double sum = 0;
        for (double x : xs) {
            sum += x;
        }
        return sum / xs.length;
    }

    /** 样本标准差（除以 n-1）；样本数 &lt; 2 时返回 0。 */
    private static double sampleStd(double[] xs) {
        if (xs.length < 2) {
            return 0.0;
        }
        double m = mean(xs);
        double ss = 0;
        for (double x : xs) {
            ss += (x - m) * (x - m);
        }
        return Math.sqrt(ss / (xs.length - 1));
    }
}
