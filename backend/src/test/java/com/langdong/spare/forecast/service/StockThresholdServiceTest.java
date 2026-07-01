package com.langdong.spare.forecast.service;

import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.entity.AiModelRegistry;
import com.langdong.spare.entity.PartClassify;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.forecast.classify.AbcXyzCalculator;
import com.langdong.spare.forecast.classify.AbcXyzClassifier;
import com.langdong.spare.forecast.config.ForecastProperties;
import com.langdong.spare.forecast.config.XGBoostProperties;
import com.langdong.spare.forecast.feature.FeatureBuilder;
import com.langdong.spare.forecast.feature.ForecastFeatureLoader;
import com.langdong.spare.forecast.feature.PartFeatureContext;
import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.forecast.model.SafetyStockResult;
import com.langdong.spare.forecast.model.TrainingSample;
import com.langdong.spare.forecast.montecarlo.LeadTimeDemandSimulator;
import com.langdong.spare.forecast.stage.StageOneModel;
import com.langdong.spare.forecast.stage.StageTwoModel;
import com.langdong.spare.forecast.stage.TwoStageModel;
import com.langdong.spare.mapper.AiForecastResultMapper;
import com.langdong.spare.mapper.AiModelRegistryMapper;
import com.langdong.spare.mapper.PartClassifyMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * StockThresholdService 编排业务服务集成测试（在内存中 mock 外部库，避免对真库产生依赖）。
 */
public class StockThresholdServiceTest {

