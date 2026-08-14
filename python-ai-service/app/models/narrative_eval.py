"""
真实滚动回测：库内消耗 + 多基线 + 分层 + 库存模拟。
指标直接由模型输出计算，不做论文量级事后校准。
"""
from __future__ import annotations

import math
from typing import Any

import numpy as np

from app.models.baselines import (
    METHOD_KEYS_15,
    METHOD_LABELS,
    LGBMQuantileForecaster,
    NGBoostLikeForecaster,
    adida,
    croston,
    deepar,
    deepar_interval,
    deepar_samples,
    empirical_crps,
    exp_smooth,
    mapa,
    nhits,
    rf_predict,
    sba,
    single_stage_xgb_predict,
    sma,
    tft,
    tft_interval,
    tft_samples,
    tsb,
)
from collections import defaultdict

from app.models.demand_forecast import HurdleGammaModel
from app.services.inventory_calc import simulate_lead_time_demand

DEFAULT_MAX_PARTS = 50
THESIS_MAX_MONTH = "2026-06"
THESIS_FOCUS = "C0070003"
# 与 01_输入/36件清单.csv、表 3-4～3-15 同一批；不含库内其余 14 件。
THESIS_36_CODES = (
    "C0100002",
    "C0020001",
    "C0070002",
    "C0040001",
    "C0070003",
    "C0020002",
    "C0020003",
    "C0050001",
    "C0030001",
    "C0020004",
    "C0020005",
    "C0100003",
    "C0090001",
    "C0080001",
    "C0040002",
    "C0060001",
    "C0050002",
    "C0050003",
    "C0020006",
    "C0070004",
    "C0020007",
    "C0100004",
    "C0040003",
    "C0070005",
    "C0020008",
    "C0030002",
    "C0030003",
    "C0030004",
    "C0020009",
    "C0020010",
    "C0020011",
    "C0070006",
    "C0070007",
    "C0040004",
    "C0040005",
    "C0090002",
)
THESIS_INV9 = {
    "AX": "C0100002",
    "AY": "C0020002",
    "AZ": "C0020004",
    "BX": "C0080001",
    "BY": "C0050002",
    "BZ": "C0020007",
    "CX": "C0030004",
    "CY": "C0070006",
    "CZ": "C0040005",
}


def _restrict_thesis_demand(
    demand: dict[str, dict[str, float]],
) -> dict[str, dict[str, float]]:
    """只保留论文 36 件、截止 2026-06，避免库内其余件挤掉 CZ 等席位。"""
    allow = set(THESIS_36_CODES)
    return {
        c: {m: float(v) for m, v in s.items() if m <= THESIS_MAX_MONTH}
        for c, s in demand.items()
        if c in allow
    }


def _months_sorted(dem: dict[str, dict[str, float]]) -> list[str]:
    s: set[str] = set()
    for m in dem.values():
        s.update(m.keys())
    return sorted(s)


def _select_parts(
    demand: dict[str, dict[str, float]],
    all_m: list[str],
    test_ms: list[str],
    max_parts: int,
) -> list[str]:
    """按训练窗非零月数与总量筛选，上限 max_parts，不强制 9×4。"""
    train_ms = [m for m in all_m if m < test_ms[0]]
    ranked: list[tuple[str, float, int]] = []
    for code, series in demand.items():
        if len(train_ms) < 12:
            continue
        nz = sum(1 for m in train_ms if series.get(m, 0.0) > 0)
        if nz < 3:
            continue
        total = sum(float(series.get(m, 0.0)) for m in train_ms)
        ranked.append((code, total, nz))
    ranked.sort(key=lambda x: (-x[1], -x[2], x[0]))
    return [c for c, _, _ in ranked[: max(1, max_parts)]]


def _compute_labels(
    demand: dict[str, dict[str, float]],
    annual: dict[str, float],
    train_for_label: list[str],
    _part_meta: dict[str, dict] | None = None,
) -> dict[str, tuple[str, str]]:
    """ABC/XYZ 一律由序列计算，忽略种子/论文标签。"""
    return {
        code: (_abc_from_annual(annual, code), _xyz(series, train_for_label))
        for code, series in demand.items()
    }


def _prev_month(ym: str, k: int = 1) -> str:
    y, m = int(ym[:4]), int(ym[5:7])
    m -= k
    while m <= 0:
        m += 12
        y -= 1
    return f"{y:04d}-{m:02d}"


def _xyz(series: dict[str, float], months: list[str]) -> str:
    pos = [series.get(m, 0.0) for m in months if series.get(m, 0.0) > 0]
    if len(pos) < 3:
        return "Z"
    mean = float(np.mean(pos))
    var = float(np.var(pos))
    cv2 = var / (mean * mean + 1e-8)
    if cv2 < 0.5:
        return "X"
    if cv2 < 1.0:
        return "Y"
    return "Z"


def _abc_from_annual(annual: dict[str, float], code: str) -> str:
    ranks = sorted(annual.items(), key=lambda x: -x[1])
    total = sum(v for _, v in ranks) or 1.0
    cum = 0.0
    for c, v in ranks:
        prev = cum / total
        cum += v
        if c == code:
            if prev < 0.70:
                return "A"
            if prev < 0.90:
                return "B"
            return "C"
    return "C"


def _features(series: dict[str, float], month: str, abc: str, xyz: str) -> list[float]:
    ac = {"A": 3, "B": 2, "C": 1}[abc]
    xc = {"X": 1, "Y": 2, "Z": 3}[xyz]
    lag1 = series.get(_prev_month(month, 1), 0.0)
    lag3 = [series.get(_prev_month(month, i), 0.0) for i in range(1, 4)]
    lag3_mean = float(np.mean(lag3))
    lag3_std = float(np.std(lag3, ddof=1)) if len(lag3) > 1 else 0.0
    zero6 = [series.get(_prev_month(month, i), 0.0) for i in range(1, 7)]
    zero_ratio = sum(1 for v in zero6 if v == 0) / 6.0
    pos = []
    for i in range(1, 80):
        v = series.get(_prev_month(month, i), 0.0)
        if v > 0:
            pos.append(v)
        if len(pos) >= 3:
            break
    pos_lag1 = pos[0] if pos else 0.0
    pos_lag3 = float(np.mean(pos[:3])) if pos else 0.0
    mon = float(int(month[5:7]))
    return [lag1, lag3_mean, lag3_std, zero_ratio, 0.0, 0.0, mon, float(ac), float(xc), pos_lag1, pos_lag3]


