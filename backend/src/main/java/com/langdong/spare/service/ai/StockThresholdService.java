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
import java.time.YearMonth;
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

    @Autowired
    private ReorderSuggestMapper reorderSuggestMapper;

    /**
     * 重算安全阈值并推送补货建议
     *
     * @param forecasts 本次生成的 AI 预测结果集
     * @param contexts  配套的预测上下文（包含提前期、当前库存等）
     */
    public void recalcAndPush(List<AiForecastResult> forecasts, List<PredictContextDTO> contexts) {
        if (forecasts == null || forecasts.isEmpty() || contexts == null || contexts.isEmpty())
            return;

        int suggestCount = 0;
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        int loopSize = Math.min(forecasts.size(), contexts.size());

        for (int i = 0; i < loopSize; i++) {
            AiForecastResult fr = forecasts.get(i);
            PredictContextDTO ctx = contexts.get(i);
            if (fr == null || ctx == null || fr.getPredictQty() == null || ctx.getPartCode() == null) {
                continue;
            }

            String suggestMonth = currentMonth;
            if (fr.getForecastMonth() != null) {
                try {
                    suggestMonth = YearMonth.parse(fr.getForecastMonth()).toString();
                } catch (Exception ignored) {
                    suggestMonth = currentMonth;
                }
            }

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

            // 6. 读取当前真实库存（优先 spare_part_stock，兜底 spare_part.quantity）
            Integer stock = reorderSuggestMapper.findCurrentStockByPartCode(ctx.getPartCode());
            int currentStock = stock == null ? 0 : Math.max(stock, 0);

            // 7. 判断是否需要推送补货建议
            if (currentStock <= reorderPoint) {
                ReorderSuggest suggest = new ReorderSuggest();
                suggest.setPartCode(ctx.getPartCode());
                suggest.setSuggestMonth(suggestMonth);
                suggest.setCurrentStock(currentStock);
                suggest.setReorderPoint(reorderPoint);
                suggest.setSuggestQty(fr.getPredictQty().intValue());
                suggest.setForecastQty(fr.getPredictQty());
                suggest.setLowerBound(fr.getLowerBound());
                suggest.setUpperBound(fr.getUpperBound());
                suggest.setUrgency(currentStock < safetyStock ? "紧急" : "正常");
                suggest.setStatus("待处理");

                // 同月同备件仅保留一条待处理建议，重复运行时覆盖旧建议
                reorderSuggestMapper.deletePendingByPartAndMonth(suggest.getPartCode(), suggestMonth);
                reorderSuggestMapper.insert(suggest);
                suggestCount++;
            }
        }

        log.info("[库存预警] 阈值重算完成，共生成补货建议推送: {} 条", suggestCount);
    }
}
