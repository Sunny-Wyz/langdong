package com.langdong.spare.forecast.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.langdong.spare.forecast.classify.AbcXyzCalculator;
import com.langdong.spare.forecast.config.XGBoostProperties;
import com.langdong.spare.forecast.feature.FeatureBuilder;
import com.langdong.spare.forecast.feature.PartFeatureContext;
import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.forecast.model.SafetyStockResult;
import com.langdong.spare.forecast.model.TrainingSample;
import com.langdong.spare.forecast.montecarlo.LeadTimeDemandSimulator;
import com.langdong.spare.forecast.service.PredictionService;
import com.langdong.spare.forecast.stage.DemandOccurrenceStage;
import com.langdong.spare.forecast.stage.DemandQuantityStage;
import com.langdong.spare.forecast.stage.TwoStageModel;
import com.langdong.spare.forecast.xgboost.XgbTrainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线评估回测工具（EvaluationRunner，模块 H）。
 *
 * <p>以独立 JUnit 测试执行，无需启动 Spring 上下文或连接 MySQL，保障 100% 本地快速运行。
 * 支持论文对应的切分协议：
 * - 协议 A（前 30 月训练，后 6 月滚动）：评估点预测（wMAPE, MASE）及概率预测（Brier, CRPS, 90%区间覆盖率）。
 * - 协议 B（12 月训练，12 月 holdout）：评估库存绩效（缺货月次、缺货量、服务水平满足率、平均期末库存）。</p>
 */
public class EvaluationRunner {

    private PredictionService predictionService;
    private FeatureBuilder featureBuilder;
    private LeadTimeDemandSimulator leadTimeDemandSimulator;

    @BeforeEach
    void setup() {
        XGBoostProperties props = new XGBoostProperties();
        XgbTrainer trainer = new XgbTrainer(props);
        DemandOccurrenceStage occ = new DemandOccurrenceStage(trainer);
        DemandQuantityStage qty = new DemandQuantityStage(trainer, props);
        predictionService = new PredictionService(occ, qty);
        featureBuilder = new FeatureBuilder();
        com.langdong.spare.forecast.config.ForecastProperties forecastProperties = new com.langdong.spare.forecast.config.ForecastProperties();
        leadTimeDemandSimulator = new LeadTimeDemandSimulator(forecastProperties);
    }

    @Test
    @DisplayName("运行协议 A 滚动预测评估 与 协议 B 库存模拟评估，并输出结构化 JSON 报告")
    void runEvaluations() throws IOException {
        // 1. 生成 5 种典型备件的 36 个月高仿真历史消耗轨迹
        List<String> months = new ArrayList<>();
        YearMonth start = YearMonth.parse("2023-01");
        for (int i = 0; i < 36; i++) {
            months.add(start.plusMonths(i).toString());
        }

        // 构造特征上下文
        List<PartFeatureContext> partContexts = buildHighFidelityContexts(months);

        // 2. 运行协议 A：30月训练，6月滚动回测
        Map<String, Object> protocolAResults = evaluateProtocolA(partContexts, months);

        // 3. 运行协议 B：12月训练，12月 holdout 库存模拟
        Map<String, Object> protocolBResults = evaluateProtocolB(partContexts, months);

        // 4. 组装最终 JSON 报告并写入 target 目录
        Map<String, Object> finalReport = new LinkedHashMap<>();
        finalReport.put("reportName", "Two-Stage Demand Forecasting Evaluation Report");
        finalReport.put("generatedAt", new Date().toString());
        finalReport.put("protocolA", protocolAResults);
        finalReport.put("protocolB", protocolBResults);

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        File targetDir = new File("target");
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        File reportFile = new File(targetDir, "evaluation_report.json");
        try (FileWriter writer = new FileWriter(reportFile)) {
            mapper.writeValue(writer, finalReport);
        }

        System.out.println("====== [评估自检] 报告生成成功: " + reportFile.getAbsolutePath() + " ======");
        assertNotNull(reportFile);
        assertTrue(reportFile.exists());
    }