def _wmape(y, yh):
    sa = sum(abs(a) for a in y)
    if sa <= 1e-12:
        return 0.0
    return 100.0 * sum(abs(a - b) for a, b in zip(y, yh)) / sa


def _brier(y, p):
    if not y:
        return float("nan")
    return sum((pi - (1.0 if yi > 0 else 0.0)) ** 2 for yi, pi in zip(y, p)) / len(y)


def _cov90(y, L, U):
    n = c = 0
    w = 0.0
    for a, lo, hi in zip(y, L, U):
        if a <= 0:
            continue
        n += 1
        if lo <= a <= hi:
            c += 1
        w += max(0.0, hi - lo)
    return {
        "positivePoints": n,
        "covered": c,
        "missed": max(0, n - c),
        "coverageRate": round(100.0 * c / n, 2) if n else None,
        "avgWidth": round(w / n, 2) if n else None,
    }


def _wilson(hit: int, n: int, z: float = 1.96) -> tuple[float, float]:
    if n <= 0:
        return 0.0, 0.0
    ph = hit / n
    den = 1 + z * z / n
    center = (ph + z * z / (2 * n)) / den
    margin = z * math.sqrt((ph * (1 - ph) + z * z / (4 * n)) / n) / den
    return round(100 * max(0, center - margin), 2), round(100 * min(1, center + margin), 2)


def _hist_before(series: dict[str, float], month: str, all_months: list[str]) -> list[float]:
    return [series.get(m, 0.0) for m in all_months if m < month]


def _naive_mae(series: dict[str, float], train_ms: list[str]) -> float:
    vals = [series.get(m, 0.0) for m in train_ms]
    if len(vals) < 2:
        return 1.0
    errs = [abs(vals[i] - vals[i - 1]) for i in range(1, len(vals))]
    return max(0.5, float(np.mean(errs)))


# 可导出完整预测分布、参与真实 CRPS 对照的概率基线
PROB_DIST_METHODS = ("lgbm_q", "ngboost", "deepar", "tft")


def _crps_zig(y: float, p: float, mu: float, k: float) -> float:
    """零膨胀 Gamma CRPS：与基线共用 empirical_crps 公式。"""
    rng = np.random.default_rng(int(abs(p * 10000 + mu * 10 + k * 100)) % (2**31 - 1) + 1)
    n = 200
    k = max(0.5, min(float(k), 40.0))
    scale = max(float(mu) / k, 1e-3)
    p = float(np.clip(p, 0.0, 1.0))
    occ = rng.random(n) < p
    samples = np.zeros(n, dtype=float)
    n_pos = int(np.sum(occ))
    if n_pos > 0:
        samples[occ] = rng.gamma(k, scale, size=n_pos)
    return empirical_crps(samples, y)


def _strip_dist_fields(rows: list[dict]) -> list[dict]:
    """序列化前剥离 numpy 样本，仅保留区间摘要。"""
    clean = []
    for r in rows:
        rr = {k: v for k, v in r.items() if not str(k).startswith("_dist")}
        intervals = r.get("_dist_intervals")
        if intervals:
            rr["baselineIntervals"] = {
                k: {"L": round(float(v[0]), 2), "U": round(float(v[1]), 2)}
                for k, v in intervals.items()
            }
        clean.append(rr)
    return clean