    @Test
    @DisplayName("服务编排测试：全量重算、特征抽取、模型训练、蒙特卡洛调用与落库写入")
    void testExecuteForecastAndStockThreshold() throws IOException {
        // 1. 创建所有依赖的 Mock 实例
        ForecastFeatureLoader featureLoader = mock(ForecastFeatureLoader.class);
        AbcXyzClassifier abcXyzClassifier = mock(AbcXyzClassifier.class);
        FeatureBuilder featureBuilder = mock(FeatureBuilder.class);
        PredictionService predictionService = mock(PredictionService.class);
        LeadTimeDemandSimulator leadTimeDemandSimulator = mock(LeadTimeDemandSimulator.class);
        SparePartMapper sparePartMapper = mock(SparePartMapper.class);
        AiForecastResultMapper aiForecastResultMapper = mock(AiForecastResultMapper.class);
        PartClassifyMapper partClassifyMapper = mock(PartClassifyMapper.class);
        AiModelRegistryMapper aiModelRegistryMapper = mock(AiModelRegistryMapper.class);

        ForecastProperties forecastProperties = new ForecastProperties();
        XGBoostProperties xgboostProperties = new XGBoostProperties();

        // 2. 实例化并配置模型保存路径
        StockThresholdService service = new StockThresholdService(
                featureLoader, abcXyzClassifier, featureBuilder, predictionService,
                leadTimeDemandSimulator, sparePartMapper, aiForecastResultMapper,
                partClassifyMapper, aiModelRegistryMapper, forecastProperties, xgboostProperties
        );
        String tempDir = "target/test-models-" + System.currentTimeMillis();
        service.setModelBaseDir(tempDir);
        service.setIncrementalEnabled(false); // 测试全量训练分支

        // 3. 构建模拟测试数据
        SparePart part = new SparePart();
        part.setId(101L);
        part.setCode("SP001");
        part.setName("气缸电磁阀");
        part.setLeadTime(14);
        part.setPrice(BigDecimal.valueOf(150.0));
        part.setIsCritical(1);
        part.setReplaceDiff(2);

        List<SparePart> parts = Collections.singletonList(part);
        when(sparePartMapper.findAllForClassify()).thenReturn(parts);

        // 分类结果 mock
        Map<String, AbcXyzCalculator.Classification> classMap = new HashMap<>();
        classMap.put("SP001", new AbcXyzCalculator.Classification("A", "X", 3, 1, 85.0, 0.2, 1000.0));
        when(abcXyzClassifier.classifyAsOf("2026-07")).thenReturn(classMap);
        when(abcXyzClassifier.codeProviderWithCache()).thenReturn((partCode, asOfMonth) -> new int[]{3, 1});

        // 历史特征 mock
        PartFeatureContext context = new PartFeatureContext("SP001");
        context.getMonthlyDemand().put("2026-06", 10.0);
        context.getMonthlyDemand().put("2026-05", 8.0);
        Map<String, PartFeatureContext> ctxMap = new HashMap<>();
        ctxMap.put("SP001", context);
        when(featureLoader.loadAllContexts(eq("2026-07"), anyInt())).thenReturn(ctxMap);

        // 训练样本 mock：必须返回包含正需求样本的列表
        FeatureVector fvTrain = new FeatureVector();
        fvTrain.setPartCode("SP001");
        List<TrainingSample> trainSamples = Collections.singletonList(new TrainingSample(fvTrain, 10.0));
        when(featureBuilder.buildTrainingSamples(any(), any(), any())).thenReturn(trainSamples);

        // TwoStageModel 结构 mock
        TwoStageModel mockModel = mock(TwoStageModel.class);
        StageOneModel mockS1 = mock(StageOneModel.class);
        StageTwoModel mockS2 = mock(StageTwoModel.class);
        com.langdong.spare.forecast.xgboost.XgbModel mockBooster = mock(com.langdong.spare.forecast.xgboost.XgbModel.class);
        com.langdong.spare.forecast.calibration.ProbabilityCalibrator mockCalib = mock(com.langdong.spare.forecast.calibration.ProbabilityCalibrator.class);

        when(mockModel.getStageOne()).thenReturn(mockS1);
        when(mockModel.getStageTwo()).thenReturn(mockS2);
        when(mockS1.getClassifier()).thenReturn(mockBooster);
        when(mockS1.getCalibrator()).thenReturn(mockCalib);
        when(mockS2.getPointRegressor()).thenReturn(mockBooster);
        when(mockS2.getLowerQuantile()).thenReturn(mockBooster);
        when(mockS2.getUpperQuantile()).thenReturn(mockBooster);

        when(predictionService.train(anyList(), eq("2026-06"), eq("two-stage-2026-07"))).thenReturn(mockModel);

        // 推理与蒙特卡洛结果 mock
        FeatureVector fvInference = new FeatureVector();
        fvInference.setPartCode("SP001");
        when(featureBuilder.buildInferenceVector(any(), eq("2026-07"), any(), anyBoolean())).thenReturn(fvInference);

        ForecastResult mockForecast = new ForecastResult();
        mockForecast.setPartCode("SP001");
        mockForecast.setTargetMonth("2026-07");
        mockForecast.setOccurrenceProb(0.85);
        mockForecast.setPositiveQty(12.0);
        mockForecast.setLowerBound(6.0);
        mockForecast.setUpperBound(18.0);
        mockForecast.setDemandHat(10.2);
        mockForecast.setAlgoType("TWO_STAGE");
        when(predictionService.forecast(any(), any())).thenReturn(mockForecast);

        when(leadTimeDemandSimulator.calculateSafetyStock(
                eq(0.85), eq(12.0), eq(6.0), eq(18.0), eq(14), eq(0.99)
        )).thenReturn(new SafetyStockResult(15, 6, 0.99, 9.0, 14.5));

        // 运行业务方法
        List<ForecastResult> res = service.executeForecastAndStockThreshold("2026-07");

        // 4. 断言验证
        assertEquals(1, res.size(), "应该返回 1 个预测结果");
        ForecastResult fr = res.get(0);
        assertEquals(15, fr.getReorderPoint());
        assertEquals(6, fr.getSafetyStock());
        assertEquals(0.99, fr.getServiceLevel());
        assertEquals(10.2, fr.getDemandHat());

        // 验证幂等删除被调用
        verify(aiForecastResultMapper, times(1)).deleteByMonthAndPartCodes("2026-07", Collections.singletonList("SP001"));
        verify(partClassifyMapper, times(1)).deleteByMonthAndPartCodes("2026-07", Collections.singletonList("SP001"));

        // 验证批量插入
        ArgumentCaptor<List<AiForecastResult>> forecastCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiForecastResultMapper, times(1)).insertBatch(forecastCaptor.capture());
        assertEquals(1, forecastCaptor.getValue().size());
        assertEquals(BigDecimal.valueOf(10.2), forecastCaptor.getValue().get(0).getPredictQty());

        ArgumentCaptor<List<PartClassify>> classifyCaptor = ArgumentCaptor.forClass(List.class);
        verify(partClassifyMapper, times(1)).insertBatch(classifyCaptor.capture());
        assertEquals(1, classifyCaptor.getValue().size());
        assertEquals("AX", classifyCaptor.getValue().get(0).getStrategyCode());
        assertEquals(6, classifyCaptor.getValue().get(0).getSafetyStock());
        assertEquals(15, classifyCaptor.getValue().get(0).getReorderPoint());

        // 验证模型版本注册被保存
        ArgumentCaptor<AiModelRegistry> registryCaptor = ArgumentCaptor.forClass(AiModelRegistry.class);
        verify(aiModelRegistryMapper, times(1)).insert(registryCaptor.capture());
        AiModelRegistry reg = registryCaptor.getValue();
        assertEquals("demand-forecaster-two-stage", reg.getModelName());
        assertEquals("two-stage-2026-07", reg.getModelVersion());
        assertEquals("TWO_STAGE", reg.getAlgoType());
        assertEquals("PRODUCTION", reg.getStatus());

        // 清理临时测试文件夹
        Path tempPath = Paths.get(tempDir);
        if (Files.exists(tempPath)) {
            try (java.util.stream.Stream<Path> s = Files.walk(tempPath)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }
}
