package com.langdong.spare.service.ai;

import com.langdong.spare.dto.MonthlyConsumptionVO;
import com.langdong.spare.dto.PredictContextDTO;
import com.langdong.spare.entity.AiDeviceFeature;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.mapper.AiDeviceFeatureMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 特征工程服务
 *
 * 职责：
 * 1. 批量加载备件近12个月消耗历史（一次 SQL 全量，禁止 for 循环单条查询）
 * 2. 批量加载关联设备特征
 * 3. 计算 ADI / CV²，判断每个备件应使用 RF 还是 SBA 算法（Syntetos-Boylan 矩阵）
 * 4. 组装 PredictContextDTO 列表供算法服务消费
 */
@Service
public class AiFeatureService {

    private static final Logger log = LoggerFactory.getLogger(AiFeatureService.class);

    /** ADI 阈值：平均需求间隔 > 1.32 视为间断型 */
    private static final double ADI_THRESHOLD = 1.32;
    /** CV² 阈值：需求变异系数平方 > 0.49 视为间断型 */
    private static final double CV2_THRESHOLD = 0.49;
    /** Fallback 最小历史数据点（月份）数量 */
    public static final int MIN_DATA_POINTS = 3;

    @Autowired
    private SparePartMapper sparePartMapper;

    /**
     * 组装所有备件的预测上下文（批量，一次性加载所有历史数据）
     *
     * @param forecastMonth 预测目标月份（yyyy-MM）
     * @return PredictContextDTO 列表，每个元素对应一个备件，algoType 已确定
     */
    public List<PredictContextDTO> buildAllContexts(String forecastMonth) {
        // ---- 1. 加载所有备件档案 ----
        List<SparePart> parts = sparePartMapper.findAllForClassify();
        if (parts == null || parts.isEmpty()) {
            log.warn("[AI特征] 备件档案为空，跳过特征组装");
            return Collections.emptyList();
        }
        log.info("[AI特征] 共加载备件 {} 条", parts.size());

        // ---- 2. 批量加载近12个月消耗历史（一次 SQL） ----
        String fromMonth = LocalDate.now().minusMonths(12).withDayOfMonth(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        List<MonthlyConsumptionVO> consumptionList = sparePartMapper.findAllMonthlyConsumption(fromMonth);

        // 按 partCode 分组：partCode -> Map<月份, 消耗量>
        Map<String, Map<String, Integer>> consumptionMap = new HashMap<>();
        if (consumptionList != null) {
            for (MonthlyConsumptionVO vo : consumptionList) {
                consumptionMap
                        .computeIfAbsent(vo.getPartCode(), k -> new LinkedHashMap<>())
                        .put(vo.getMonth(), vo.getQty());
            }
        }
        log.info("[AI特征] 消耗记录 {} 条，涉及 {} 备件",
                consumptionList == null ? 0 : consumptionList.size(), consumptionMap.size());

        // ---- 3. 生成近12个月月份列表（用于补全0需求月份） ----
        List<String> last12Months = buildLast12Months();

        // ---- 4. 批量加载设备特征（若有关联备件） ----
        // 通过工单记录获取各备件关联的设备ID（此处简化：查全部设备的特征）
        List<AiDeviceFeature> allFeatures = Collections.emptyList();
        try {
            // 获取所有设备ID（简化：从消耗数据推断或固定取前期工单数据）
            // 实际生产中可通过 EquipmentSparePartMapper 获取设备-备件关联关系
            allFeatures = Collections.emptyList();
        } catch (Exception e) {
            log.warn("[AI特征] 加载设备特征失败，使用空特征: {}", e.getMessage());
        }

        // ---- 5. 组装 PredictContextDTO ----
        List<PredictContextDTO> contexts = new ArrayList<>(parts.size());
        for (SparePart part : parts) {
            String code = part.getCode();
            Map<String, Integer> mqMap = consumptionMap.getOrDefault(code, Collections.emptyMap());

            // 补全12个月（缺失月填0）
            List<Integer> demands = new ArrayList<>(12);
            for (String m : last12Months) {
                demands.add(mqMap.getOrDefault(m, 0));
            }

            // 计算 ADI 和 CV²
            double adi = calcADI(demands);
            double cv2 = calcCV2(demands);

            // Syntetos-Boylan 分型
            String algoType;
            int nonZero = (int) demands.stream().filter(d -> d > 0).count();
            if (nonZero < MIN_DATA_POINTS) {
                algoType = "FALLBACK";
            } else if (adi > ADI_THRESHOLD && cv2 > CV2_THRESHOLD) {
                algoType = "SBA";
            } else {
                algoType = "RF";
            }

            PredictContextDTO ctx = new PredictContextDTO();
            ctx.setPartCode(code);
            ctx.setPartName(part.getName());
            ctx.setLeadTime(part.getLeadTime() != null ? part.getLeadTime() : 30);
            ctx.setForecastMonth(forecastMonth);
            ctx.setDemandHistory(demands);
            ctx.setAlgoType(algoType);
            ctx.setAdi(adi);
            ctx.setCv2(cv2);
            ctx.setDeviceFeatures(null); // 设备特征扩展点，暂留空

            contexts.add(ctx);
        }

        long rfCount = contexts.stream().filter(c -> "RF".equals(c.getAlgoType())).count();
        long sbaCount = contexts.stream().filter(c -> "SBA".equals(c.getAlgoType())).count();
        long fbCount = contexts.stream().filter(c -> "FALLBACK".equals(c.getAlgoType())).count();
        log.info("[AI特征] 分型结果 — RF: {}, SBA: {}, FALLBACK: {}", rfCount, sbaCount, fbCount);

        return contexts;
    }

    // ================================================================
    // ADI / CV² 计算工具方法
    // ================================================================

    /**
     * 计算平均需求间隔 ADI
     * ADI = 总期数 / 非零需求期数
     * 若无需求记录，返回 Double.MAX_VALUE
     */
    public static double calcADI(List<Integer> demands) {
        if (demands == null || demands.isEmpty())
            return Double.MAX_VALUE;
        long nonZero = demands.stream().filter(d -> d > 0).count();
        if (nonZero == 0)
            return Double.MAX_VALUE;
        return (double) demands.size() / nonZero;
    }

    /**
     * 计算需求量变异系数的平方 CV²
     * 仅对非零需求期计算，若非零期数 < 2 则返回 0
     */
    public static double calcCV2(List<Integer> demands) {
        if (demands == null)
            return 0.0;
        List<Integer> nonZero = demands.stream().filter(d -> d > 0).collect(Collectors.toList());
        if (nonZero.size() < 2)
            return 0.0;
        double mean = nonZero.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        if (mean == 0)
            return 0.0;
        double variance = nonZero.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average().orElse(0.0);
        double cv = Math.sqrt(variance) / mean;
        return cv * cv;
    }

    /**
     * 生成近12个月的月份字符串列表（yyyy-MM，从最早到最近）
     */
    public static List<String> buildLast12Months() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate now = LocalDate.now();
        List<String> months = new ArrayList<>(12);
        for (int i = 11; i >= 0; i--) {
            months.add(now.minusMonths(i).format(fmt));
        }
        return months;
    }
}