def run_narrative_experiment(
    demand: dict[str, dict[str, float]],
    test_months: int = 6,
    focus_code: str | None = None,
    part_meta: dict[str, dict] | None = None,
    max_parts: int = DEFAULT_MAX_PARTS,
    protocol: str = "live",
) -> dict[str, Any]:
    thesis = protocol == "thesis"
    if thesis:
        demand = _restrict_thesis_demand(demand)
        test_months = 6
    else:
        demand = {c: {m: float(v) for m, v in s.items()} for c, s in demand.items()}
    all_m = _months_sorted(demand)
    if len(all_m) < test_months + 6:
        raise ValueError("历史月份不足：至少需要测试月数 + 6 个月训练窗")
    test_ms = all_m[-test_months:]
    train_for_label = [m for m in all_m if m < test_ms[0]] or all_m
    annual = {
        c: sum(float(s.get(m, 0.0)) for m in train_for_label) for c, s in demand.items()
    }
    if thesis:
        labels = {}
        for code, series in demand.items():
            meta = (part_meta or {}).get(code) or {}
            if meta.get("abc") and meta.get("xyz"):
                labels[code] = (str(meta["abc"]), str(meta["xyz"]))
            else:
                labels[code] = (_abc_from_annual(annual, code), _xyz(series, train_for_label))
        buckets: dict[str, list[str]] = defaultdict(list)
        for code in sorted(annual.keys(), key=lambda c: -annual[c]):
            buckets[labels[code][0] + labels[code][1]].append(code)
        selected: list[str] = []
        for combo in ["AX", "AY", "AZ", "BX", "BY", "BZ", "CX", "CY", "CZ"]:
            selected.extend(buckets.get(combo, [])[:4])
        parts = selected[:36]
        if not parts:
            raise ValueError("论文口径：无法凑齐分层样本")
    else:
        labels = _compute_labels(demand, annual, train_for_label, part_meta)
        parts = _select_parts(demand, all_m, test_ms, max_parts)
        if not parts:
            raise ValueError("没有足够历史消耗的备件可回测（训练窗需 ≥12 月且至少 3 个非零月）")

    methods = list(METHOD_KEYS_15)
    rows: list[dict] = []
    naive_by_part = {
        c: _naive_mae(demand[c], [m for m in all_m if m < test_ms[0]]) for c in parts
    }

    for t_month in test_ms:
        train_ms = [m for m in all_m if m < t_month]
        if len(train_ms) < 12:
            continue

        group_models: dict[str, HurdleGammaModel] = {}
        for g in ("X", "Y", "Z"):
            Xtr, ytr, gtr = [], [], []
            for code in parts:
                a, x = labels[code]
                if x != g:
                    continue
                series = demand[code]
                for m in train_ms[3:]:
                    Xtr.append(_features(series, m, a, x))
                    ytr.append(series.get(m, 0.0))
                    gtr.append(x)
            if len(Xtr) < 20:
                continue
            model = HurdleGammaModel()
            model.train(np.array(Xtr, float), np.array(ytr, float), gtr)
            group_models[g] = model

        Xall, yall, gall = [], [], []
        for code in parts:
            a, x = labels[code]
            series = demand[code]
            for m in train_ms[3:]:
                Xall.append(_features(series, m, a, x))
                yall.append(series.get(m, 0.0))
                gall.append(x)
        global_model = HurdleGammaModel()
        global_model.train(np.array(Xall, float), np.array(yall, float), gall)

        Xall_a = np.array(Xall, float)
        yall_a = np.array(yall, float)

        # 概率树基线：按月 fit 一次，全件共享 → 导出完整分位/参数分布
        lgbm_forecaster = LGBMQuantileForecaster().fit(yall_a, Xall_a)
        ngb_forecaster = NGBoostLikeForecaster().fit(yall_a, Xall_a)

        for code in parts:
            series = demand[code]
            a, x = labels[code]
            hist = _hist_before(series, t_month, all_m)
            y = float(series.get(t_month, 0.0))
            fx = _features(series, t_month, a, x)
            model = group_models.get(x, global_model)
            pr = model.predict(np.array([fx], float), [x])[0]
            p_t, mu_t = float(pr["p_t"]), float(pr["mu_t"])
            y_two = p_t * mu_t
            L, U = float(pr["lower_bound"]), float(pr["upper_bound"])
            k_val = float(pr.get("k", 1.0))

            lgbm_point = lgbm_forecaster.predict_point(fx)
            ngb_point = ngb_forecaster.predict_point(fx)
            deepar_point = deepar(hist)
            tft_point = tft(hist)

            # 分布样本（CRPS 用）；seed 与 (part, month) 绑定保证可复现
            seed_base = (
                sum((i + 1) * ord(c) for i, c in enumerate(f"{code}:{t_month}"))
                % (2**31 - 1)
            )
            lgbm_samp = lgbm_forecaster.predict_samples(fx, seed=seed_base + 1)
            ngb_samp = ngb_forecaster.predict_samples(fx, seed=seed_base + 2)
            deepar_samp = deepar_samples(hist, seed=seed_base + 3)
            tft_samp = tft_samples(hist, seed=seed_base + 4)
            lgbm_L, lgbm_U = lgbm_forecaster.predict_interval(fx)
            ngb_L, ngb_U = ngb_forecaster.predict_interval(fx)
            deepar_L, deepar_U = deepar_interval(hist)
            tft_L, tft_U = tft_interval(hist)

            preds = {
                "two_stage": y_two,
                "single_xgb": single_stage_xgb_predict(yall_a, Xall_a, fx),
                "rf": rf_predict(yall_a, Xall_a, fx),
                "sba": sba(hist),
                "croston": croston(hist),
                "es": exp_smooth(hist, 0.3),
                "sma3": sma(hist, 3),
                "tsb": tsb(hist),
                "lgbm_q": lgbm_point,
                "ngboost": ngb_point,
                "deepar": deepar_point,
                "tft": tft_point,
                "nhits": nhits(hist),
                "mapa": mapa(hist),
                "adida": adida(hist),
            }

            rows.append(
                {
                    "partCode": code,
                    "month": t_month,
                    "abc": a,
                    "xyz": x,
                    "actual": round(y, 2),
                    "preds": {k: round(float(v), 2) for k, v in preds.items()},
                    "occurrenceProb": round(p_t, 4),
                    "positiveQty": round(mu_t, 2),
                    "lowerBound": round(max(0.0, L), 2),
                    "upperBound": round(max(L + 0.1, U), 2),
                    "k": round(k_val, 4),
                    "naiveMae": round(naive_by_part[code], 4),
                    "mu": round(mu_t, 4),
                    # 概率基线分布（仅内存用，序列化时剥离）
                    "_dist_samples": {
                        "lgbm_q": lgbm_samp,
                        "ngboost": ngb_samp,
                        "deepar": deepar_samp,
                        "tft": tft_samp,
                    },
                    "_dist_intervals": {
                        "lgbm_q": (float(lgbm_L), float(lgbm_U)),
                        "ngboost": (float(ngb_L), float(ngb_U)),
                        "deepar": (float(deepar_L), float(deepar_U)),
                        "tft": (float(tft_L), float(tft_U)),
                    },
                }
            )

    actuals = [r["actual"] for r in rows]
    probs = [r["occurrenceProb"] for r in rows]
    lowers = [r["lowerBound"] for r in rows]
    uppers = [r["upperBound"] for r in rows]

    def summarize(method: str):
        yh = [r["preds"][method] for r in rows]
        return round(_wmape(actuals, yh), 2)

    overall_methods = {m: summarize(m) for m in methods}
    two = overall_methods["two_stage"]
    sma_w = overall_methods["sma3"]

    # MASE / CRPS
    # CRPS 口径：
    #   - two_stage：零膨胀 Gamma 混合（p, μ, k）蒙特卡洛样本
    #   - lgbm_q / ngboost / deepar / tft：各自导出的完整预测分布样本
    #   - 纯点预测：Dirac 退化分布，CRPS ≡ MAE（严格定义，非“代理糊弄”）
    mase = {}
    crps = {}
    crps_note = {
        "two_stage": "zero_inflated_gamma_mc",
        "lgbm_q": "lightgbm_multi_quantile_samples",
        "ngboost": "gaussian_residual_truncated_samples",
        "deepar": "zero_inflated_lognormal_samples",
        "tft": "gated_residual_normal_samples",
        "_point_methods": "dirac_equals_mae",
    }
    for m in methods:
        num = sum(abs(r["actual"] - r["preds"][m]) for r in rows)
        den = sum(r["naiveMae"] for r in rows)
        mase[m] = round(num / den, 4) if den > 0 else None
        if m == "two_stage":
            crps[m] = round(
                float(
                    np.mean(
                        [
                            _crps_zig(r["actual"], r["occurrenceProb"], r["mu"], r["k"])
                            for r in rows
                        ]
                    )
                ),
                2,
            )
        elif m in PROB_DIST_METHODS:
            vals = []
            for r in rows:
                samp = r.get("_dist_samples", {}).get(m)
                if samp is None:
                    vals.append(abs(r["actual"] - r["preds"][m]))
                else:
                    vals.append(empirical_crps(samp, r["actual"]))
            crps[m] = round(float(np.mean(vals)), 2)
        else:
            # Dirac 点预测：CRPS = MAE
            crps[m] = round(
                float(np.mean([abs(r["actual"] - r["preds"][m]) for r in rows])), 2
            )

    # 分层
    def slice_group(keys, label_fn):
        out = []
        for key in keys:
            sub = [r for r in rows if label_fn(r) == key]
            if not sub:
                out.append({"group": key, "n": 0})
                continue
            y = [r["actual"] for r in sub]
            item = {"group": key, "n": len(sub)}
            for m in ("two_stage", "single_xgb", "rf", "sma3", "sba", "croston", "lgbm_q"):
                item[f"wmape_{m}"] = round(_wmape(y, [r["preds"][m] for r in sub]), 2)
            p = [r["occurrenceProb"] for r in sub]
            L = [r["lowerBound"] for r in sub]
            U = [r["upperBound"] for r in sub]
            item["brier"] = round(_brier(y, p), 4)
            item.update({f"cov_{k}": v for k, v in _cov90(y, L, U).items()})
            out.append(item)
        return out

    by_abc = slice_group(["A", "B", "C"], lambda r: r["abc"])
    by_xyz = slice_group(["X", "Y", "Z"], lambda r: r["xyz"])

    by_month = []
    for tm in test_ms:
        sub = [r for r in rows if r["month"] == tm]
        if not sub:
            continue
        y = [r["actual"] for r in sub]
        item = {"month": tm, "n": len(sub)}
        for m in methods:
            item[f"wmape_{m}"] = round(_wmape(y, [r["preds"][m] for r in sub]), 2)
        by_month.append(item)

    if focus_code and focus_code in parts:
        focus = focus_code
    elif thesis and THESIS_FOCUS in parts:
        focus = THESIS_FOCUS
    else:
        focus = max(parts, key=lambda c: annual.get(c, 0.0)) if parts else None

    focus_series = [r for r in rows if r["partCode"] == focus]
    focus_wmape = {}
    if focus_series:
        y = [r["actual"] for r in focus_series]
        for m in methods:
            focus_wmape[m] = round(_wmape(y, [r["preds"][m] for r in focus_series]), 2)

    ablation = _ablation_full(rows, demand, all_m, test_ms)
    k_strategy = _k_strategy_compare(rows)
    if thesis:
        lead_map = {
            c: float((part_meta or {}).get(c, {}).get("leadTime") or 14)
            for c in parts
        }
        inventory = _inventory_rq(demand, labels, all_m, test_ms, lead_map, rows)
    else:
        inventory = _inventory_backtest(demand, parts, labels, all_m, test_ms, part_meta)
    coverage = _cov90(actuals, lowers, uppers)
    coverage_stats = _coverage_stats_table(rows)
    significance = _significance(rows, parts, methods)
    robustness = _robustness(rows, overall_methods, demand, parts, labels, all_m)
    normality = _normality(demand, inventory)
    lead_time = _lead_time(inventory, demand, all_m, test_ms, part_meta)
    csl = _csl_table(inventory)

    table_36 = []
    for m in sorted(methods, key=lambda k: overall_methods[k]):
        is_prob = m in ("two_stage", "lgbm_q", "ngboost", "tft", "deepar")
        intermittent = m in ("sba", "croston", "tsb", "adida", "mapa")
        row = {
            "method": METHOD_LABELS.get(m, m),
            "methodKey": m,
            "wmape": overall_methods[m],
            "mase": mase.get(m),
            "crps": crps.get(m),
            "category": _category(m),
            "probabilistic": is_prob,
            "crpsSource": (
                "zig_mc"
                if m == "two_stage"
                else (
                    crps_note.get(m, "full_distribution")
                    if m in PROB_DIST_METHODS
                    else "dirac_mae"
                )
            ),
        }
        if m == "two_stage":
            row["coverage"] = coverage.get("coverageRate")
            row["brier"] = round(_brier(actuals, probs), 4)
        elif m in PROB_DIST_METHODS:
            # 用该方法自己的 90% 区间与发生概率（样本中 >0 比例）算真实覆盖/Brier
            Ls, Us, p_hat = [], [], []
            complete = True
            for r in rows:
                interval = (r.get("_dist_intervals") or {}).get(m)
                samp = (r.get("_dist_samples") or {}).get(m)
                if not interval or interval[0] is None or interval[1] is None or samp is None:
                    complete = False
                    break
                Ls.append(float(interval[0]))
                Us.append(float(interval[1]))
                p_hat.append(float(np.mean(np.asarray(samp) > 0)))
            if complete:
                cov_m = _cov90(actuals, Ls, Us)
                row["coverage"] = cov_m.get("coverageRate")
                row["brier"] = round(_brier(actuals, p_hat), 4)
                row["avgWidth"] = cov_m.get("avgWidth")
            else:
                row["coverage"] = None
                row["brier"] = None
                row["avgWidth"] = None
        else:
            row["coverage"] = None
            row["brier"] = None
        if intermittent:
            row["coverage"] = "—"
            row["brier"] = "—"
        table_36.append(row)

    detail_clean = _strip_dist_fields(rows)
    focus_clean = _strip_dist_fields(focus_series)

    return {
        "available": True,
        "protocol": (
            "论文3.2.3/3.3.3：36件九组合、E01、2026-01～06、无事后校准"
            if thesis
            else "库内领用消耗滚动回测（多基线+分层+库存；无事后校准）"
        ),
        "testMonths": test_ms,
        "maxParts": max_parts,
        "partCount": len(parts),
        "sampleCount": len(rows),
        "parts": parts,
        "labels": {c: {"abc": labels[c][0], "xyz": labels[c][1]} for c in parts},
        "overallMethods": overall_methods,
        "methodLabels": METHOD_LABELS,
        "mase": mase,
        "crps": crps,
        "crpsNote": crps_note,
        "advantageOverSma": round(sma_w - two, 2),
        "overall": {
            "group": "整体",
            "n": len(rows),
            "wmapeTwoStage": two,
            "wmapeSma3": sma_w,
            "wmapeSingleXgb": overall_methods["single_xgb"],
            "wmapeLgbm": overall_methods.get("lgbm_q"),
            "brier": round(_brier(actuals, probs), 4),
            "crpsTwoStage": crps.get("two_stage"),
            "crpsLgbm": crps.get("lgbm_q"),
            "crpsNgboost": crps.get("ngboost"),
            "crpsDeepar": crps.get("deepar"),
            "crpsTft": crps.get("tft"),
            **{f"cov_{k}": v for k, v in coverage.items()},
        },
        "coverage": coverage,
        "coverageStats": coverage_stats,
        "byAbc": by_abc,
        "byXyz": by_xyz,
        "byMonth": by_month,
        "methodTable": table_36,
        "table36": table_36,
        "focusPartCode": focus,
        "focusSeries": focus_clean,
        "focusWmape": focus_wmape,
        "ablation": ablation,
        "kStrategy": k_strategy,
        "inventory": inventory,
        "significance": significance,
        "robustness": robustness,
        "normality": normality,
        "leadTime": lead_time,
        "csl": csl,
        "detail": detail_clean,
        "detailTotal": len(rows),
        "meta": {
            "postProcess": "none",
            "selection": (
                "thesis_36_fixed_codes" if thesis else "volume_ranked_max_parts"
            ),
            "labels": "assigned_thesis" if thesis else "computed_from_series",
            "trainCutoffPerFold": "rolling < test month",
            "crpsProtocol": (
                "统一 empirical CRPS；两阶段=ZIG；"
                "LightGBM=多分位逆变换采样；NGBoost=截断正态；"
                "DeepAR=零膨胀对数正态；TFT=门控残差正态；"
                "点预测=Dirac≡MAE"
            ),
        },
    }


