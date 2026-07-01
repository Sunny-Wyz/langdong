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
import com.langdong.spare.forecast.feature.MonthlyClassCodeProvider;
import com.langdong.spare.forecast.feature.PartFeatureContext;
import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.forecast.model.SafetyStockResult;
import com.langdong.spare.forecast.model.TrainingSample;
import com.langdong.spare.forecast.montecarlo.LeadTimeDemandSimulator;
import com.langdong.spare.forecast.stage.TwoStageModel;
import com.langdong.spare.mapper.AiForecastResultMapper;
import com.langdong.spare.mapper.AiModelRegistryMapper;
import com.langdong.spare.mapper.PartClassifyMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 两阶段智能预测与库存阈值重算业务服务（模块 G）。
 *
 * <p>主要编排：加载历史特征 -> ABC×XYZ 分类 -> 两阶段模型重训(包含增量快照优化)
 * -> 两阶段概率预测 -> 蒙特卡洛提前期累计需求模拟 -> 事务一致性落库与模型注册。</p>
 */
@Service
public class StockThresholdService {

    private static final Logger log = LoggerFactory.getLogger(StockThresholdService.class);

    private final ForecastFeatureLoader featureLoader;
    private final AbcXyzClassifier abcXyzClassifier;
    private final FeatureBuilder featureBuilder;
    private final PredictionService predictionService;
    private final LeadTimeDemandSimulator leadTimeDemandSimulator;

    private final SparePartMapper sparePartMapper;
    private final AiForecastResultMapper aiForecastResultMapper;
    private final PartClassifyMapper partClassifyMapper;
    private final AiModelRegistryMapper aiModelRegistryMapper;

    private final ForecastProperties forecastProperties;
    private final XGBoostProperties xgboostProperties;

    // 增量优化配置开关，可通过 application.yml 中的 forecast.incremental 覆盖，默认 true
    private boolean incrementalEnabled = true;
    private String modelBaseDir = "target/models/";

    public StockThresholdService(ForecastFeatureLoader featureLoader,
                                 AbcXyzClassifier abcXyzClassifier,
                                 FeatureBuilder featureBuilder,
                                 PredictionService predictionService,
                                 LeadTimeDemandSimulator leadTimeDemandSimulator,
                                 SparePartMapper sparePartMapper,
                                 AiForecastResultMapper aiForecastResultMapper,
                                 PartClassifyMapper partClassifyMapper,
                                 AiModelRegistryMapper aiModelRegistryMapper,
                                 ForecastProperties forecastProperties,
                                 XGBoostProperties xgboostProperties) {
        this.featureLoader = featureLoader;
        this.abcXyzClassifier = abcXyzClassifier;
        this.featureBuilder = featureBuilder;
        this.predictionService = predictionService;
        this.leadTimeDemandSimulator = leadTimeDemandSimulator;
        this.sparePartMapper = sparePartMapper;
        this.aiForecastResultMapper = aiForecastResultMapper;
        this.partClassifyMapper = partClassifyMapper;
        this.aiModelRegistryMapper = aiModelRegistryMapper;
        this.forecastProperties = forecastProperties;
        this.xgboostProperties = xgboostProperties;
        this.incrementalEnabled = forecastProperties.isIncrementalEnabled();
        this.modelBaseDir = forecastProperties.getModelBaseDir();
    }

    public static final class ProgressUpdate {
        public final int total;
        public final int processed;
        public final int failed;
        public final String stage;
        public final String message;

        public ProgressUpdate(int total, int processed, int failed, String stage, String message) {
            this.total = total;
            this.processed = processed;
            this.failed = failed;
            this.stage = stage;
            this.message = message;
        }
    }

    public void setIncrementalEnabled(boolean incrementalEnabled) {
        this.incrementalEnabled = incrementalEnabled;
    }

    public void setModelBaseDir(String modelBaseDir) {
        this.modelBaseDir = modelBaseDir;
    }

    /**
     * 全量执行需求预测与库存阈值计算。
     */
    @Transactional
    public List<ForecastResult> executeForecastAndStockThreshold(String targetMonth) {
        return executeForecastAndStockThreshold(targetMonth, null);
    }

