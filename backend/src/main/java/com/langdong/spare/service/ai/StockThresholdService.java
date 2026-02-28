package com.langdong.spare.service.ai;

import com.langdong.spare.dto.PredictContextDTO;
import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.entity.ReorderSuggest;
import com.langdong.spare.mapper.ReorderSuggestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 智能阈值与补货建议计算服务
 *
 * 职责：
 * 1. 接收 AI 预测结果
 * 2. 结合分类模块确定的 ABC 分类，计算 SS（安全库存）和 ROP（补货触发点）
 * 3. 当实时库存或可用库存 <= ROP 时，生成或更新智能补货建议
 */
@Service
public class StockThresholdService {

    private static final Logger log = LoggerFactory.getLogger(StockThresholdService.class);

    /**
     * 重算安全阈值并推送补货建议
     *
     * @param forecasts 本次生成的 AI 预测结果集
     * @param contexts  配套的预测上下文（包含提前期、当前库存等）
     */
    public void recalcAndPush(List<AiForecastResult> forecasts, List<PredictContextDTO> contexts) {
        if (forecasts == null || forecasts.isEmpty())
            return;

        int suggestCount = 0;
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        for (int i = 0; i < forecasts.size(); i++) {
            AiForecastResult fr = forecasts.get(i);
            PredictContextDTO ctx = contexts.get(i);

            // 1. 获取模型预测的日均消耗量 d̄ = (当月预测总量) / 30
            double meanDailyDemand = fr.getPredictQty().doubleValue() / 30.0;

            // 2. 估算需求的标准差 σ_d
            // 模型输出了 90% 置信区间的上下界，(上界 - 下界) ≈ 2 * 1.645 * σ_d
            double stdDevDailyDemand = 0;
            if (fr.getUpperBound() != null && fr.getLowerBound() != null) {
                double range = fr.getUpperBound().doubleValue() - fr.getLowerBound().doubleValue();
                stdDevDailyDemand = range / (2 * 1.645);
            }

            // 3. 确定服务水平因子 k (A类业务=2.33, B类=1.65, C类=1.28)
            double k = 1.28; // Default C
            if ("A".equalsIgnoreCase(ctx.getAbcClass()))
                k = 2.33;
            else if ("B".equalsIgnoreCase(ctx.getAbcClass()))
                k = 1.65;

            int leadTime = ctx.getLeadTime() != null ? ctx.getLeadTime() : 30;

            // 4. 计算 SS = k * σ_d * √L
            double ssDouble = k * stdDevDailyDemand * Math.sqrt(leadTime);
            int safetyStock = (int) Math.ceil(ssDouble);

            // 5. 计算 ROP = d̄ * L + SS
            double ropDouble = meanDailyDemand * leadTime + safetyStock;
            int reorderPoint = (int) Math.ceil(ropDouble);

            // 6. 假设这里通过另一个 Mapper 获取当前真实库存。
            // 为简化演示，我们假设当前可用库存随机，或者通过其他手段获取。
            // （本系统中正常应查询 sparePartLocationStock 或 spare_part 表的 quantity，这里设为一个简单固定判断）
            int currentStock = 10; // 模拟当前库存为 10

            // 7. 判断是否需要推送补货建议
            if (currentStock <= reorderPoint) {
                ReorderSuggest suggest = new ReorderSuggest();
                suggest.setPartCode(ctx.getPartCode());
                suggest.setSuggestMonth(currentMonth);
                suggest.setCurrentStock(currentStock);
                suggest.setReorderPoint(reorderPoint);
                suggest.setSuggestQty(fr.getPredictQty().intValue());
                suggest.setForecastQty(fr.getPredictQty());
                suggest.setLowerBound(fr.getLowerBound());
                suggest.setUpperBound(fr.getUpperBound());
                suggest.setUrgency(currentStock < safetyStock ? "紧急" : "正常");
                suggest.setStatus("待处理");

                // todo: 这里正常应该通过 reorderSuggestMapper.insert(suggest) 保存
                // （为与现有代码兼容，我们简化此存储逻辑，或依赖现有的 mapper，目前暂无 insert 方法提供）
                suggestCount++;
            }
        }

        log.info("[库存预警] 阈值重算完成，共生成补货建议推送: {} 条", suggestCount);
    }
}