def _category(m: str) -> str:
    if m == "two_stage":
        return "两阶段"
    if m in ("deepar", "tft", "nhits", "deepar_like", "tft_like"):
        return "深度方法"
    if m in ("tsb", "sba", "croston", "adida", "mapa"):
        return "间歇专用"
    if m in ("lgbm_q", "ngboost"):
        return "概率树"
    if m in ("single_xgb", "rf", "sma3", "es"):
        return "点预测基线"
    return "其他"


def _ablation_full(
    rows: list[dict],
    demand: dict[str, dict[str, float]],
    all_m: list[str],
    test_ms: list[str],
) -> list[dict]:
    """用同一批明细重算：单阶段 / p×历史均值 / 仅 μ / 完整 p×μ。"""
    y = [r["actual"] for r in rows]
    w_xgb = _wmape(y, [r["preds"]["single_xgb"] for r in rows])
    w_ts = _wmape(y, [r["preds"]["two_stage"] for r in rows])
    only2 = _wmape(y, [float(r.get("positiveQty") or r.get("mu") or 0.0) for r in rows])
    train_ms = [m for m in all_m if m < test_ms[0]]
    hist_mean: dict[str, float] = {}
    only1_preds: list[float] = []
    for r in rows:
        code = r["partCode"]
        if code not in hist_mean:
            series = demand.get(code, {})
            vals = [float(series.get(m, 0.0)) for m in train_ms]
            hist_mean[code] = float(np.mean(vals)) if vals else 0.0
        only1_preds.append(float(r.get("occurrenceProb") or 0.0) * hist_mean[code])
    only1 = _wmape(y, only1_preds)
    return [
        {"config": "单阶段近似(仅量)", "wmape": round(w_xgb, 2), "delta": None, "note": "主表 single_xgb"},
        {"config": "仅第一阶段(p×历史均值)", "wmape": round(only1, 2), "delta": round(w_xgb - only1, 2)},
        {"config": "仅第二阶段(不乘p)", "wmape": round(only2, 2), "delta": round(w_xgb - only2, 2)},
        {"config": "两阶段完整(p×μ)", "wmape": round(w_ts, 2), "delta": round(w_xgb - w_ts, 2), "note": "主表 two_stage"},
    ]


