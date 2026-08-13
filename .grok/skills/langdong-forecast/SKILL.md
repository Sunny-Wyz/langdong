---
name: langdong-forecast
description: Use when changing demand forecast, Hurdle-Gamma, TWO_STAGE, safety stock, ROP, Monte Carlo, inventory-calc, StockThresholdService, or monthly scheduler. Also when a user mentions RF, SBA, XGBoost4J, or dynamic SS=kσ√L as production.
---

# 月度预测主链路

算法推导见 `docs/AI_ALGORITHMS/两阶段Hurdle-Gamma与蒙特卡洛.md`。改代码前先读该文和下列类，不要按 RF/SBA 或 JVM 内 XGBoost4J 写新路径。

## 该走哪条

```
目标月 = 下月（ForecastTargetMonths）
    → FeatureBuilder 11 维（只读 < 目标月）
    → POST /api/algorithm/train     全量样本
    → POST /api/algorithm/predict   p_t, μ_t, 90% 区间
    → LeadTimeDemandSimulator       区间反推 k_MC
    → POST /api/algorithm/inventory-calc
    → 落库 ai_forecast_result + biz_part_classify
    → ReplenishmentService
```

入口：`MonthlyForecastScheduler`（月初 cron）、`/api/ai/forecast/trigger`、`HurdleGammaJobService`（可按编码过滤推理；**训练仍全量**）。

`AiForecastService` 只做查询和回调落库，不再训练。

## 模型

- 阶段一：`XGBClassifier(binary:logistic)` → \(p_t\)
- 阶段二：`XGBRegressor(reg:gamma)`，仅 \(y>0\) → \(\mu_t\)
- \(k\)：残差 \(y/\mu\) 的 MLE，按 XYZ 共享，推理时 clip 到 `[0.8, 12]`
- 点预测 \(\hat D = p_t \mu_t\)
- 90% 区间是**正需求条件区间**，不是含零的无条件区间

周粒度 `DemandForecaster`（19 维）不是月初全量重算路径。

## 蒙特卡洛（现行 SS/ROP）

\[
\mathrm{ROP}=\lceil Q_\alpha\rceil,\quad
\mathrm{SS}=\mathrm{ROP}-\lceil\overline{D_L}\rceil
\]

\(\alpha\)：A=0.99 / B=0.95 / C=0.90。

**不要**再实现 \(SS=k\sigma\sqrt{L}\)。

实现注意（按代码，不要“按论文理想化”）：

1. `StockThresholdService` **不把** Python MLE \(k\) 传给 MC；`LeadTimeDemandSimulator` 用 \((U-L)/(2\times 1.645)\) 反推 \(k_{\mathrm{MC}}\)。
2. `forecast.monte-carlo.seed=20260518` **尚未**传入 Python；`simulate_lead_time_demand` 未 seed。
3. 任务中心单备件任务不刷 `PRODUCTION` 模型注册。

## 禁止

- 恢复 RF/SBA 为默认全量重算
- 在 Java 里重新引入 XGBoost4J 本地拟合
- 把 `smart_replenishment.py` / 根目录旧脚本当生产入口
- 用旧文 `docs/archive/历史算法/` 当现行规格
