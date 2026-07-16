package com.langdong.spare.forecast.service;

import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.entity.AiModelRegistry;
import com.langdong.spare.entity.PartClassify;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.forecast.classify.AbcXyzCalculator;
import com.langdong.spare.forecast.classify.AbcXyzClassifier;
import com.langdong.spare.forecast.config.ForecastProperties;
import com.langdong.spare.forecast.feature.FeatureBuilder;
import com.langdong.spare.forecast.feature.ForecastFeatureLoader;
import com.langdong.spare.forecast.feature.MonthlyClassCodeProvider;
import com.langdong.spare.forecast.feature.PartFeatureContext;
import com.langdong.spare.forecast.model.FeatureVector;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.forecast.model.SafetyStockResult;
import com.langdong.spare.forecast.model.TrainingSample;
import com.langdong.spare.forecast.montecarlo.LeadTimeDemandSimulator;
import com.langdong.spare.mapper.AiForecastResultMapper;
import com.langdong.spare.mapper.AiModelRegistryMapper;
import com.langdong.spare.mapper.PartClassifyMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 两阶段 Hurdle-Gamma 智能预测与库存阈值重算业务服务（模块 G）。
 *
 * <p>已重构：使用外部 Python 算法微服务进行 Hurdle-Gamma 两阶段预测与蒙特卡洛安全库存计算。</p>
 */
@Service
public class StockThresholdService {

    private static final Logger log = LoggerFactory.getLogger(StockThresholdService.class);

    private final ForecastFeatureLoader featureLoader;
    private final AbcXyzClassifier abcXyzClassifier;
    private final FeatureBuilder featureBuilder;
    private final LeadTimeDemandSimulator leadTimeDemandSimulator;

    private final SparePartMapper sparePartMapper;
    private final AiForecastResultMapper aiForecastResultMapper;
    private final PartClassifyMapper partClassifyMapper;
    private final AiModelRegistryMapper aiModelRegistryMapper;

    private final ForecastProperties forecastProperties;
    private final RestTemplate restTemplate;

    @Value("${ai.python.base-url:http://localhost:8001}")
    private String pythonBaseUrl;