def _k_strategy_compare(rows: list[dict]) -> list[dict]:
    """只汇报本实验模型实际用到的组内 k 与覆盖，不编造对照方案。"""
    ks: dict[str, list[float]] = {"X": [], "Y": [], "Z": []}
    for r in rows:
        xyz = r.get("xyz")
        if xyz in ks and r.get("k") is not None:
            ks[xyz].append(float(r["k"]))
    cov = _cov90(
        [r["actual"] for r in rows],
        [r["lowerBound"] for r in rows],
        [r["upperBound"] for r in rows],
    )
    zrows = [r for r in rows if r.get("xyz") == "Z"]
    zcov = _cov90(
        [r["actual"] for r in zrows],
        [r["lowerBound"] for r in zrows],
        [r["upperBound"] for r in zrows],
    ) if zrows else {"coverageRate": None}
    br = round(_brier([r["actual"] for r in rows], [r["occurrenceProb"] for r in rows]), 4)
    return [
        {
            "scheme": "本实验组内共享 k（模型输出）",
            "k": {g: (round(float(np.mean(v)), 2) if v else None) for g, v in ks.items()},
            "coverage": cov.get("coverageRate"),
            "zCoverage": zcov.get("coverageRate"),
            "brier": br,
            "note": "未事后改写区间或 k",
        }
    ]


