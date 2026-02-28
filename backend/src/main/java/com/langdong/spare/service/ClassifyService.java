package com.langdong.spare.service;

import com.langdong.spare.dto.MonthlyConsumptionVO;
import com.langdong.spare.entity.PartClassify;
import com.langdong.spare.entity.SparePart;
import com.langdong.spare.mapper.PartClassifyMapper;
import com.langdong.spare.mapper.SparePartMapper;
import com.langdong.spare.util.ClassifyCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 备件智能分类服务
 *
 * 负责：
 *   1. 全量分类重算（ABC + XYZ + SS + ROP）
 *   2. 分类结果查询（分页、历史、矩阵）
 *   3. 定时任务：每月1日凌晨1点自动重算
 */
@Service
public class ClassifyService {

    private static final Logger log = LoggerFactory.getLogger(ClassifyService.class);

    @Autowired
    private SparePartMapper sparePartMapper;

    @Autowired
    private PartClassifyMapper partClassifyMapper;

    // ================================================================
    // 1. 全量分类重算（异步触发入口）
    // ================================================================

    /**
     * 异步触发全量分类重算
     * 由 Controller 调用（手动触发）或定时任务调用
     * 使用 @Async 确保不阻塞调用方线程
     */
    @Async
    public void runFullClassify() {
        log.info("[分类重算] 开始执行全量分类重算...");
        try {
            doClassify();
            log.info("[分类重算] 全量分类重算执行完成");
        } catch (Exception e) {
            log.error("[分类重算] 全量分类重算执行异常", e);
        }
    }

