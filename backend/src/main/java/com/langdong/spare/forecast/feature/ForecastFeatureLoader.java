package com.langdong.spare.forecast.feature;

import com.langdong.spare.dto.MonthlyConsumptionVO;
import com.langdong.spare.entity.AiDeviceFeature;
import com.langdong.spare.entity.EquipmentSparePart;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.mapper.AiDeviceFeatureMapper;
import com.langdong.spare.mapper.EquipmentSparePartMapper;
import com.langdong.spare.mapper.SparePartMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 预测特征加载服务（模块 B 的 DB 加载层）。
 *
 * <p>高性能、批量获取数据库特征数据，为每个备件组装 {@link PartFeatureContext}。
 * 遵循严格防泄露设计，所有加载进入 context 的历史月份都必须严格早于 targetMonth。</p>
 */
@Component
public class ForecastFeatureLoader {

    private static final Logger log = LoggerFactory.getLogger(ForecastFeatureLoader.class);

    private final SparePartMapper sparePartMapper;
    private final EquipmentSparePartMapper equipmentSparePartMapper;
    private final AiDeviceFeatureMapper aiDeviceFeatureMapper;

    public ForecastFeatureLoader(SparePartMapper sparePartMapper,
                                 EquipmentSparePartMapper equipmentSparePartMapper,
                                 AiDeviceFeatureMapper aiDeviceFeatureMapper) {
        this.sparePartMapper = sparePartMapper;
        this.equipmentSparePartMapper = equipmentSparePartMapper;
        this.aiDeviceFeatureMapper = aiDeviceFeatureMapper;
    }

    /**
     * 为目标月份批量加载所有备件的历史特征上下文。
     *
     * @param targetMonth   预测目标月份（yyyy-MM）
     * @param historyMonths 历史回看月数（通常为 36）
     * @return partCode -> 备件特征上下文
     */
    public Map<String, PartFeatureContext> loadAllContexts(String targetMonth, int historyMonths) {
        log.info("[特征加载] 开始为月份 {} 加载历史特征，回看月数: {}", targetMonth, historyMonths);

        YearMonth target = YearMonth.parse(targetMonth);
        YearMonth startMonth = target.minusMonths(historyMonths);

        // 1. 加载所有备件档案
        List<SparePart> parts = sparePartMapper.findAllForClassify();
        if (parts == null || parts.isEmpty()) {
            log.warn("[特征加载] 未找到任何备件档案");
            return Collections.emptyMap();
        }

        // 2. 批量加载月度消耗（一次性载入窗口期内所有消耗）
        String fromMonthStr = startMonth.atDay(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        List<MonthlyConsumptionVO> consumptions = sparePartMapper.findAllMonthlyConsumption(fromMonthStr);

        // 备件编码 -> (月份 -> 消耗量)，并应用防泄露过滤 (只保留 < targetMonth 的数据)
        Map<String, Map<String, Double>> consumptionMap = new HashMap<>();
        if (consumptions != null) {
            for (MonthlyConsumptionVO vo : consumptions) {
                if (vo.getPartCode() == null || vo.getMonth() == null) {
                    continue;
                }
                try {
                    YearMonth m = YearMonth.parse(vo.getMonth());
                    if (!m.isBefore(target)) {
                        continue; // 严格防泄露：抛弃 >= targetMonth 的消耗数据
                    }
                    double qty = vo.getQty() == null ? 0.0 : vo.getQty();
                    consumptionMap.computeIfAbsent(vo.getPartCode(), k -> new HashMap<>())
                            .put(vo.getMonth(), qty);
                } catch (Exception ignored) {
                }
            }
        }

        Map<String, PartFeatureContext> result = new HashMap<>();
        String fromMonthDeviceStr = startMonth.toString(); // 格式为 yyyy-MM

        // 3. 为每个备件单独组装上下文（含关联设备特征）
        for (SparePart part : parts) {
            String code = part.getCode();
            PartFeatureContext context = new PartFeatureContext(code);

            // 填充消耗历史
            Map<String, Double> demands = consumptionMap.getOrDefault(code, Collections.emptyMap());
            context.setMonthlyDemand(new HashMap<>(demands));

            // 获取关联的设备
            List<EquipmentSparePart> eqSps = equipmentSparePartMapper.findBySparePartId(part.getId());
            if (eqSps != null && !eqSps.isEmpty()) {
                List<Long> deviceIds = eqSps.stream()
                        .map(EquipmentSparePart::getEquipmentId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (!deviceIds.isEmpty()) {
                    // 查询设备特征记录
                    List<AiDeviceFeature> deviceFeatures = aiDeviceFeatureMapper.findByDeviceIds(deviceIds, fromMonthDeviceStr);
                    if (deviceFeatures != null) {
                        for (AiDeviceFeature f : deviceFeatures) {
                            if (f.getStatMonth() == null) {
                                continue;
                            }
                            try {
                                YearMonth m = YearMonth.parse(f.getStatMonth());
                                if (!m.isBefore(target)) {
                                    continue; // 严格防泄露：抛弃 >= targetMonth 的设备状态数据
                                }
                                double hrs = f.getRunHours() != null ? f.getRunHours().doubleValue() : 0.0;
                                double cnt = f.getWorkOrderCount() != null ? f.getWorkOrderCount().doubleValue() : 0.0;

                                context.getMonthlyEquipHr().merge(f.getStatMonth(), hrs, Double::sum);
                                context.getMonthlyRepairCnt().merge(f.getStatMonth(), cnt, Double::sum);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }

            result.put(code, context);
        }

        log.info("[特征加载] 特征载入完成，共处理备件 {} 个", result.size());
        return result;
    }
}