def _inventory_rq(demand, labels, all_m, test_ms, lead_time_map, rows, W: int = 22):
    """论文库存：九指定代表件，(R,Q) 连续盘点，订单 L 天后到货。"""
    from scipy import stats as sp_stats

    train_ms = [m for m in all_m if m < test_ms[0]]
    row_by = {(r["partCode"], r["month"]): r for r in rows}
    alpha_by = {"A": 0.99, "B": 0.95, "C": 0.90}

    def simulate_continuous(series, rop_by_month: dict[str, float], Q: float, L_days: float):
        L_ticks = max(1, int(round(L_days)))
        daily, rop_day, month_of = [], [], []
        for tm in test_ms:
            qty = series.get(tm, 0.0) / W
            r = float(rop_by_month[tm])
            daily.extend([qty] * W)
            rop_day.extend([r] * W)
            month_of.extend([tm] * W)
        on_hand = float(rop_by_month[test_ms[0]]) + Q
        pending: list[tuple[int, float]] = []
        so_q = dem = filled = inv_sum = 0.0
        so_months: set[str] = set()
        for day, d in enumerate(daily):
            arrived = [q for arr, q in pending if arr == day]
            on_hand += sum(arrived)
            if arrived:
                pending = [(arr, q) for arr, q in pending if arr != day]
            dem += d
            if d > on_hand + 1e-9:
                so_q += d - on_hand
                filled += on_hand
                on_hand = 0.0
                so_months.add(month_of[day])
            else:
                filled += d
                on_hand -= d
            inv_sum += on_hand
            position = on_hand + sum(q for _, q in pending)
            if position <= rop_day[day] + 1e-9:
                pending.append((day + L_ticks, Q))
        fr = 100.0 * filled / dem if dem > 0 else 100.0
        return {
            "stockoutMonths": len(so_months),
            "stockoutQty": round(so_q, 2),
            "fillRate": round(fr, 2),
            "avgInv": round(inv_sum / max(len(daily), 1), 2),
            "demand": round(dem, 2),
            "filled": round(filled, 2),
            "n": len(test_ms),
        }

    results = []
    for combo, code in THESIS_INV9.items():
        if code not in demand or code not in labels:
            continue
        a, _x = labels[code]
        series = demand[code]
        hist = [series.get(m, 0.0) for m in train_ms]
        mean_h = float(np.mean(hist)) if hist else 0.0
        std_h = float(np.std(hist)) if len(hist) > 1 else 0.0
        alpha = alpha_by[a]
        z_a = float(sp_stats.norm.ppf(alpha))
        L_days = float(lead_time_map.get(code, 14.0))
        Q = max(1.0, mean_h)
        rop_exp = max(1.0, mean_h)
        rop_norm = max(1.0, mean_h + z_a * std_h)
        ours = {}
        for tm in test_ms:
            r = row_by.get((code, tm))
            if r is None:
                ours[tm] = rop_norm
                continue
            k_val = float(np.clip(float(r.get("k") or 1.0), 0.8, 12.0))
            mc = simulate_lead_time_demand(
                p_t=float(r["occurrenceProb"]),
                mu_t=max(float(r.get("mu") or r.get("positiveQty") or 1e-5), 1e-5),
                k=k_val,
                L=L_days,
                alpha=alpha,
            )
            ours[tm] = max(1.0, float(mc["rop"]))
        se = simulate_continuous(series, {tm: rop_exp for tm in test_ms}, Q, L_days)
        so = simulate_continuous(series, ours, Q, L_days)
        sn = simulate_continuous(series, {tm: rop_norm for tm in test_ms}, Q, L_days)
        results.append(
            {
                "combo": combo,
                "partCode": code,
                "leadTimeDays": L_days,
                "orderQty": round(Q, 2),
                "methods": [
                    {"method": "经验法", "rop": round(rop_exp, 1), **se},
                    {
                        "method": "本文方法",
                        "rop": round(float(np.mean(list(ours.values()))), 1),
                        "ropByMonth": ours,
                        **so,
                    },
                    {"method": "正态解析法", "rop": round(rop_norm, 1), **sn},
                ],
            }
        )

    def sum_pack(name: str) -> dict:
        sub = [m for r in results for m in r["methods"] if m["method"] == name]
        if not sub:
            return {"method": name, "stockoutMonths": 0, "stockoutQty": 0.0, "fillRate": None, "avgInv": None}
        return {
            "method": name,
            "stockoutMonths": sum(m["stockoutMonths"] for m in sub),
            "stockoutQty": round(sum(m["stockoutQty"] for m in sub), 2),
            "fillRate": round(float(np.mean([m["fillRate"] for m in sub])), 2),
            "avgInv": round(float(np.mean([m["avgInv"] for m in sub])), 2),
        }

    return {
        "note": "九组合代表件；(R,Q) 连续盘点，订单 L 天后到货。无事后改写。",
        "byCombo": results,
        "summary": [sum_pack("经验法"), sum_pack("本文方法"), sum_pack("正态解析法")],
    }


def _simulate_base_stock(series: dict[str, float], test_ms: list[str], rop: int) -> dict[str, float]:
    """月初若库存 < ROP 则补到 ROP，再扣当月需求。结果不事后改写。"""
    inv = float(rop)
    so_m = so_q = dem = filled = inv_sum = 0.0
    n = 0
    for tm in test_ms:
        if inv < rop:
            inv = float(rop)
        y = float(series.get(tm, 0.0))
        dem += y
        n += 1
        if y > inv + 1e-9:
            so_m += 1
            so_q += y - inv
            filled += inv
            inv = 0.0
        else:
            filled += y
            inv -= y
        inv_sum += inv
    fr = 100.0 * filled / dem if dem > 0 else 100.0
    return {
        "stockoutMonths": int(so_m),
        "stockoutQty": round(so_q, 2),
        "fillRate": round(fr, 2),
        "avgInv": round(inv_sum / max(n, 1), 2),
        "demand": dem,
        "filled": filled,
        "n": n,
    }


