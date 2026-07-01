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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class IncrementalSnapshotTest {

    private ForecastFeatureLoader featureLoader;
    private AbcXyzClassifier abcXyzClassifier;
    private FeatureBuilder featureBuilder;
    private PredictionService predictionService;
    private LeadTimeDemandSimulator leadTimeDemandSimulator;
    private SparePartMapper sparePartMapper;
    private AiForecastResultMapper aiForecastResultMapper;
    private PartClassifyMapper partClassifyMapper;
    private AiModelRegistryMapper aiModelRegistryMapper;

    private ForecastProperties forecastProperties;
    private XGBoostProperties xgboostProperties;

    private StockThresholdService service;
    private String tempBaseDir;

    @BeforeEach
    void setup() throws IOException {
        featureLoader = mock(ForecastFeatureLoader.class);
        abcXyzClassifier = mock(AbcXyzClassifier.class);
        featureBuilder = mock(FeatureBuilder.class);
        predictionService = mock(PredictionService.class);
        leadTimeDemandSimulator = mock(LeadTimeDemandSimulator.class);
        sparePartMapper = mock(SparePartMapper.class);
        aiForecastResultMapper = mock(AiForecastResultMapper.class);
        partClassifyMapper = mock(PartClassifyMapper.class);
        aiModelRegistryMapper = mock(AiModelRegistryMapper.class);

        forecastProperties = new ForecastProperties();
        xgboostProperties = new XGBoostProperties();

        service = new StockThresholdService(
                featureLoader, abcXyzClassifier, featureBuilder, predictionService,
                leadTimeDemandSimulator, sparePartMapper, aiForecastResultMapper,
                partClassifyMapper, aiModelRegistryMapper, forecastProperties, xgboostProperties
        );

        tempBaseDir = "target/test-incremental-" + System.currentTimeMillis();
        service.setModelBaseDir(tempBaseDir);

        // 创建前一月的模拟模型文件，用于模拟“上月已存在模型快照”的情况
        Path prevDir = Paths.get(tempBaseDir).resolve("two-stage-2026-06").resolve("SP001");
        Files.createDirectories(prevDir);
        Files.write(prevDir.resolve("classifier.xgb"), "dummy model content".getBytes());
        Files.write(prevDir.resolve("calibrator.calib"), "dummy content".getBytes());
        Files.write(prevDir.resolve("point_regressor.xgb"), "dummy content".getBytes());
        Files.write(prevDir.resolve("lower_quantile.xgb"), "dummy content".getBytes());
        Files.write(prevDir.resolve("upper_quantile.xgb"), "dummy content".getBytes());

        // Mock 基础的 metadata
        SparePart part = new SparePart();
        part.setId(1L);
        part.setCode("SP001");
        part.setLeadTime(30);
        when(sparePartMapper.findAllForClassify()).thenReturn(Collections.singletonList(part));

        Map<String, AbcXyzCalculator.Classification> classMap = new HashMap<>();
        classMap.put("SP001", new AbcXyzCalculator.Classification("A", "X", 3, 1, 80.0, 0.2, 1000.0));
        when(abcXyzClassifier.classifyAsOf("2026-07")).thenReturn(classMap);
        when(abcXyzClassifier.codeProviderWithCache()).thenReturn((partCode, asOfMonth) -> new int[]{3, 1});

        // Mock 查上月在用生产模型版本
        AiModelRegistry prevRegistry = new AiModelRegistry();
        prevRegistry.setId(99L);
        prevRegistry.setModelVersion("two-stage-2026-06");
        when(aiModelRegistryMapper.findProductionModel("demand-forecaster-two-stage")).thenReturn(prevRegistry);

        // Mock 预测与模拟输出
        FeatureVector fv = new FeatureVector();
        fv.setPartCode("SP001");
        when(featureBuilder.buildInferenceVector(any(), anyString(), any(), anyBoolean())).thenReturn(fv);

        ForecastResult mockForecast = new ForecastResult();
        mockForecast.setPartCode("SP001");
        mockForecast.setTargetMonth("2026-07");
        mockForecast.setOccurrenceProb(0.9);
        mockForecast.setPositiveQty(10.0);
        mockForecast.setLowerBound(5.0);
        mockForecast.setUpperBound(15.0);
        mockForecast.setDemandHat(9.0);
        mockForecast.setAlgoType("TWO_STAGE");
        when(predictionService.forecast(any(), any())).thenReturn(mockForecast);

        when(leadTimeDemandSimulator.calculateSafetyStock(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt(), anyDouble()
        )).thenReturn(new SafetyStockResult(10, 4, 0.99, 8.0, 9.5));
    }

    @AfterEach
    void cleanup() throws IOException {
        Path path = Paths.get(tempBaseDir);
        if (Files.exists(path)) {
            try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    @DisplayName("增量快照优化测试：增量开启且上月无新领用时直接拷贝并复用快照，跳过重训")
    void testReuseSnapshotWhenNoDemand() {
        service.setIncrementalEnabled(true);

        // 上月无消耗：cutoffMonth("2026-06") demand == 0.0
        PartFeatureContext context = new PartFeatureContext("SP001");
        context.getMonthlyDemand().put("2026-06", 0.0);
        context.getMonthlyDemand().put("2026-05", 5.0);
        Map<String, PartFeatureContext> ctxMap = new HashMap<>();
        ctxMap.put("SP001", context);
        when(featureLoader.loadAllContexts(eq("2026-07"), anyInt())).thenReturn(ctxMap);

        TwoStageModel mockModel = mock(TwoStageModel.class);

        try (org.mockito.MockedStatic<TwoStageModel> modelMock = Mockito.mockStatic(TwoStageModel.class)) {
            modelMock.when(() -> TwoStageModel.load(any(), anyString(), anyString())).thenReturn(mockModel);

            // 执行预测
            List<ForecastResult> res = service.executeForecastAndStockThreshold("2026-07");

            // 验证返回结果正确
            assertEquals(1, res.size());
            assertEquals(10, res.get(0).getReorderPoint());
            assertEquals(4, res.get(0).getSafetyStock());

            // 验证 predictionService.train 绝未被调用（成功跳过重训复用快照）
            verify(predictionService, never()).train(anyList(), anyString(), anyString());

            // 验证新的模型目录中成功拷贝了快照模型文件
            Path currentDir = Paths.get(tempBaseDir).resolve("two-stage-2026-07").resolve("SP001");
            assertEquals(true, Files.exists(currentDir.resolve("classifier.xgb")));
            assertEquals(true, Files.exists(currentDir.resolve("point_regressor.xgb")));
        }
    }

    @Test
    @DisplayName("增量快照优化测试：上月有新消耗时即便增量开启，也必须触发重训")
    void testRetrainWhenNewDemandExists() {
        service.setIncrementalEnabled(true);

        // 上月有消耗：cutoffMonth("2026-06") demand == 4.0
        PartFeatureContext context = new PartFeatureContext("SP001");
        context.getMonthlyDemand().put("2026-06", 4.0);
        context.getMonthlyDemand().put("2026-05", 5.0);
        Map<String, PartFeatureContext> ctxMap = new HashMap<>();
        ctxMap.put("SP001", context);
        when(featureLoader.loadAllContexts(eq("2026-07"), anyInt())).thenReturn(ctxMap);

        // Mock 训练过程
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

        List<TrainingSample> trainSamples = Collections.singletonList(new TrainingSample(new FeatureVector(), 4.0));
        when(featureBuilder.buildTrainingSamples(any(), anyList(), any())).thenReturn(trainSamples);

        // 执行预测
        List<ForecastResult> res = service.executeForecastAndStockThreshold("2026-07");

        assertEquals(1, res.size());

        // 验证 predictionService.train 被正确触发训练了 1 次
        verify(predictionService, times(1)).train(anyList(), eq("2026-06"), eq("two-stage-2026-07"));
    }

    @Test
    @DisplayName("增量快照优化测试：增量开关关闭时不论有无新消耗，都必须重新训练")
    void testRetrainWhenIncrementalDisabled() {
        service.setIncrementalEnabled(false); // 关闭增量优化

        // 上月无消耗
        PartFeatureContext context = new PartFeatureContext("SP001");
        context.getMonthlyDemand().put("2026-06", 0.0);
        context.getMonthlyDemand().put("2026-05", 5.0);
        Map<String, PartFeatureContext> ctxMap = new HashMap<>();
        ctxMap.put("SP001", context);
        when(featureLoader.loadAllContexts(eq("2026-07"), anyInt())).thenReturn(ctxMap);

        // Mock 训练过程
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

        List<TrainingSample> trainSamples = Collections.singletonList(new TrainingSample(new FeatureVector(), 5.0));
        when(featureBuilder.buildTrainingSamples(any(), anyList(), any())).thenReturn(trainSamples);

        // 执行预测
        List<ForecastResult> res = service.executeForecastAndStockThreshold("2026-07");

        assertEquals(1, res.size());

        // 验证 predictionService.train 被触发重训
        verify(predictionService, times(1)).train(anyList(), eq("2026-06"), eq("two-stage-2026-07"));
    }
}
