package com.langdong.spare.forecast.stage;

import com.langdong.spare.forecast.config.XGBoostProperties;
import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.forecast.model.TrainingSample;
import com.langdong.spare.forecast.service.PredictionService;
import com.langdong.spare.forecast.xgboost.XgbTrainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 两阶段模型数值单测（测试要求 5）：D_hat = p × ŷ、L ≤ U、概率随驱动特征区分。
 */
public class TwoStageNumericTest {

    private PredictionService predictionService;

    @BeforeEach
    void setup() {
        XGBoostProperties props = new XGBoostProperties();
        XgbTrainer trainer = new XgbTrainer(props);
        DemandOccurrenceStage occ = new DemandOccurrenceStage(trainer);
        DemandQuantityStage qty = new DemandQuantityStage(trainer, props);
        predictionService = new PredictionService(occ, qty);
    }

    /** 由 lag1 驱动的合成样本：lag1 大 → 更可能发生、量更大。 */
    private List<TrainingSample> synth(int n, long seed) {
        Random rnd = new Random(seed);
        String[] months = new String[24];
        for (int i = 0; i < 24; i++) {
            months[i] = String.format("2024-%02d", (i % 12) + 1);
        }
        List<TrainingSample> samples = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double lag1 = rnd.nextInt(11); // 0~10
            boolean occur = lag1 > 3 ? rnd.nextDouble() < 0.9 : rnd.nextDouble() < 0.1;
            double demand = occur ? Math.round(5 + 0.8 * lag1 + rnd.nextGaussian()) : 0;
            if (demand < 0) {
                demand = 0;
            }
            FeatureVector fv = feature(lag1, months[i % months.length]);
            samples.add(new TrainingSample(fv, demand));
        }
        return samples;
    }

    private FeatureVector feature(double lag1, String month) {
        FeatureVector fv = new FeatureVector();
        fv.setPartCode("SP" + (int) lag1);
        fv.setTargetMonth(month);
        fv.setLag1(lag1);
        fv.setLag3Mean(lag1);
        fv.setLag3Std(1.0);
        fv.setZeroRatio6(lag1 > 3 ? 0.2 : 0.8);
        fv.setEquipHr(100);
        fv.setRepairCnt(lag1 > 3 ? 2 : 0);
        fv.setMonth(6);
        fv.setAbcCode(2);
        fv.setXyzCode(2);
        fv.setPosLag1(lag1);
        fv.setPosLag3Mean(lag1);
        return fv;
    }

    @Test
    @DisplayName("D_hat = p × ŷ，且 L ≤ U，概率落 [0,1]")
    void testDhatAndInterval() {
        TwoStageModel model = predictionService.train(synth(400, 1L), "2024-12", "test-v1");

        FeatureVector probe = feature(8, "2025-01");
        ForecastResult r = predictionService.forecast(model, probe);

        assertTrue(r.getOccurrenceProb() >= 0 && r.getOccurrenceProb() <= 1, "概率须落 [0,1]");
        assertEquals(r.getOccurrenceProb() * r.getPositiveQty(), r.getDemandHat(), 1e-5,
                "总需求点估计必须等于 p × ŷ");
        assertTrue(r.getLowerBound() <= r.getUpperBound(), "区间下界须 ≤ 上界");
        assertTrue(r.getPositiveQty() >= 0, "正需求量非负");
        assertEquals("test-v1", r.getModelVersion());
    }

    @Test
    @DisplayName("发生概率随驱动特征 lag1 单调区分（高 > 低）")
    void testProbabilityDiscrimination() {
        TwoStageModel model = predictionService.train(synth(500, 2L), "2024-12", "test-v2");

        double pHigh = predictionService.forecast(model, feature(9, "2025-01")).getOccurrenceProb();
        double pLow = predictionService.forecast(model, feature(0, "2025-01")).getOccurrenceProb();
        assertTrue(pHigh > pLow, "lag1 高应比 lag1 低有更高发生概率: high=" + pHigh + " low=" + pLow);
    }

    @Test
    @DisplayName("数据不足特征向量 → 返回标记结果，不抛异常")
    void testInsufficient() {
        TwoStageModel model = predictionService.train(synth(300, 3L), "2024-12", "test-v3");

        FeatureVector fv = new FeatureVector();
        fv.setPartCode("NEW001");
        fv.setTargetMonth("2025-01");
        fv.setDataInsufficient(true);
        fv.setInsufficientReason("无历史");

        ForecastResult r = assertDoesNotThrow(() -> predictionService.forecast(model, fv));
        assertTrue(r.isDataInsufficient());
        assertEquals("test-v3", r.getModelVersion());
    }
}