def _inventory_backtest(demand, parts, labels, all_m, test_ms, part_meta):
    """按训练窗统计量设 ROP，开环模拟测试窗，不保论文双占优。"""
    train_ms = [m for m in all_m if m < test_ms[0]]
    picked: dict[str, str] = {}
    for code in parts:
        combo = labels[code][0] + labels[code][1]
        if combo not in picked:
            picked[combo] = code

    results = []
    z_alpha = {"A": 2.33, "B": 1.65, "C": 1.28}
    for combo, code in sorted(picked.items()):
        a, _x = labels[code]
        series = demand[code]
        hist = [float(series.get(m, 0.0)) for m in train_ms]
        mean = float(np.mean(hist)) if hist else 1.0
        std = float(np.std(hist, ddof=1)) if len(hist) > 1 else max(1.0, mean * 0.3)
        pos = [v for v in hist if v > 0]
        p_hat = len(pos) / max(len(hist), 1)
        mu_pos = float(np.mean(pos)) if pos else mean
        lead = 1.0
        if part_meta and code in part_meta and part_meta[code].get("leadTime"):
            lead = max(1.0 / 30.0, float(part_meta[code]["leadTime"]) / 30.0)
        z = z_alpha.get(a, 1.65)
        rop_exp = max(1, int(round(mean * lead)))
        rop_ours = max(1, int(round(p_hat * mu_pos * lead + z * std * math.sqrt(lead))))
        rop_norm = max(1, int(round(mean * lead + z * std * math.sqrt(lead))))
        se = _simulate_base_stock(series, test_ms, rop_exp)
        so = _simulate_base_stock(series, test_ms, rop_ours)
        sn = _simulate_base_stock(series, test_ms, rop_norm)

        results.append(
            {
                "combo": combo,
                "partCode": code,
                "methods": [
                    {"method": "经验法", "rop": rop_exp, **se},
                    {"method": "本文方法", "rop": rop_ours, **so},
                    {"method": "正态解析法", "rop": rop_norm, **sn},
                ],
            }
        )

    def sum_pack(name: str) -> dict[str, Any]:
        sub = [m for r in results for m in r["methods"] if m["method"] == name]
        if not sub:
            return {"method": name, "stockoutMonths": 0, "stockoutQty": 0.0, "fillRate": None, "avgInv": None}
        return {
            "method": name,
            "stockoutMonths": sum(m["stockoutMonths"] for m in sub),
            "stockoutQty": round(sum(m["stockoutQty"] for m in sub), 2),
            "fillRate": round(float(np.mean([m["fillRate"] for m in sub])), 2),
            "avgInv": round(float(np.mean([m["avgInv"] for m in sub])), 2),
        }

    summary = [sum_pack("经验法"), sum_pack("本文方法"), sum_pack("正态解析法")]
    extra_hold = 0.0
    cut = 0.0
    for r in results:
        e = next(m for m in r["methods"] if m["method"] == "经验法")
        o = next(m for m in r["methods"] if m["method"] == "本文方法")
        extra_hold += max(0.0, o["avgInv"] - e["avgInv"]) * len(test_ms)
        cut += max(0.0, e["stockoutQty"] - o["stockoutQty"])
    be = extra_hold / cut if cut > 0 else None

    return {
        "note": "各 ABC×XYZ 组合取一件代表件；ROP 由训练窗估计；测试窗开环模拟，未改写满足率。",
        "byCombo": results,
        "summary": summary,
        "breakEven": {
            "extraHolding": round(extra_hold, 2),
            "stockoutCut": round(cut, 2),
            "months": round(be, 2) if be is not None else None,
        },
    }


def _coverage_stats_table(rows):
    def one(sub, label):
        y = [r["actual"] for r in sub]
        L = [r["lowerBound"] for r in sub]
        U = [r["upperBound"] for r in sub]
        c = _cov90(y, L, U)
        hit, n = c["covered"] or 0, c["positivePoints"] or 0
        lo, hi = _wilson(hit, n)
        return {
            "group": label,
            **c,
            "wilsonLow": lo,
            "wilsonHigh": hi,
        }

    out = [one(rows, "整体")]
    for g in ("X", "Y", "Z"):
        out.append(one([r for r in rows if r["xyz"] == g], g))
    zpos = [r for r in rows if r["xyz"] == "Z" and r["actual"] > 0]
    rates = []
    if zpos:
        rng = np.random.default_rng(0)
        n_z = len(zpos)
        for _ in range(1000):
            idx = rng.integers(0, n_z, size=n_z)
            sample = [zpos[i] for i in idx]
            h = sum(1 for r in sample if r["lowerBound"] <= r["actual"] <= r["upperBound"])
            rates.append(100.0 * h / len(sample))
    if rates:
        out.append(
            {
                "group": "Z_bootstrap",
                "mean": round(float(np.mean(rates)), 2),
                "sd": round(float(np.std(rates)), 2),
                "p2_5": round(float(np.percentile(rates, 2.5)), 2),
                "p97_5": round(float(np.percentile(rates, 97.5)), 2),
                "n": 1000,
            }
        )
    return out


def _significance(rows, parts, methods):
    part_w = {}
    for p in parts:
        sub = [r for r in rows if r["partCode"] == p]
        ya = [r["actual"] for r in sub]
        if not sub:
            continue
        part_w[p] = {m: _wmape(ya, [r["preds"][m] for r in sub]) for m in methods}

    usable = [p for p in parts if p in part_w]
    raw = []
    for m in methods:
        if m == "two_stage":
            continue
        gaps = [part_w[p][m] - part_w[p]["two_stage"] for p in usable]
        if not gaps:
            continue
        better = sum(1 for g in gaps if g > 0)
        pval = None
        if len(usable) >= 6 and any(abs(g) > 1e-12 for g in gaps):
            try:
                from scipy.stats import wilcoxon

                a = [part_w[p]["two_stage"] for p in usable]
                b = [part_w[p][m] for p in usable]
                pval = float(wilcoxon(a, b, alternative="two-sided").pvalue)
            except (ValueError, ImportError):
                pval = None
        n_pairs = len(usable)
        r_eff = (2 * better / n_pairs - 1.0) if n_pairs else 0.0
        raw.append((m, pval, r_eff, better, round(float(np.mean(gaps)), 2)))

    with_p = sorted([(m, p) for m, p, *_ in raw if p is not None], key=lambda x: x[1])
    holm: dict[str, float | None] = {m: None for m, *_ in raw}
    prev = 0.0
    n_p = len(with_p)
    for i, (m, p) in enumerate(with_p):
        h = min(1.0, p * (n_p - i))
        h = max(prev, h)
        holm[m] = h
        prev = h

    out = []
    for m, pval, r_eff, better, mean_gap in raw:
        holm_p = holm.get(m)
        out.append(
            {
                "vs": METHOD_LABELS.get(m, m),
                "methodKey": m,
                "parts": len(usable),
                "twoStageBetterCount": better,
                "meanGapWmape": mean_gap,
                "wilcoxonP": round(pval, 4) if pval is not None else None,
                "holmP": round(holm_p, 4) if holm_p is not None else None,
                "effectR": round(r_eff, 2),
                "significant": bool(holm_p is not None and holm_p < 0.05),
            }
        )
    return out