    public StockThresholdService(ForecastFeatureLoader featureLoader,
                                 AbcXyzClassifier abcXyzClassifier,
                                 FeatureBuilder featureBuilder,
                                 LeadTimeDemandSimulator leadTimeDemandSimulator,
                                 SparePartMapper sparePartMapper,
                                 AiForecastResultMapper aiForecastResultMapper,
                                 PartClassifyMapper partClassifyMapper,
                                 AiModelRegistryMapper aiModelRegistryMapper,
                                 ForecastProperties forecastProperties,
                                 RestTemplate pythonRestTemplate) {
        this.featureLoader = featureLoader;
        this.abcXyzClassifier = abcXyzClassifier;
        this.featureBuilder = featureBuilder;
        this.leadTimeDemandSimulator = leadTimeDemandSimulator;
        this.sparePartMapper = sparePartMapper;
        this.aiForecastResultMapper = aiForecastResultMapper;
        this.partClassifyMapper = partClassifyMapper;
        this.aiModelRegistryMapper = aiModelRegistryMapper;
        this.forecastProperties = forecastProperties;
        this.restTemplate = pythonRestTemplate;
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

    /**
     * 全量执行需求预测与库存阈值计算。
     */
    @Transactional
    public List<ForecastResult> executeForecastAndStockThreshold(String targetMonth) {
        return executeForecastAndStockThreshold(targetMonth, null, null);
    }

    /**
     * 全量执行需求预测与库存阈值计算，并调用外部 Python 微服务进行预测仿真。
     */
    @Transactional
    public List<ForecastResult> executeForecastAndStockThreshold(String targetMonth,
                                                                 java.util.function.Consumer<ProgressUpdate> progressConsumer) {
        return executeForecastAndStockThreshold(targetMonth, progressConsumer, null);
    }

    /**
     * 执行两阶段 Hurdle-Gamma 预测与库存阈值计算。
     *
     * @param onlyPartCodes 非空时仅对指定备件编码做推理/落库（训练仍用全量样本以保证模型质量）
     */
    @Transactional
    public List<ForecastResult> executeForecastAndStockThreshold(String targetMonth,
                                                                 java.util.function.Consumer<ProgressUpdate> progressConsumer,
                                                                 Collection<String> onlyPartCodes) {
        log.info("[重算任务] 开始调用 Python 算法微服务执行智能预测与安全库存计算，目标月份: {}, 过滤备件数: {}",
                targetMonth, onlyPartCodes == null ? "ALL" : onlyPartCodes.size());

        YearMonth target = YearMonth.parse(targetMonth);
        // 版本号需落在 ai_forecast_result.model_version（varchar(64)）内；
        // 形如 two-stage-python-2026-08（24 字符），旧库 varchar(20) 会触发 #22001
        String modelVersion = "two-stage-python-" + targetMonth;
        Set<String> filterCodes = normalizePartCodeFilter(onlyPartCodes);
        boolean partialJob = !filterCodes.isEmpty();

        // 1. ABC×XYZ 批量分类（截止到上月）
        Map<String, AbcXyzCalculator.Classification> classifications = abcXyzClassifier.classifyAsOf(targetMonth);

        // 2. 加载全量备件的历史特征
        int historyMonths = forecastProperties.getHistoryMonths();
        Map<String, PartFeatureContext> contexts = featureLoader.loadAllContexts(targetMonth, historyMonths);

        // 获取所有备件档案（训练用全量；推理可按编码过滤）
        List<SparePart> allParts = sparePartMapper.findAllForClassify();
        if (allParts == null || allParts.isEmpty()) {
            return Collections.emptyList();
        }
        List<SparePart> parts = partialJob
                ? allParts.stream().filter(p -> p != null && p.getCode() != null && filterCodes.contains(p.getCode().trim())).toList()
                : allParts;
        if (parts.isEmpty()) {
            log.warn("[重算任务] 过滤后无可预测备件，targetMonth={}, filter={}", targetMonth, filterCodes);
            return Collections.emptyList();
        }

        // 找出上月的最新模型注册信息以做参考
        AiModelRegistry prevRegistry = aiModelRegistryMapper.findProductionModel("demand-forecaster-two-stage");

        // 获取分类编码提供者 (防止数据泄露)
        MonthlyClassCodeProvider codeProvider = abcXyzClassifier.codeProviderWithCache();

        List<ForecastResult> results = new ArrayList<>();
        List<AiForecastResult> entitiesToInsert = new ArrayList<>();
        List<PartClassify> classifiesToInsert = new ArrayList<>();

        if (progressConsumer != null) {
            progressConsumer.accept(new ProgressUpdate(parts.size(), 0, 0, "TRAINING", "正在组装历史特征矩阵并进行 Python 算法模型训练"));
        }

        // 构造训练时间段（36 个月，终止于上月）
        List<String> trainingMonths = new ArrayList<>();
        for (int i = historyMonths - 1; i >= 0; i--) {
            trainingMonths.add(target.minusMonths(i + 1L).toString());
        }

        // ==================== 1. 批量收集特征矩阵，发起全局微服务训练 ====================
        List<List<Double>> allTrainX = new ArrayList<>();
        List<Double> allTrainY = new ArrayList<>();
        List<String> allTrainGroups = new ArrayList<>();

        for (SparePart part : allParts) {
            String partCode = part.getCode();
            PartFeatureContext context = contexts.get(partCode);
            AbcXyzCalculator.Classification classification = classifications.get(partCode);

            if (context == null || classification == null) {
                continue;
            }

            List<TrainingSample> trainingSamples = featureBuilder.buildTrainingSamples(context, trainingMonths, codeProvider);
            for (TrainingSample sample : trainingSamples) {
                float[] featArr = sample.getFeatures().toStage2Array();
                List<Double> row = new ArrayList<>();
                for (float val : featArr) {
                    row.add((double) val);
                }
                allTrainX.add(row);
                allTrainY.add(sample.getDemand());
                allTrainGroups.add(classification.xyzClass());
            }
        }

        int trainCount = allTrainX.size();
        if (trainCount > 0) {
            Map<String, Object> trainRequest = new HashMap<>();
            trainRequest.put("X", allTrainX);
            trainRequest.put("y", allTrainY);
            trainRequest.put("xyz_groups", allTrainGroups);

            String trainUrl = pythonBaseUrl + "/api/algorithm/train";
            try {
                restTemplate.postForObject(trainUrl, trainRequest, Map.class);
                log.info("[重算任务] Python 算法端全局模型训练成功，样本行数: {}", trainCount);
            } catch (Exception e) {
                log.error("[重算任务] 发起 Python 全局模型训练网络请求失败", e);
                throw new RuntimeException("Python 端算法训练异常", e);
            }
        }

        // ==================== 2. 批量组装推理向量，发起 Python 批量推理 ====================
        List<List<Double>> allPredictX = new ArrayList<>();
        List<String> allPredictGroups = new ArrayList<>();
        List<SparePart> validParts = new ArrayList<>();
        List<AbcXyzCalculator.Classification> validClassifications = new ArrayList<>();

        int skipCount = 0;

        for (SparePart part : parts) {
            String partCode = part.getCode();
            PartFeatureContext context = contexts.get(partCode);
            AbcXyzCalculator.Classification classification = classifications.get(partCode);

            if (context == null || classification == null) {
                results.add(ForecastResult.insufficient(partCode, targetMonth, "未找到备件特征或分类数据"));
                skipCount++;
                continue;
            }

            FeatureVector fv = featureBuilder.buildInferenceVector(context, targetMonth, codeProvider, true);
            if (fv.isDataInsufficient()) {
                results.add(ForecastResult.insufficient(partCode, targetMonth, fv.getInsufficientReason()));
                skipCount++;
                continue;
            }

            float[] featArr = fv.toStage2Array();
            List<Double> row = new ArrayList<>();
            for (float val : featArr) {
                row.add((double) val);
            }

            allPredictX.add(row);
            allPredictGroups.add(classification.xyzClass());
            validParts.add(part);
            validClassifications.add(classification);
        }

        List<Map<String, Object>> predictions = new ArrayList<>();
        if (!allPredictX.isEmpty()) {
            Map<String, Object> predictRequest = new HashMap<>();
            predictRequest.put("X", allPredictX);
            predictRequest.put("xyz_groups", allPredictGroups);

            String predictUrl = pythonBaseUrl + "/api/algorithm/predict";
            try {
                Map response = restTemplate.postForObject(predictUrl, predictRequest, Map.class);
                if (response != null && response.containsKey("predictions")) {
                    predictions = (List<Map<String, Object>>) response.get("predictions");
                }
            } catch (Exception e) {
                log.error("[重算任务] 发起 Python 批量推理预测接口网络请求失败", e);
                throw new RuntimeException("Python 端推理预测异常", e);
            }
        }

        // ==================== 3. 循环解析结果并调用库存模拟 ====================
        for (int i = 0; i < validParts.size(); i++) {
            SparePart part = validParts.get(i);
            String partCode = part.getCode();
            AbcXyzCalculator.Classification classification = validClassifications.get(i);
            Map<String, Object> pred = predictions.get(i);

            double p = ((Number) pred.get("p_t")).doubleValue();
            double mu = ((Number) pred.get("mu_t")).doubleValue();
            double lower = ((Number) pred.get("lower_bound")).doubleValue();
            double upper = ((Number) pred.get("upper_bound")).doubleValue();

            ForecastResult fr = new ForecastResult();
            fr.setPartCode(partCode);
            fr.setTargetMonth(targetMonth);
            fr.setOccurrenceProb(p);
            fr.setPositiveQty(mu);
            fr.setLowerBound(lower);
            fr.setUpperBound(upper);
            fr.setDemandHat(p * mu);
            fr.setModelVersion(modelVersion);

            if (progressConsumer != null) {
                progressConsumer.accept(new ProgressUpdate(parts.size(), results.size(), skipCount, "SIMULATION", 
                        "正在计算第 " + (results.size() + 1) + "/" + parts.size() + " 个备件的蒙特卡洛安全库存"));
            }

            int leadTime = part.getLeadTime() != null ? part.getLeadTime() : 30;
            double serviceLevel = forecastProperties.getClassify().serviceLevelOf(classification.abcClass());

            SafetyStockResult ssRes = leadTimeDemandSimulator.calculateSafetyStock(
                    p, mu, lower, upper, leadTime, serviceLevel
            );

            fr.setReorderPoint(ssRes.getReorderPoint());
            fr.setSafetyStock(ssRes.getSafetyStock());
            fr.setServiceLevel(ssRes.getServiceLevel());
            double ltQuantile = ssRes.getLeadTimeDemandQuantile();

            results.add(fr);

            // 组装 DB 实体（按列精度 setScale，避免 DOUBLE→DECIMAL 长尾导致截断）
            AiForecastResult fEntity = new AiForecastResult();
            fEntity.setPartCode(partCode);
            fEntity.setForecastMonth(targetMonth);
            fEntity.setPredictQty(toDecimal(fr.getDemandHat(), 8, 2));
            fEntity.setLowerBound(toDecimal(fr.getLowerBound(), 8, 2));
            fEntity.setUpperBound(toDecimal(fr.getUpperBound(), 8, 2));
            fEntity.setOccurrenceProb(toDecimal(clamp(fr.getOccurrenceProb(), 0.0, 1.0), 5, 4));
            fEntity.setPositiveQty(toDecimal(fr.getPositiveQty(), 8, 2));
            fEntity.setLeadTimeQuantile(toDecimal(ltQuantile, 8, 2));
            fEntity.setAlgoType("TWO_STAGE");
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

        // 4. 批量且幂等持久化落库
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

        // 5. 模型版本注册：仅全量任务切换 PRODUCTION，避免任务中心单备件任务刷爆注册表
        if (!partialJob) {
            AiModelRegistry registry = new AiModelRegistry();
            registry.setModelName("demand-forecaster-two-stage");
            registry.setModelVersion(modelVersion);
            registry.setAlgoType("TWO_STAGE");
            registry.setArtifactPath("python-microservice");
            registry.setTrainParts(validParts.size());
            registry.setTrainWeeks(36 * 4);
            registry.setStatus("PRODUCTION");
            registry.setDeployTime(LocalDateTime.now());
            registry.setCreateTime(LocalDateTime.now());

            if (prevRegistry != null) {
                aiModelRegistryMapper.updateStatus(prevRegistry.getId(), "ARCHIVED");
            }
            aiModelRegistryMapper.insert(registry);
        }

        log.info("[重算任务] 调用 Python 接口重算落库成功！有效备件数={}, 跳过数={}, partial={}, modelVersion={}",
                validParts.size(), skipCount, partialJob, modelVersion);

        return results;
    }

    private static Set<String> normalizePartCodeFilter(Collection<String> onlyPartCodes) {
        if (onlyPartCodes == null || onlyPartCodes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String code : onlyPartCodes) {
            if (code == null) {
                continue;
            }
            String trimmed = code.trim();
            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }
        return codes;
    }

    /**
     * 将 double 转为指定 DECIMAL(precision, scale) 可落库的 BigDecimal，并做范围钳制。
     * precision 指总位数（含小数位），例如 DECIMAL(8,2) 的整数部分最多 6 位。
     */
    static BigDecimal toDecimal(double value, int precision, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        int intDigits = Math.max(1, precision - scale);
        double maxAbs = Math.pow(10, intDigits) - Math.pow(10, -scale);
        double clamped = Math.max(-maxAbs, Math.min(maxAbs, value));
        return BigDecimal.valueOf(clamped).setScale(scale, RoundingMode.HALF_UP);
    }

    static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