    /**
     * 定时任务：每月1日凌晨1点自动触发全量重算
     * cron 表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 1 1 * ?")
    public void scheduledClassify() {
        log.info("[分类重算] 定时任务触发全量分类重算（每月1日凌晨1点）");
        runFullClassify();
    }

    // ================================================================
    // 2. 核心分类计算流程
    // ================================================================

    /**
     * 执行分类计算的核心逻辑（同步执行）
     *
     * 步骤：
     *   1. 查询所有备件档案
     *   2. 查询近12个月月度消耗汇总
     *   3. 在内存中完成 ABC/XYZ 分类 + SS/ROP 计算
     *   4. 批量写入 biz_part_classify 表
     */
    private void doClassify() {
        // 当前分类月份（格式：yyyy-MM）
        String classifyMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        // ---- 第1步：查询所有备件档案 ----
        List<SparePart> parts = sparePartMapper.findAllForClassify();
        if (parts == null || parts.isEmpty()) {
            log.warn("[分类重算] 备件档案为空，跳过计算");
            return;
        }
        log.info("[分类重算] 共读取备件 {} 条", parts.size());

        // ---- 第2步：查询近12个月月度消耗 ----
        // 起始日期：当前日期往前12个月
        String fromMonth = LocalDate.now()
                .minusMonths(12)
                .withDayOfMonth(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        List<MonthlyConsumptionVO> consumptionList = sparePartMapper.findAllMonthlyConsumption(fromMonth);

        // 按 partCode 分组，key=备件编码，value=Map<月份, 消耗量>
        Map<String, Map<String, Integer>> consumptionMap = new HashMap<>();
        if (consumptionList != null) {
            for (MonthlyConsumptionVO vo : consumptionList) {
                consumptionMap
                        .computeIfAbsent(vo.getPartCode(), k -> new LinkedHashMap<>())
                        .put(vo.getMonth(), vo.getQty());
            }
        }
        log.info("[分类重算] 共读取月度消耗记录 {} 条（涉及 {} 个备件）",
                consumptionList == null ? 0 : consumptionList.size(), consumptionMap.size());

        // ---- 第3步：计算每个备件的年消耗金额和综合得分 ----
        // 中间结果：partCode -> 年消耗金额
        Map<String, Double> annualCostMap = new HashMap<>();
        // 中间结果：partCode -> 近12月月度消耗量列表（含0需求月份，共12个数据点）
        Map<String, List<Integer>> monthlyDemandMap = new HashMap<>();

        // 生成近12个月的月份列表（用于补全0需求月份）
        List<String> last12Months = buildLast12Months();

        for (SparePart part : parts) {
            String code = part.getCode();
            Map<String, Integer> monthQtyMap = consumptionMap.getOrDefault(code, Collections.emptyMap());

            // 补全12个月数据（没有消耗记录的月份填0）
            List<Integer> demands = new ArrayList<>(12);
            for (String m : last12Months) {
                demands.add(monthQtyMap.getOrDefault(m, 0));
            }
            monthlyDemandMap.put(code, demands);

            // 年消耗金额 = 12个月总消耗量 × 单价
            int annualQty = demands.stream().mapToInt(Integer::intValue).sum();
            double unitPrice = (part.getPrice() != null) ? part.getPrice().doubleValue() : 0.0;
            annualCostMap.put(code, annualQty * unitPrice);
        }

        // 最大年消耗金额（用于 min-max 归一化）
        double maxAnnualCost = annualCostMap.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        // ---- 第4步：计算综合得分并排序，确定 ABC 分类 ----
        // partCode -> 综合得分
        Map<String, Double> scoreMap = new HashMap<>();
        for (SparePart part : parts) {
            String code = part.getCode();
            double annualCostScore  = ClassifyCalculator.calcAnnualCostScore(
                    annualCostMap.getOrDefault(code, 0.0), maxAnnualCost);
            double criticalScore    = ClassifyCalculator.calcCriticalScore(part.getIsCritical());
            double leadTimeScore    = ClassifyCalculator.calcLeadTimeScore(part.getLeadTime());
            double replaceDiffScore = ClassifyCalculator.calcReplaceDiffScore(part.getReplaceDiff());
            double compositeScore   = ClassifyCalculator.calcCompositeScore(
                    annualCostScore, criticalScore, leadTimeScore, replaceDiffScore);
            scoreMap.put(code, ClassifyCalculator.round(compositeScore, 2));
        }

        // 按综合得分降序排列（得分最高的 rank=1）
        List<String> sortedCodes = parts.stream()
                .map(SparePart::getCode)
                .sorted((a, b) -> Double.compare(scoreMap.getOrDefault(b, 0.0), scoreMap.getOrDefault(a, 0.0)))
                .collect(Collectors.toList());
        int totalCount = sortedCodes.size();

        // partCode -> ABC 分类（根据排名分位数）
        Map<String, String> abcClassMap = new HashMap<>();
        for (int i = 0; i < sortedCodes.size(); i++) {
            String code = sortedCodes.get(i);
            // rank 从1开始
            String abcClass = ClassifyCalculator.classifyABC(totalCount, i + 1);
            abcClassMap.put(code, abcClass);
        }

        // ---- 第5步：计算 XYZ 分类、SS、ROP，组装结果 ----
        List<PartClassify> resultList = new ArrayList<>(parts.size());

        for (SparePart part : parts) {
            String code = part.getCode();
            List<Integer> demands = monthlyDemandMap.getOrDefault(code, Collections.emptyList());

            // XYZ分类
            double cv2 = ClassifyCalculator.calcCV2(demands);
            // 有效月份数（消耗量>0的月份数）
            int nonZeroMonths = (int) demands.stream().filter(d -> d > 0).count();
            String xyzClass = ClassifyCalculator.classifyXYZ(cv2, nonZeroMonths);

            // ABC分类
            String abcClass = abcClassMap.getOrDefault(code, "C");

            // 月均消耗量 & 月标准差（用于 SS/ROP）
            double avgMonthlyQty = demands.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            double monthlyStdDev = ClassifyCalculator.calcMonthlyStdDev(demands);

            // 安全库存 SS 和补货触发点 ROP
            int safetyStock  = ClassifyCalculator.calcSafetyStock(abcClass, monthlyStdDev, part.getLeadTime());
            int reorderPoint = ClassifyCalculator.calcReorderPoint(avgMonthlyQty, part.getLeadTime(), safetyStock);

            // 组装分类结果记录
            PartClassify classify = new PartClassify();
            classify.setPartCode(code);
            classify.setClassifyMonth(classifyMonth);
            classify.setAbcClass(abcClass);
            classify.setXyzClass(xyzClass);
            classify.setCompositeScore(BigDecimal.valueOf(scoreMap.getOrDefault(code, 0.0)));
            classify.setAnnualCost(BigDecimal.valueOf(
                    ClassifyCalculator.round(annualCostMap.getOrDefault(code, 0.0), 2)));
            // CV² 若为 MAX_VALUE（无消耗），存0
            double cv2Stored = (cv2 == Double.MAX_VALUE) ? 0.0 : ClassifyCalculator.round(cv2, 4);
            classify.setCv2(BigDecimal.valueOf(cv2Stored));
            classify.setSafetyStock(safetyStock);
            classify.setReorderPoint(reorderPoint);
            classify.setServiceLevel(BigDecimal.valueOf(ClassifyCalculator.getServiceLevel(abcClass)));
            classify.setStrategyCode(abcClass + xyzClass);

            resultList.add(classify);
        }

        // ---- 第6步：批量写入数据库 ----
        if (!resultList.isEmpty()) {
            partClassifyMapper.insertBatch(resultList);
            log.info("[分类重算] 分类结果已写入数据库，共 {} 条，分类月份：{}", resultList.size(), classifyMonth);
        }
    }

    /**
     * 生成近12个月的月份字符串列表（yyyy-MM格式，从最早到最近）
     * 例如当前月为2026-02，则返回 [2025-03, 2025-04, ..., 2026-02]
     */
    private List<String> buildLast12Months() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate now = LocalDate.now();
        List<String> months = new ArrayList<>(12);
        for (int i = 11; i >= 0; i--) {
            months.add(now.minusMonths(i).format(fmt));
        }
        return months;
    }

