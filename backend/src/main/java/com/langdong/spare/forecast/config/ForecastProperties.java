package com.langdong.spare.forecast.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 需求预测与安全库存总配置。
 *
 * <p>集中管理蒙特卡洛参数、随机种子、ABC/XYZ 分档阈值、加权评分权重、服务水平映射等。
 * 凡提示词标注「⚠️待确认」的参数，默认值均在此列出，便于与论文逐条核对。
 * 全部可通过 application.yml 的 {@code forecast.*} 覆盖。</p>
 */
@Data
@ConfigurationProperties(prefix = "forecast")
public class ForecastProperties {

    /** 蒙特卡洛安全库存模拟参数。 */
    private MonteCarlo monteCarlo = new MonteCarlo();

    /** ABC×XYZ 分类参数。 */
    private Classify classify = new Classify();

    /** 特征窗口：历史统计回看月数（默认 36，覆盖论文前 30 月训练场景）。 */
    private int historyMonths = 36;

    /** 是否开启增量快照训练优化（月初重算时避开无新领用的备件，默认开启）。 */
    private boolean incrementalEnabled = true;

    /** 模型训练快照文件保存基目录（支持相对/绝对路径）。 */
    private String modelBaseDir = "target/models/";

    /**
     * 蒙特卡洛模拟配置（算法 3-2）。
     */
    @Data
    public static class MonteCarlo {
        /** 蒙特卡洛随机种子（论文硬约束，固定 20260518，不得更改）。 */
        private long seed = 20260518L;
        /** 模拟次数 M。 */
        private int simulations = 10000;
        /** 月工作天数 W。 */
        private int workingDays = 22;
        /** 90% 区间反推标准差用的正态分位常数（1.645 = z_{0.95}）。 */
        private double intervalZ = 1.645;
    }

    /**
     * ABC×XYZ 分类配置（论文 4.2.2 / 4.1.3.2）。
     * <p>⚠️待确认项均给出论文默认值。ABC_code / XYZ_code 编码已被论文表 3-2 写死，不提供覆盖入口。</p>
     */
    @Data
    public static class Classify {
        // ---- ABC 加权综合评分权重（合计 1.0）----
        /** 年消耗金额权重。 */
        private double weightAnnualCost = 0.40;
        /** 设备关键度权重。 */
        private double weightCriticality = 0.25;
        /** 采购提前期权重。 */
        private double weightLeadTime = 0.20;
        /** 供应替代难度权重。 */
        private double weightReplaceDiff = 0.15;

        // ---- ABC 帕累托分档（累计占比）----
        /** A 类累计占比上界（前 70%）。 */
        private double abcCutoffA = 0.70;
        /** B 类累计占比上界（70%~90%）。 */
        private double abcCutoffB = 0.90;

        // ---- XYZ 按 CV² 分档 ----
        /** X/Y 分界：CV² < 0.5 → X。 */
        private double xyzCutoffX = 0.5;
        /** Y/Z 分界：0.5 ≤ CV² < 1.0 → Y，否则 Z。 */
        private double xyzCutoffY = 1.0;

        // ---- 服务水平 α（按 ABC 取，用作蒙特卡洛分位数）----
        /** A 类服务水平。 */
        private double serviceLevelA = 0.99;
        /** B 类服务水平。 */
        private double serviceLevelB = 0.95;
        /** C 类服务水平。 */
        private double serviceLevelC = 0.90;

        /**
         * 按 ABC 等级返回服务水平 α。未知等级按 C 类保守取 0.90。
         */
        public double serviceLevelOf(String abcClass) {
            if (abcClass == null) {
                return serviceLevelC;
            }
            switch (abcClass.trim().toUpperCase()) {
                case "A":
                    return serviceLevelA;
                case "B":
                    return serviceLevelB;
                default:
                    return serviceLevelC;
            }
        }
    }
}
