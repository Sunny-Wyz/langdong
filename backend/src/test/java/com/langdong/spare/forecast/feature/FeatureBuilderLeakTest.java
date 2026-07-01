package com.langdong.spare.forecast.feature;

import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.TrainingSample;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FeatureBuilder 防泄露与特征正确性单元测试（对应测试要求 1、5、6）。
 *
 * <p>核心不变量：为目标月 t 构造特征时，绝不读取 ≥ t 月的消耗 / 设备数据。</p>
 */
public class FeatureBuilderLeakTest {

    private final FeatureBuilder builder = new FeatureBuilder();

    /** 固定分类码提供者（本测试聚焦特征读取泄露，分类泄露由分类器自身保证）。 */
    private final MonthlyClassCodeProvider constCodes = (part, month) -> new int[]{3, 1};

    private PartFeatureContext buildSampleContext() {
        PartFeatureContext ctx = new PartFeatureContext("SP0001");
        // 2026-01 ~ 2026-06 为历史，2026-07（目标月）及之后为「未来」，不得被读取
        ctx.getMonthlyDemand().put("2026-01", 5.0);
        ctx.getMonthlyDemand().put("2026-02", 0.0);
        ctx.getMonthlyDemand().put("2026-03", 3.0);
        ctx.getMonthlyDemand().put("2026-04", 0.0);
        ctx.getMonthlyDemand().put("2026-05", 0.0);
        ctx.getMonthlyDemand().put("2026-06", 4.0);
        ctx.getMonthlyDemand().put("2026-07", 999.0); // 目标月，禁止读取
        ctx.getMonthlyDemand().put("2026-08", 888.0); // 未来，禁止读取

        ctx.getMonthlyEquipHr().put("2026-06", 100.0);
        ctx.getMonthlyEquipHr().put("2026-07", 9999.0);
        ctx.getMonthlyRepairCnt().put("2026-06", 2.0);
        ctx.getMonthlyRepairCnt().put("2026-07", 99.0);
        return ctx;
    }

    @Test
    @DisplayName("防泄露：修改目标月及未来数据不改变特征向量")
    void testNoFutureLeak() {
        PartFeatureContext ctx = buildSampleContext();
        FeatureVector before = builder.buildInferenceVector(ctx, "2026-07", constCodes, true);

        // 篡改 >= 目标月 的所有数据
        ctx.getMonthlyDemand().put("2026-07", -12345.0);
        ctx.getMonthlyDemand().put("2026-08", -6789.0);
        ctx.getMonthlyDemand().put("2026-09", 777.0);
        ctx.getMonthlyEquipHr().put("2026-07", -1.0);
        ctx.getMonthlyRepairCnt().put("2026-07", -1.0);

        FeatureVector after = builder.buildInferenceVector(ctx, "2026-07", constCodes, true);

        assertArrayEquals(before.toStage2Array(), after.toStage2Array(), 1e-6f,
                "篡改目标月及未来数据后特征向量发生变化 → 存在未来信息泄露");
        assertFalse(before.isDataInsufficient());
    }

    @Test
    @DisplayName("特征数值正确性（阶段一/阶段二 11 维）")
    void testFeatureValues() {
        FeatureVector fv = builder.buildInferenceVector(buildSampleContext(), "2026-07", constCodes, true);

        assertEquals(4.0, fv.getLag1(), 1e-9, "lag_1 应为前一月 2026-06 的 4");
        // lag3 = [06=4, 05=0, 04=0] → mean = 4/3
        assertEquals(4.0 / 3.0, fv.getLag3Mean(), 1e-9);
        // 样本标准差(除以 n-1) of [4,0,0]
        double m = 4.0 / 3.0;
        double expStd = Math.sqrt(((4 - m) * (4 - m) + (0 - m) * (0 - m) + (0 - m) * (0 - m)) / 2.0);
        assertEquals(expStd, fv.getLag3Std(), 1e-9);
        // zero_ratio_6：06..01 = [4,0,0,3,0,5] → 3 个 0
        assertEquals(3.0 / 6.0, fv.getZeroRatio6(), 1e-9);
        assertEquals(100.0, fv.getEquipHr(), 1e-9);
        assertEquals(2.0, fv.getRepairCnt(), 1e-9);
        assertEquals(7.0, fv.getMonth(), 1e-9);
        assertEquals(3.0, fv.getAbcCode(), 1e-9);
        assertEquals(1.0, fv.getXyzCode(), 1e-9);
        // pos_lag_1：最近正需求 2026-06=4
        assertEquals(4.0, fv.getPosLag1(), 1e-9);
        // pos_lag_3_mean：最近三次正需求 06=4,03=3,01=5 → 4.0
        assertEquals(4.0, fv.getPosLag3Mean(), 1e-9);
    }

    @Test
    @DisplayName("阶段一不填充阶段二特征（pos_lag 保持 0）")
    void testStage1OnlyLeavesPosLagsZero() {
        FeatureVector fv = builder.buildInferenceVector(buildSampleContext(), "2026-07", constCodes, false);
        assertEquals(0.0, fv.getPosLag1(), 1e-9);
        assertEquals(0.0, fv.getPosLag3Mean(), 1e-9);
        assertEquals(9, FeatureVector.STAGE1_FEATURES.length);
    }

    @Test
    @DisplayName("新备件无历史 → 标记数据不足，不抛异常（TC-FC-04）")
    void testNewPartInsufficient() {
        PartFeatureContext ctx = new PartFeatureContext("SP9999");
        // 仅有目标月当月及之后的数据，之前无任何历史
        ctx.getMonthlyDemand().put("2026-07", 10.0);
        ctx.getMonthlyDemand().put("2026-08", 12.0);

        FeatureVector fv = assertDoesNotThrow(
                () -> builder.buildInferenceVector(ctx, "2026-07", constCodes, true));
        assertTrue(fv.isDataInsufficient(), "无历史新备件应标记数据不足");
        assertNotNull(fv.getInsufficientReason());
    }

    @Test
    @DisplayName("训练样本：标签为当月实际、occurrence 标签正确、无泄露月被跳过")
    void testTrainingSamplesLabels() {
        PartFeatureContext ctx = buildSampleContext();
        List<String> labelMonths = Arrays.asList(
                "2026-01", "2026-02", "2026-03", "2026-04", "2026-05", "2026-06");
        List<TrainingSample> samples = builder.buildTrainingSamples(ctx, labelMonths, constCodes);

        // 2026-01 之前无历史 → 跳过；其余 5 个月生成样本
        assertEquals(5, samples.size());
        TrainingSample feb = samples.get(0); // 2026-02
        assertEquals("2026-02", feb.getFeatures().getTargetMonth());
        assertEquals(0.0, feb.getDemand(), 1e-9);
        assertEquals(0, feb.getOccurrenceLabel(), "2026-02 需求为 0，occurrence 应为 0");

        TrainingSample mar = samples.get(1); // 2026-03 demand=3 → occurrence 1
        assertEquals(3.0, mar.getDemand(), 1e-9);
        assertEquals(1, mar.getOccurrenceLabel());
        // 2026-03 的 lag_1 应为 2026-02=0，绝不读取 2026-03 当月
        assertEquals(0.0, mar.getFeatures().getLag1(), 1e-9);
    }
}