    // ================================================================
    // 评估协议 A (前 30 月训练，后 6 月滚动)
    // ================================================================
    private Map<String, Object> evaluateProtocolA(List<PartFeatureContext> contexts, List<String> months) {
        int trainEndIdx = 30; // 月份 0~29 作为基础训练
        List<String> testMonths = months.subList(trainEndIdx, months.size()); // 月份 30~35 (共 6 个月滚动)

        double absoluteErrors = 0;
        double sumActuals = 0;
        double brierSum = 0;
        double crpsSum = 0;
        int intervalCoverageCount = 0;
        int intervalCoverageTotal = 0;

        List<Double> maseList = new ArrayList<>();

        // 分类编码 provider mock (用于特征构建)
        com.langdong.spare.forecast.feature.MonthlyClassCodeProvider dummyCodeProvider = (partCode, asOfMonth) -> new int[]{3, 1};

        for (PartFeatureContext ctx : contexts) {
            // 计算 MASE 的 Naive 季节性分母 (12 个月滞后，N=30)
            double naiveDenominator = 0.0;
            int denCount = 0;
            for (int i = 12; i < trainEndIdx; i++) {
                double y_i = ctx.demandOf(months.get(i));
                double y_prev = ctx.demandOf(months.get(i - 12));
                naiveDenominator += Math.abs(y_i - y_prev);
                denCount++;
            }
            double denom = denCount > 0 ? (naiveDenominator / denCount) : 1.0;
            if (denom == 0.0) {
                denom = 1.0; // 兜底避免除以 0
            }

            double partMaseNumeratorSum = 0.0;

            for (int tIdx = trainEndIdx; tIdx < months.size(); tIdx++) {
                String testMonth = months.get(tIdx);
                double y_actual = ctx.demandOf(testMonth);

                // 滚动切分训练集：0 至 tIdx - 1
                List<String> curTrainMonths = months.subList(0, tIdx);
                List<TrainingSample> trainingSamples = buildTrainingSamples(ctx, curTrainMonths, dummyCodeProvider);

                // 训练当期模型
                TwoStageModel curModel = predictionService.train(trainingSamples, months.get(tIdx - 1), "eval-" + testMonth);

                // 推理当期预测
                FeatureVector fv = featureBuilder.buildInferenceVector(ctx, testMonth, dummyCodeProvider, true);
                ForecastResult fr = predictionService.forecast(curModel, fv);

                // 评估 wMAPE & MASE
                double y_pred = fr.getDemandHat();
                absoluteErrors += Math.abs(y_actual - y_pred);
                sumActuals += y_actual;
                partMaseNumeratorSum += Math.abs(y_actual - y_pred);

                // 评估 Brier Score (第一阶段二分类)
                double p_prob = fr.getOccurrenceProb();
                double o_binary = y_actual > 0 ? 1.0 : 0.0;
                brierSum += Math.pow(p_prob - o_binary, 2);

                // 评估 CRPS (蒙特卡洛抽样)
                double[] crpsSamples = leadTimeDemandSimulator.simulateLeadTimeDemandSamples(
                        fr.getOccurrenceProb(), fr.getPositiveQty(), fr.getLowerBound(), fr.getUpperBound(), 30, 200
                );
                crpsSum += calculateCRPS(crpsSamples, y_actual);

                // 评估置信区间经验覆盖率 (仅对有正需求的月份统计)
                if (y_actual > 0) {
                    intervalCoverageTotal++;
                    if (y_actual >= fr.getLowerBound() && y_actual <= fr.getUpperBound()) {
                        intervalCoverageCount++;
                    }
                }
            }

            maseList.add((partMaseNumeratorSum / testMonths.size()) / denom);
        }

        int totalPredictions = contexts.size() * testMonths.size();
        double wmape = sumActuals > 0 ? (absoluteErrors / sumActuals) : 0.0;
        double meanMase = maseList.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double brier = brierSum / totalPredictions;
        double crps = crpsSum / totalPredictions;
        double coverage = intervalCoverageTotal > 0 ? ((double) intervalCoverageCount / intervalCoverageTotal) : 0.0;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("protocol", "3.2.3 (30m train / 6m rolling)");
        report.put("wMAPE", wmape);
        report.put("MASE", meanMase);
        report.put("BrierScore", brier);
        report.put("CRPS", crps);
        report.put("IntervalCoverageRate90", coverage);
        return report;
    }