    /**
     * 全量执行需求预测与库存阈值计算，包含进度更新。
     */
    @Transactional
    public List<ForecastResult> executeForecastAndStockThreshold(String targetMonth, java.util.function.Consumer<ProgressUpdate> progressConsumer) {
        log.info("[重算任务] 开始执行两阶段智能预测与安全库存计算，目标月份: {}", targetMonth);

        YearMonth target = YearMonth.parse(targetMonth);
        String cutoffMonth = target.minusMonths(1).toString();
        String modelVersion = "two-stage-" + targetMonth;

        // 1. ABC×XYZ 批量分类（截止到上月）
        Map<String, AbcXyzCalculator.Classification> classifications = abcXyzClassifier.classifyAsOf(targetMonth);

        // 2. 加载全量备件的 36 个月历史特征
        int historyMonths = forecastProperties.getHistoryMonths();
        Map<String, PartFeatureContext> contexts = featureLoader.loadAllContexts(targetMonth, historyMonths);

        // 获取所有备件档案
        List<SparePart> parts = sparePartMapper.findAllForClassify();
        if (parts == null || parts.isEmpty()) {
            return Collections.emptyList();
        }

        // 找出上月的最新生产模型，用于增量快照复用
        AiModelRegistry prevRegistry = aiModelRegistryMapper.findProductionModel("demand-forecaster-two-stage");
        String prevModelVersion = prevRegistry != null ? prevRegistry.getModelVersion() : null;

        // 获取月度分类编码提供者 (防泄露)
        MonthlyClassCodeProvider codeProvider = abcXyzClassifier.codeProviderWithCache();

        List<ForecastResult> results = new ArrayList<>();
        List<AiForecastResult> entitiesToInsert = new ArrayList<>();
        List<PartClassify> classifiesToInsert = new ArrayList<>();

        if (progressConsumer != null) {
            progressConsumer.accept(new ProgressUpdate(parts.size(), 0, 0, "TRAINING", "正在执行分类与XGBoost两阶段模型训练"));
        }

        Path baseDir = Paths.get(modelBaseDir);
        int trainCount = 0;
        int reuseCount = 0;
        int skipCount = 0;

        // 构造训练时间段（36 个月，终止于上月）
        List<String> trainingMonths = new ArrayList<>();
        for (int i = historyMonths - 1; i >= 0; i--) {
            trainingMonths.add(target.minusMonths(i + 1L).toString());
        }

        for (SparePart part : parts) {
            if (progressConsumer != null) {
                progressConsumer.accept(new ProgressUpdate(parts.size(), results.size(), skipCount, "TRAINING", "正在执行第 " + (results.size() + 1) + "/" + parts.size() + " 个备件的两阶段XGBoost预测重算"));
            }

            String partCode = part.getCode();
            PartFeatureContext context = contexts.get(partCode);
            AbcXyzCalculator.Classification classification = classifications.get(partCode);

            if (context == null || classification == null) {
                results.add(ForecastResult.insufficient(partCode, targetMonth, "未找到备件特征或分类数据"));
                skipCount++;
                continue;
            }

            Path currentModelDir = baseDir.resolve(modelVersion).resolve(partCode);
            Path prevModelDir = prevModelVersion != null ? baseDir.resolve(prevModelVersion).resolve(partCode) : null;

            TwoStageModel partModel = null;
            boolean hasPrevModel = prevModelDir != null && Files.exists(prevModelDir.resolve("classifier.xgb"));

            // 增量优化：判断上月有无新增消耗
            double lastMonthDemand = context.demandOf(cutoffMonth);
            boolean hasNewConsumption = lastMonthDemand > 0.0;

            if (incrementalEnabled && hasPrevModel && !hasNewConsumption) {
                // 复用上月模型，进行快照拷贝
                try {
                    copyDirectory(prevModelDir, currentModelDir);
                    partModel = TwoStageModel.load(currentModelDir, cutoffMonth, modelVersion);
                    reuseCount++;
                } catch (Exception e) {
                    log.warn("[重算任务] 复用上月模型快照失败，将重新训练: part={}, error={}", partCode, e.getMessage());
                }
            }

            // 若无法复用（无上月模型、有新领用、或增量开关关闭），则重新训练
            if (partModel == null) {
                List<TrainingSample> trainingSamples = featureBuilder.buildTrainingSamples(context, trainingMonths, codeProvider);
                List<TrainingSample> positives = trainingSamples.stream().filter(TrainingSample::isPositive).collect(Collectors.toList());

                if (positives.isEmpty()) {
                    // 没有正需求样本，无法训练回归器，跳过
                    results.add(ForecastResult.insufficient(partCode, targetMonth, "正需求历史数据不足，跳过训练"));
                    skipCount++;
                    continue;
                }

                try {
                    partModel = predictionService.train(trainingSamples, cutoffMonth, modelVersion);
                    partModel.save(currentModelDir);
                    trainCount++;
                } catch (Exception e) {
                    log.error("[重算任务] 模型训练失败: part={}, error={}", partCode, e.getMessage());
                    results.add(ForecastResult.insufficient(partCode, targetMonth, "模型训练失败: " + e.getMessage()));
                    skipCount++;
                    continue;
                }
            }

            // 4. 执行单月推理
            FeatureVector fv = featureBuilder.buildInferenceVector(context, targetMonth, codeProvider, true);
            ForecastResult fr = predictionService.forecast(partModel, fv);

            double ltQuantile = 0.0;
            if (!fr.isDataInsufficient()) {
                // 5. 蒙特卡洛模拟安全库存
                int leadTime = part.getLeadTime() != null ? part.getLeadTime() : 30;
                double serviceLevel = forecastProperties.getClassify().serviceLevelOf(classification.abcClass());

                SafetyStockResult ssRes = leadTimeDemandSimulator.calculateSafetyStock(
                        fr.getOccurrenceProb(),
                        fr.getPositiveQty(),
                        fr.getLowerBound(),
                        fr.getUpperBound(),
                        leadTime,
                        serviceLevel
                );

                fr.setReorderPoint(ssRes.getReorderPoint());
                fr.setSafetyStock(ssRes.getSafetyStock());
                fr.setServiceLevel(ssRes.getServiceLevel());
                ltQuantile = ssRes.getLeadTimeDemandQuantile();
            } else {
                fr.setReorderPoint(0);
                fr.setSafetyStock(0);
                fr.setServiceLevel(0.0);
            }

            results.add(fr);

            // 组装 DB 实体
            AiForecastResult fEntity = new AiForecastResult();
            fEntity.setPartCode(partCode);
            fEntity.setForecastMonth(targetMonth);
            fEntity.setPredictQty(BigDecimal.valueOf(fr.getDemandHat()));
            fEntity.setLowerBound(BigDecimal.valueOf(fr.getLowerBound()));
            fEntity.setUpperBound(BigDecimal.valueOf(fr.getUpperBound()));
            fEntity.setOccurrenceProb(BigDecimal.valueOf(fr.getOccurrenceProb()));
            fEntity.setPositiveQty(BigDecimal.valueOf(fr.getPositiveQty()));
            fEntity.setLeadTimeQuantile(BigDecimal.valueOf(ltQuantile));
            fEntity.setAlgoType(fr.getAlgoType());
            fEntity.setModelVersion(modelVersion);
            fEntity.setCreateTime(LocalDateTime.now());
            entitiesToInsert.add(fEntity);

            PartClassify cEntity = new PartClassify();
            cEntity.setPartCode(partCode);
            cEntity.setClassifyMonth(targetMonth);
            cEntity.setAbcClass(classification.abcClass());
            cEntity.setXyzClass(classification.xyzClass());
            cEntity.setCompositeScore(BigDecimal.valueOf(classification.compositeScore()));
            cEntity.setAnnualCost(BigDecimal.valueOf(classification.annualCost()));
            cEntity.setCv2(BigDecimal.valueOf(classification.cv2() == Double.MAX_VALUE ? 0.0 : classification.cv2()));
            cEntity.setSafetyStock(fr.getSafetyStock());
            cEntity.setReorderPoint(fr.getReorderPoint());
            cEntity.setServiceLevel(BigDecimal.valueOf(fr.getServiceLevel() * 100.0));
            cEntity.setStrategyCode(classification.abcClass() + classification.xyzClass());
            cEntity.setCreateTime(LocalDateTime.now());
            classifiesToInsert.add(cEntity);
        }

        // 6. 批量且幂等持久化落库
        List<String> partCodes = parts.stream().map(SparePart::getCode).collect(Collectors.toList());

        // 删除旧的预测与分类结果
        aiForecastResultMapper.deleteByMonthAndPartCodes(targetMonth, partCodes);
        partClassifyMapper.deleteByMonthAndPartCodes(targetMonth, partCodes);

        // 批量插入
        if (!entitiesToInsert.isEmpty()) {
            aiForecastResultMapper.insertBatch(entitiesToInsert);
        }
        if (!classifiesToInsert.isEmpty()) {
            partClassifyMapper.insertBatch(classifiesToInsert);
        }

        // 7. 模型版本注册记录
        AiModelRegistry registry = new AiModelRegistry();
        registry.setModelName("demand-forecaster-two-stage");
        registry.setModelVersion(modelVersion);
        registry.setAlgoType("TWO_STAGE");
        registry.setArtifactPath(baseDir.resolve(modelVersion).toAbsolutePath().toString());
        registry.setTrainParts(trainCount + reuseCount);
        // 估计每个备件 36 个月的大概周数 (36 * 4)
        registry.setTrainWeeks(36 * 4);
        registry.setStatus("PRODUCTION");
        registry.setDeployTime(LocalDateTime.now());
        registry.setCreateTime(LocalDateTime.now());

        // 将之前生产的模型状态置为 ARCHIVED 归档
        if (prevRegistry != null) {
            aiModelRegistryMapper.updateStatus(prevRegistry.getId(), "ARCHIVED");
        }
        aiModelRegistryMapper.insert(registry);

        log.info("[重算任务] 两阶段预测重算落库成功！重训数={}, 复用数={}, 跳过数={}, 已注册生产版本: {}",
                trainCount, reuseCount, skipCount, modelVersion);

        return results;
    }

    private void copyDirectory(Path src, Path dest) throws IOException {
        if (!Files.exists(src)) {
            return;
        }
        Files.createDirectories(dest);
        try (java.util.stream.Stream<Path> stream = Files.walk(src)) {
            List<Path> paths = stream.collect(Collectors.toList());
            for (Path source : paths) {
                Path target = dest.resolve(src.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