def _robustness(rows, overall, demand, parts, labels, all_m):
    w_ts, w_x = overall["two_stage"], overall["single_xgb"]
    # 零占比分组（全历史）
    low, mid, high = [], [], []
    for p in parts:
        series = [demand[p].get(m, 0.0) for m in all_m]
        zr = 100.0 * sum(1 for v in series if v <= 0) / max(1, len(series))
        if zr < 20:
            low.append(p)
        elif zr <= 50:
            mid.append(p)
        else:
            high.append(p)

    def sub_w(plist):
        sub = [r for r in rows if r["partCode"] in plist]
        if not sub:
            return None, None
        y = [r["actual"] for r in sub]
        return (
            round(_wmape(y, [r["preds"]["two_stage"] for r in sub]), 2),
            round(_wmape(y, [r["preds"]["single_xgb"] for r in sub]), 2),
        )

    t_l, x_l = sub_w(low)
    t_m, x_m = sub_w(mid)
    t_h, x_h = sub_w(high)
    out = [{"scene": "基线（无扰动）", "twoStage": w_ts, "singleXgb": w_x, "note": "可由明细复算"}]
    for scene, plist, tw, xw in (
        (f"零占比<20%（n={len(low)}）", low, t_l, x_l),
        (f"零占比20%~50%（n={len(mid)}）", mid, t_m, x_m),
        (f"零占比>50%（n={len(high)}）", high, t_h, x_h),
    ):
        if not plist or tw is None:
            out.append({"scene": scene, "twoStage": None, "singleXgb": None, "note": "本批无该子集"})
        else:
            out.append({"scene": scene, "twoStage": tw, "singleXgb": xw, "note": "子集可复算"})
    return out


def _normality(demand, inventory):
    from scipy import stats as sp_stats

    out = []
    for item in inventory.get("byCombo", []):
        code = item["partCode"]
        series = sorted(demand.get(code, {}).items())
        vals = np.array([v for _, v in series if v is not None], dtype=float)
        if len(vals) < 8:
            continue
        # 对正需求更有意义；全序列通常拒绝正态
        sw = ks = ad = None
        try:
            sw = float(sp_stats.shapiro(vals[: min(50, len(vals))]).pvalue)
        except (ValueError, ImportError):
            sw = None
        try:
            ks = float(sp_stats.kstest(vals, "norm", args=(vals.mean(), vals.std() or 1)).pvalue)
        except (ValueError, ImportError):
            ks = None
        try:
            ad = float(sp_stats.anderson(vals, dist="norm").statistic)
        except (ValueError, ImportError):
            ad = None
        out.append(
            {
                "combo": item["combo"],
                "partCode": code,
                "shapiroP": round(sw, 4) if sw is not None else None,
                "ksP": round(ks, 4) if ks is not None else None,
                "adStat": round(ad, 3) if ad is not None else None,
                "rejectNormal": bool(sw < 0.05) if sw is not None else None,
            }
        )
    return out


def _lead_time(inventory, demand, all_m, test_ms, part_meta=None):
    """用训练窗均值/标准差与同一提前期估计分位数，与库存回测 ROP 对照。"""
    out = []
    for item in inventory.get("byCombo", []):
        ours = next((m for m in item["methods"] if m["method"] == "本文方法"), None)
        if not ours:
            continue
        code = item["partCode"]
        rop = int(ours["rop"])
        hist = [float(demand.get(code, {}).get(m, 0.0)) for m in all_m if m < test_ms[0]]
        mean_m = float(np.mean(hist)) if hist else 0.0
        std_m = float(np.std(hist, ddof=1)) if len(hist) > 1 else 0.0
        lead = 1.0
        if part_meta and code in part_meta and part_meta[code].get("leadTime"):
            lead = max(1.0 / 30.0, float(part_meta[code]["leadTime"]) / 30.0)
        e_dl = mean_m * lead
        sigma = std_m * math.sqrt(lead)
        q95 = e_dl + 1.65 * sigma
        q99 = e_dl + 2.33 * sigma
        out.append(
            {
                "partCode": code,
                "combo": item["combo"],
                "E_DL": round(e_dl, 2),
                "sigma_L": round(sigma, 2),
                "Q95": round(q95, 2),
                "Q99": round(q99, 2),
                "ROP": rop,
                "safetyStock": round(max(0.0, rop - e_dl), 2),
                "note": f"训练窗月需求；提前期 {lead:.2f} 月",
                "meanMonthly": round(mean_m, 2),
            }
        )
    return out


def _csl_table(inventory):
    out = []
    target = {"A": 0.99, "B": 0.95, "C": 0.90}
    for item in inventory.get("byCombo", []):
        ours = next((m for m in item["methods"] if m["method"] == "本文方法"), None)
        if not ours:
            continue
        periods = int(ours.get("n") or 0)
        if periods <= 0:
            continue
        no_so = max(0, periods - int(ours["stockoutMonths"]))
        abc = item["combo"][0]
        out.append(
            {
                "combo": item["combo"],
                "partCode": item["partCode"],
                "targetCsl": target.get(abc, 0.95),
                "periods": periods,
                "noStockoutPeriods": no_so,
                "realizedCsl": round(no_so / periods, 4),
                "fillRate": ours["fillRate"],
            }
        )
    return out
