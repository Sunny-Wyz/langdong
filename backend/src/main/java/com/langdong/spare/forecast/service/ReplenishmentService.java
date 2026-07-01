package com.langdong.spare.forecast.service;

import com.langdong.spare.entity.ReorderSuggest;
import com.langdong.spare.forecast.model.ForecastResult;
import com.langdong.spare.mapper.ReorderSuggestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能补货推送业务服务（模块 G）。
 *
 * <p>比对备件当前可用库存与补货点 ROP，对于低于 ROP 的备件生成补货建议，
 * 计算推荐采购量并标记紧急度，最终按紧急程度降序幂等持久化落库（对应测试 TC-PO-01）。</p>
 */
@Service
public class ReplenishmentService {

    private static final Logger log = LoggerFactory.getLogger(ReplenishmentService.class);

    private final ReorderSuggestMapper reorderSuggestMapper;

    public ReplenishmentService(ReorderSuggestMapper reorderSuggestMapper) {
        this.reorderSuggestMapper = reorderSuggestMapper;
    }

    /**
     * 根据两阶段预测与安全库存结果生成补货建议。
     *
     * @param suggestMonth 建议属期月份（yyyy-MM）
     * @param forecasts    两阶段预测计算结果集
     * @return 最终生成的且按紧急度排序的补货建议集合
     */
    @Transactional
    public List<ReorderSuggest> generateReplenishmentSuggestions(String suggestMonth, List<ForecastResult> forecasts) {
        if (forecasts == null || forecasts.isEmpty()) {
            return new ArrayList<>();
        }

        log.info("[智能补货] 开始计算补货建议，目标月份: {}，预测条数: {}", suggestMonth, forecasts.size());
        List<ReorderSuggest> suggestionList = new ArrayList<>();

        for (ForecastResult fr : forecasts) {
            // 跳过数据不足或计算失败的记录
            if (fr == null || fr.isDataInsufficient()) {
                continue;
            }

            String partCode = fr.getPartCode();
            int reorderPoint = fr.getReorderPoint() != null ? fr.getReorderPoint() : 0;
            int safetyStock = fr.getSafetyStock() != null ? fr.getSafetyStock() : 0;

            // 1. 获取当前可用库存（优先读仓储总台账，兜底物理库存）
            Integer stock = reorderSuggestMapper.findCurrentStockByPartCode(partCode);
            int currentStock = stock == null ? 0 : Math.max(stock, 0);

            // 2. 核心比对：当前可用库存是否触及/低于补货点 ROP
            if (currentStock <= reorderPoint) {
                // 3. 计算采购量：建议采购量 = 补货点 - 当前库存（至少为 1 件）
                int suggestQty = Math.max(1, reorderPoint - currentStock);

                ReorderSuggest suggest = new ReorderSuggest();
                suggest.setPartCode(partCode);
                suggest.setSuggestMonth(suggestMonth);
                suggest.setCurrentStock(currentStock);
                suggest.setReorderPoint(reorderPoint);
                suggest.setSuggestQty(suggestQty);
                suggest.setForecastQty(BigDecimal.valueOf(fr.getDemandHat()));
                suggest.setLowerBound(BigDecimal.valueOf(fr.getLowerBound()));
                suggest.setUpperBound(BigDecimal.valueOf(fr.getUpperBound()));

                // 4. 判定紧急度：可用库存低于安全库存 SS 时为 紧急，否则为 正常
                suggest.setUrgency(currentStock < safetyStock ? "紧急" : "正常");
                suggest.setStatus("待处理");

                suggestionList.add(suggest);
            }
        }

        // 5. 对生成结果排序：紧急排最前，正常排后（二次按编码排序）
        suggestionList.sort((a, b) -> {
            if (a.getUrgency().equals(b.getUrgency())) {
                return a.getPartCode().compareTo(b.getPartCode());
            }
            return "紧急".equals(a.getUrgency()) ? -1 : 1;
        });

        // 6. 事务内幂等持久化落库
        int insertCount = 0;
        for (ReorderSuggest suggest : suggestionList) {
            // 同月同备件仅保留最新的一条待处理建议，重复生成时覆盖
            reorderSuggestMapper.deletePendingByPartAndMonth(suggest.getPartCode(), suggestMonth);
            reorderSuggestMapper.insert(suggest);
            insertCount++;
        }

        log.info("[智能补货] 建议计算完毕，共向数据库推送 {} 条补货建议", insertCount);
        return suggestionList;
    }
}