    // ================================================================
    // 评估协议 B (12 月训练，12 月 holdout 库存绩效模拟)
    // ================================================================
    private Map<String, Object> evaluateProtocolB(List<PartFeatureContext> contexts, List<String> months) {
        int trainEndIdx = 12; // 月份 0~11 作为基础训练
        List<String> holdoutMonths = months.subList(trainEndIdx, 24); // 月份 12~23 作为 12 个月 holdout 测试

        int totalStockoutMonths = 0;
        double totalStockoutQty = 0;
        double totalDemand = 0;
        double totalFilledQty = 0;
        double sumEomInventory = 0;

        com.langdong.spare.forecast.feature.MonthlyClassCodeProvider dummyCodeProvider = (partCode, asOfMonth) -> new int[]{3, 1};

        // 一次性训练 12 个月基础模型
        Map<String, TwoStageModel> trainedModels = new HashMap<>();
        for (PartFeatureContext ctx : contexts) {
            List<String> trainRange = months.subList(0, trainEndIdx);
            List<TrainingSample> trainingSamples = buildTrainingSamples(ctx, trainRange, dummyCodeProvider);
            TwoStageModel model = predictionService.train(trainingSamples, months.get(trainEndIdx - 1), "eval-B");
            trainedModels.put(ctx.getPartCode(), model);
        }

        // 开始对每个备件进行 holdout 月度库存仿真
        for (PartFeatureContext ctx : contexts) {
            TwoStageModel model = trainedModels.get(ctx.getPartCode());

            // 初始预测（用于获取 ROP）
            FeatureVector firstFv = featureBuilder.buildInferenceVector(ctx, holdoutMonths.get(0), dummyCodeProvider, true);
            ForecastResult firstFr = predictionService.forecast(model, firstFv);
            SafetyStockResult ssRes = leadTimeDemandSimulator.calculateSafetyStock(
                    firstFr.getOccurrenceProb(), firstFr.getPositiveQty(), firstFr.getLowerBound(), firstFr.getUpperBound(), 30, 0.95
            );
            int rop = ssRes.getReorderPoint();

            // 仿真参数
            int inventory = rop; // 期初库存 = ROP
            int orderInTransit = 0; // 在途订单量
            boolean orderPlacedLastMonth = false;

            for (String month : holdoutMonths) {
                double demand = ctx.demandOf(month);
                totalDemand += demand;

                // 1. 到货处理 (上月订购的本月期初到达)
                if (orderPlacedLastMonth) {
                    inventory += orderInTransit;
                    orderInTransit = 0;
                    orderPlacedLastMonth = false;
                }

                // 2. 消耗与缺货结算
                if (inventory < demand) {
                    totalStockoutMonths++;
                    totalStockoutQty += (demand - inventory);
                    totalFilledQty += inventory;
                    inventory = 0;
                } else {
                    totalFilledQty += demand;
                    inventory -= demand;
                }

                // 3. 补货触发判断 (本月月末触发)
                if (inventory <= rop) {
                    orderInTransit = rop - inventory;
                    orderPlacedLastMonth = true;
                }

                sumEomInventory += inventory;
            }
        }

        double fillRate = totalDemand > 0 ? (totalFilledQty / totalDemand) : 1.0;
        double avgEomInventory = sumEomInventory / (contexts.size() * holdoutMonths.size());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("protocol", "3.3.3 (12m train / 12m holdout)");
        report.put("StockoutMonths", totalStockoutMonths);
        report.put("StockoutQuantity", totalStockoutQty);
        report.put("FillRate", fillRate);
        report.put("AverageEndOfMonthInventory", avgEomInventory);
        return report;
    }

    // ================================================================
    // 仿真测试辅助方法
    // ================================================================

    private List<TrainingSample> buildTrainingSamples(PartFeatureContext ctx, List<String> trainMonths,
                                                      com.langdong.spare.forecast.feature.MonthlyClassCodeProvider codeProvider) {
        List<TrainingSample> samples = new ArrayList<>();
        for (String m : trainMonths) {
            FeatureVector fv = featureBuilder.buildInferenceVector(ctx, m, codeProvider, true);
            if (!fv.isDataInsufficient()) {
                samples.add(new TrainingSample(fv, ctx.demandOf(m)));
            }
        }
        return samples;
    }

    private double calculateCRPS(double[] samples, double y) {
        int s = samples.length;
        double sum1 = 0;
        for (double x : samples) {
            sum1 += Math.abs(x - y);
        }
        double sum2 = 0;
        for (int i = 0; i < s; i++) {
            for (int j = 0; j < s; j++) {
                sum2 += Math.abs(samples[i] - samples[j]);
            }
        }
        return (sum1 / s) - (sum2 / (2.0 * s * s));
    }

    private List<PartFeatureContext> buildHighFidelityContexts(List<String> months) {
        List<PartFeatureContext> contexts = new ArrayList<>();

        // Part 1: SP001 (A-X 频繁平稳消耗)
        PartFeatureContext c1 = new PartFeatureContext("SP001");
        Random r1 = new Random(101L);
        for (String m : months) {
            // 90% 概率有需求，需求在 [10, 30] 波动
            boolean occur = r1.nextDouble() < 0.90;
            c1.getMonthlyDemand().put(m, occur ? (double) (10 + r1.nextInt(21)) : 0.0);
        }
        contexts.add(c1);

        // Part 2: SP002 (B-Y 中频波动消耗)
        PartFeatureContext c2 = new PartFeatureContext("SP002");
        Random r2 = new Random(202L);
        for (String m : months) {
            boolean occur = r2.nextDouble() < 0.50;
            c2.getMonthlyDemand().put(m, occur ? (double) (3 + r2.nextInt(10)) : 0.0);
        }
        contexts.add(c2);

        // Part 3: SP003 (C-Z 间歇式慢速需求)
        PartFeatureContext c3 = new PartFeatureContext("SP003");
        Random r3 = new Random(303L);
        for (String m : months) {
            boolean occur = r3.nextDouble() < 0.15;
            c3.getMonthlyDemand().put(m, occur ? (double) (1 + r3.nextInt(3)) : 0.0);
        }
        contexts.add(c3);

        return contexts;
    }
}
