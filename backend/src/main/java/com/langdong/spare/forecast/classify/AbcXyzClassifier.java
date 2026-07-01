package com.langdong.spare.forecast.classify;

import com.langdong.spare.dto.MonthlyConsumptionVO;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.forecast.config.ForecastProperties;
import com.langdong.spare.forecast.feature.MonthlyClassCodeProvider;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ABC×XYZ 批量分类器（模块 A，服务两阶段预测特征）。
 *
 * <p>独立于 F9 的 {@code ClassifyService}，不写 {@code biz_part_classify}，仅按论文规格计算
 * ABC_code / XYZ_code 供 FeatureBuilder 使用。分类需横向比较全量备件（帕累托 + min-max），
 * 故按「截止月份」批量计算。</p>
 *
 * <p><b>防泄露：</b>{@code classifyAsOf(asOfMonth)} 仅统计「严格早于 asOfMonth」的消耗
 * （对应论文「仅统计到分类月份前一个月」），因此用于预测目标月 t 的 {@code codesAsOf(part, t-1)}
 * 绝不触及 ≥ t 的数据。</p>
 */
@Service
public class AbcXyzClassifier {

    private static final Logger log = LoggerFactory.getLogger(AbcXyzClassifier.class);

    /** 年消耗金额与 CV² 的回看窗口（月）。年消耗金额按滚动 12 个月口径。 */
    private static final int WINDOW_MONTHS = 12;

    private final SparePartMapper sparePartMapper;
    private final ForecastProperties props;

    public AbcXyzClassifier(SparePartMapper sparePartMapper, ForecastProperties props) {
        this.sparePartMapper = sparePartMapper;
        this.props = props;
    }

    /**
     * 计算截止 {@code asOfMonth}（不含该月）的全量备件 ABC×XYZ 分类。
     *
     * @param asOfMonth 分类截止月份（yyyy-MM）；统计窗口为 [asOfMonth-12, asOfMonth)
     * @return partCode → 分类结果
     */
    public Map<String, AbcXyzCalculator.Classification> classifyAsOf(String asOfMonth) {
        YearMonth asOf = YearMonth.parse(asOfMonth);
        List<SparePart> parts = sparePartMapper.findAllForClassify();
        if (parts == null || parts.isEmpty()) {
            return new LinkedHashMap<>();
        }

        // 窗口月份集合：严格早于 asOfMonth 的最近 12 个月
        List<String> windowMonths = new ArrayList<>(WINDOW_MONTHS);
        for (int i = WINDOW_MONTHS; i >= 1; i--) {
            windowMonths.add(asOf.minusMonths(i).toString());
        }

        // 一次性加载窗口内消耗（fromMonth 为窗口最早月的 1 号）
        String fromMonth = asOf.minusMonths(WINDOW_MONTHS).atDay(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        List<MonthlyConsumptionVO> consumption = sparePartMapper.findAllMonthlyConsumption(fromMonth);

        // partCode -> (month -> qty)，仅保留窗口内月份（防泄露：丢弃 >= asOfMonth 的月份）
        Map<String, Map<String, Double>> byPart = new HashMap<>();
        if (consumption != null) {
            for (MonthlyConsumptionVO vo : consumption) {
                if (vo == null || vo.getPartCode() == null || vo.getMonth() == null) {
                    continue;
                }
                YearMonth m;
                try {
                    m = YearMonth.parse(vo.getMonth());
                } catch (Exception ex) {
                    continue;
                }
                if (!m.isBefore(asOf)) {
                    continue; // 丢弃 >= asOfMonth 的数据
                }
                double qty = vo.getQty() == null ? 0.0 : vo.getQty();
                byPart.computeIfAbsent(vo.getPartCode(), k -> new HashMap<>()).put(vo.getMonth(), qty);
            }
        }

        // 组装计算输入
        List<AbcXyzCalculator.PartInput> inputs = new ArrayList<>(parts.size());
        for (SparePart part : parts) {
            String code = part.getCode();
            Map<String, Double> mq = byPart.getOrDefault(code, new HashMap<>());

            // 窗口内月度序列（缺失月补 0）
            List<Double> series = new ArrayList<>(WINDOW_MONTHS);
            double sum = 0.0;
            for (String wm : windowMonths) {
                double q = mq.getOrDefault(wm, 0.0);
                series.add(q);
                sum += q;
            }

            double price = part.getPrice() == null ? 0.0 : part.getPrice().doubleValue();
            double annualCost = price * sum;
            double criticalityRaw = (part.getIsCritical() != null && part.getIsCritical() == 1) ? 1.0 : 0.0;
            double leadTimeRaw = part.getLeadTime() != null ? part.getLeadTime() : 0.0;
            double replaceDiffRaw = part.getReplaceDiff() != null ? part.getReplaceDiff() : 3.0;

            inputs.add(new AbcXyzCalculator.PartInput(
                    code, annualCost, criticalityRaw, leadTimeRaw, replaceDiffRaw, series));
        }

        Map<String, AbcXyzCalculator.Classification> result =
                AbcXyzCalculator.classifyAll(inputs, props.getClassify());
        log.debug("[ABC×XYZ] asOf={} 分类完成，备件数={}", asOfMonth, result.size());
        return result;
    }

    /**
     * 返回带缓存的分类码提供者：每个不同的 asOfMonth 仅计算一次。
     *
     * <p>用于 FeatureBuilder 批量构造跨月训练矩阵，避免同月重复全量分类。</p>
     */
    public MonthlyClassCodeProvider codeProviderWithCache() {
        final Map<String, Map<String, AbcXyzCalculator.Classification>> cache = new HashMap<>();
        return (partCode, asOfMonth) -> {
            Map<String, AbcXyzCalculator.Classification> monthMap =
                    cache.computeIfAbsent(asOfMonth, this::classifyAsOf);
            AbcXyzCalculator.Classification c = monthMap.get(partCode);
            if (c == null) {
                return new int[]{1, 3}; // 默认 C/Z
            }
            return new int[]{c.abcCode(), c.xyzCode()};
        };
    }

    /** 便捷：将 BigDecimal 价格安全转 double（供外部复用）。 */
    static double priceToDouble(BigDecimal price) {
        return price == null ? 0.0 : price.doubleValue();
    }
}
