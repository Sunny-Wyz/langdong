package com.langdong.spare.service.ai;

import com.langdong.spare.dto.PredictContextDTO;
import com.langdong.spare.entity.AiForecastResult;
import com.langdong.spare.mapper.AiForecastResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI智能预测核心调度服务
 *
 * 负责串联：
 * 1. 特征工程 (AiFeatureService)
 * 2. 模型分配与预测 (SBA / RF)
 * 3. 结果保存入库
 * 4. 阈值与补货联动 (StockThresholdService)
 */
@Service
public class AiForecastService {

    private static final Logger log = LoggerFactory.getLogger(AiForecastService.class);

    @Autowired
    private AiFeatureService aiFeatureService;

    @Autowired
    private RandomForestServiceImpl randomForestService;

    @Autowired
    private SbaForecastServiceImpl sbaForecastService;

    @Autowired
    private StockThresholdService stockThresholdService;

    @Autowired
    private AiForecastResultMapper aiForecastResultMapper;

    /**
     * 定时任务：每月 1 日凌晨 2 点运行
     * (比分类模块的 1 点晚，确保能拿到最新的 ABC 分类结果)
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void scheduledForecast() {
        log.info("[AI预测] 每月定时任务触发...");
        runFullForecast();
    }

    /**
     * 全量执行 AI 分析预测流水线（异步防止前端阻塞）
     */
    @Async
    public void runFullForecast() {
        log.info("[AI预测] 开始挂起全部备件的预测任务");

        // 目标月份（下个月）
        String targetMonth = LocalDate.now().plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        try {
            // 第一步：特征工程，组装所有备件的上下文数据
            List<PredictContextDTO> contexts = aiFeatureService.buildAllContexts(targetMonth);
            if (contexts.isEmpty()) {
                log.warn("[AI预测] 没有得到任何可预测的上下文，任务结束");
                return;
            }

            // 第二步：执行分型预测
            List<AiForecastResult> results = new ArrayList<>(contexts.size());
            for (PredictContextDTO ctx : contexts) {
                AiForecastResult res;
                if ("SBA".equals(ctx.getAlgoType())) {
                    res = sbaForecastService.predict(ctx);
                } else if ("RF".equals(ctx.getAlgoType())) {
                    res = randomForestService.predict(ctx);
                } else {
                    res = randomForestService.buildFallbackResult(ctx);
                }
                results.add(res);
            }

            // 第三步：批量保存预测结果
            if (!results.isEmpty()) {
                aiForecastResultMapper.insertBatch(results);
                log.info("[AI预测] 成功写入全量预测结果 {} 条", results.size());
            }

            // 第四步：触发安全库存重算与补货联动
            stockThresholdService.recalcAndPush(results, contexts);

        } catch (Exception e) {
            log.error("[AI预测] 核心流水线执行异常", e);
        }
    }

    /**
     * 分页查询预测结果
     */
    public Map<String, Object> queryResult(String month, String partCode, int page, int size) {
        String mon = (month != null && !month.trim().isEmpty()) ? month.trim() : null;
        String code = (partCode != null && !partCode.trim().isEmpty()) ? partCode.trim() : null;
        int offset = (page - 1) * size;

        List<AiForecastResult> list = aiForecastResultMapper.findByPage(mon, code, offset, size);
        long total = aiForecastResultMapper.countByPage(mon, code);

        log.info("[AI查询] 结果: month={}, partCode={}, page={}, size={}, 实际页数据条数={}, 总条数={}",
                mon, code, page, size, list.size(), total);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }

    /**
     * 获取指定备件的历史预测
     */
    public List<AiForecastResult> queryHistory(String partCode) {
        return aiForecastResultMapper.findByPartCode(partCode);
    }

}