    // ================================================================
    // 3. 查询接口
    // ================================================================

    /**
     * 分页查询最新分类结果
     *
     * @param abcClass  ABC分类过滤（null=全部）
     * @param xyzClass  XYZ分类过滤（null=全部）
     * @param partCode  备件编码关键词（null=全部）
     * @param month     分类月份（null=最新月份）
     * @param page      页码（从1开始）
     * @param pageSize  每页条数
     * @return 包含 total 和 list 的分页结果 Map
     */
    public Map<String, Object> queryResult(String abcClass, String xyzClass,
                                            String partCode, String month,
                                            int page, int pageSize) {
        // 处理空字符串
        String abc  = (abcClass  != null && !abcClass.trim().isEmpty())  ? abcClass.trim()  : null;
        String xyz  = (xyzClass  != null && !xyzClass.trim().isEmpty())  ? xyzClass.trim()  : null;
        String code = (partCode  != null && !partCode.trim().isEmpty())  ? partCode.trim()  : null;
        String mon  = (month     != null && !month.trim().isEmpty())     ? month.trim()     : null;

        int offset = (page - 1) * pageSize;
        List<PartClassify> list  = partClassifyMapper.findLatestByPage(abc, xyz, code, mon, offset, pageSize);
        long total               = partClassifyMapper.countLatest(abc, xyz, code, mon);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("list", list);
        return result;
    }

    /**
     * 查询指定备件的分类历史（按月份升序）
     *
     * @param partCode 备件编码
     */
    public List<PartClassify> queryHistory(String partCode) {
        return partClassifyMapper.findHistoryByPartCode(partCode);
    }

    /**
     * 查询 ABC×XYZ 9格矩阵的备件数量分布
     * 返回格式：{ "AX": 12, "AY": 5, "AZ": 2, "BX": 30, ... }
     */
    public Map<String, Long> queryMatrix() {
        // 初始化9个格子全为0
        String[] abcClasses = {"A", "B", "C"};
        String[] xyzClasses = {"X", "Y", "Z"};
        Map<String, Long> matrix = new LinkedHashMap<>();
        for (String abc : abcClasses) {
            for (String xyz : xyzClasses) {
                matrix.put(abc + xyz, 0L);
            }
        }

        // 查询数据库中实际的统计结果并填充
        List<Map<String, Object>> counts = partClassifyMapper.findMatrixCount();
        if (counts != null) {
            for (Map<String, Object> row : counts) {
                String key = String.valueOf(row.get("abcClass")) + String.valueOf(row.get("xyzClass"));
                Object cntObj = row.get("cnt");
                long cnt = (cntObj instanceof Number) ? ((Number) cntObj).longValue() : 0L;
                matrix.put(key, cnt);
            }
        }

        return matrix;
    }
}
