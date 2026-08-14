"""真实实验契约：指标必须来自模型输出，不得事后改写到论文带。"""
from __future__ import annotations

import inspect
import math

import numpy as np
import pytest

from app.models import narrative_eval as ne


def _series(n: int, start: str = "2023-01", base: float = 4.0, period: int = 3) -> dict[str, float]:
    y, m = int(start[:4]), int(start[5:7])
    out: dict[str, float] = {}
    for i in range(n):
        key = f"{y:04d}-{m:02d}"
        out[key] = float(base + (i % period) * 2) if i % period else 0.0
        m += 1
        if m > 12:
            m = 1
            y += 1
    return out


def test_no_post_process_calibrators() -> None:
    src = inspect.getsource(ne)
    assert "_calibrate_p" not in src
    assert "_calibrate_intervals" not in src
    assert "_nudge_method_ranking" not in src
    assert "paperTargets" not in src


def test_months_sorted_keeps_all_observed_months() -> None:
    demand = {"P1": {"2026-06": 1.0, "2026-08": 2.0, "2024-01": 3.0}}
    months = ne._months_sorted(demand)
    assert months == ["2024-01", "2026-06", "2026-08"]


def test_thesis_demand_drops_extra_parts_that_would_steal_cz() -> None:
    months = [f"2025-{m:02d}" for m in range(1, 13)] + [f"2026-{m:02d}" for m in range(1, 7)]
    demand = {c: {m: 1.0 for m in months} for c in ne.THESIS_36_CODES}
    demand["C0080004"] = {m: 999.0 for m in months}
    demand["C0080004"]["2026-07"] = 1.0
    restricted = ne._restrict_thesis_demand(demand)
    assert "C0080004" not in restricted
    assert set(restricted) == set(ne.THESIS_36_CODES)
    assert all("2026-07" not in series for series in restricted.values())
    assert len(ne.THESIS_36_CODES) == 36


def test_select_parts_honors_max_parts_and_skips_too_sparse() -> None:
    all_m = [f"2023-{m:02d}" for m in range(1, 13)] + [f"2024-{m:02d}" for m in range(1, 7)]
    test_ms = all_m[-3:]
    demand = {
        "RICH": {m: 10.0 for m in all_m},
        "MID": {m: 5.0 for m in all_m},
        "SPARSE": {all_m[0]: 1.0},
        "EMPTY": {},
    }
    picked = ne._select_parts(demand, all_m, test_ms, max_parts=2)
    assert picked == ["RICH", "MID"]
    # ranking must not use the test window
    demand["MID"]["2024-06"] = 1000.0
    assert ne._select_parts(demand, all_m, test_ms, max_parts=2) == ["RICH", "MID"]


def test_labels_come_from_series_not_part_meta() -> None:
    months = [f"2024-{m:02d}" for m in range(1, 13)]
    demand = {
        "A1": {m: 20.0 for m in months},
        "C1": {months[0]: 1.0, months[6]: 1.0},
    }
    annual = {c: sum(s.values()) for c, s in demand.items()}
    meta = {"A1": {"abc": "C", "xyz": "Z"}, "C1": {"abc": "A", "xyz": "X"}}
    labels = ne._compute_labels(demand, annual, months, meta)
    assert labels["A1"][0] == "A"
    assert labels["A1"][1] == "X"
    assert labels["C1"][0] == "C"


def test_inventory_fill_rate_matches_open_loop_simulation() -> None:
    all_m = [f"2023-{m:02d}" for m in range(1, 13)] + ["2024-01", "2024-02", "2024-03"]
    test_ms = ["2024-01", "2024-02", "2024-03"]
    demand = {"P1": {m: 4.0 for m in all_m}}
    demand["P1"]["2024-02"] = 20.0
    labels = {"P1": ("B", "Y")}
    inv = ne._inventory_backtest(demand, ["P1"], labels, all_m, test_ms, None)
    ours = next(m for m in inv["byCombo"][0]["methods"] if m["method"] == "本文方法")
    rop = int(ours["rop"])
    inv_lvl = float(rop)
    dem = filled = so_q = so_m = 0.0
    for tm in test_ms:
        if inv_lvl < rop:
            inv_lvl = float(rop)
        y = demand["P1"][tm]
        dem += y
        if y > inv_lvl:
            so_m += 1
            so_q += y - inv_lvl
            filled += inv_lvl
            inv_lvl = 0.0
        else:
            filled += y
            inv_lvl -= y
    expected = 100.0 * filled / dem
    assert ours["fillRate"] == pytest.approx(round(expected, 2))
    assert ours["stockoutMonths"] == int(so_m)
    assert ours["stockoutQty"] == pytest.approx(round(so_q, 2))


def test_ablation_uses_row_fields() -> None:
    rows = [
        {
            "partCode": "P1",
            "actual": 10.0,
            "preds": {"two_stage": 8.0, "single_xgb": 12.0},
            "occurrenceProb": 0.5,
            "positiveQty": 16.0,
            "mu": 16.0,
        },
        {
            "partCode": "P1",
            "actual": 0.0,
            "preds": {"two_stage": 2.0, "single_xgb": 3.0},
            "occurrenceProb": 0.2,
            "positiveQty": 10.0,
            "mu": 10.0,
        },
    ]
    demand = {"P1": {"2023-01": 10.0, "2023-02": 10.0, "2024-01": 10.0, "2024-02": 0.0}}
    table = ne._ablation_full(rows, demand, ["2023-01", "2023-02", "2024-01", "2024-02"], ["2024-01", "2024-02"])
    by_cfg = {r["config"]: r for r in table}
    assert by_cfg["两阶段完整(p×μ)"]["wmape"] == pytest.approx(ne._wmape([10, 0], [8, 2]), abs=1e-6)
    assert by_cfg["仅第二阶段(不乘p)"]["wmape"] == pytest.approx(ne._wmape([10, 0], [16, 10]), abs=1e-6)
    assert by_cfg["仅第一阶段(p×历史均值)"]["wmape"] == pytest.approx(
        ne._wmape([10, 0], [0.5 * 10.0, 0.2 * 10.0]), abs=1e-6
    )


def test_significance_can_be_not_significant() -> None:
    rows = []
    parts = [f"P{i}" for i in range(6)]
    # 两方法互有胜负，差距不足以显著
    deltas = [0.2, -0.3, 0.1, -0.15, 0.05, -0.08]
    for i, p in enumerate(parts):
        two = 10.0 + deltas[i]
        sma = 10.0 - deltas[i]
        rows.append(
            {
                "partCode": p,
                "month": "2024-01",
                "actual": 10.0,
                "preds": {"two_stage": two, "sma3": sma},
            }
        )
    out = ne._significance(rows, parts, ["two_stage", "sma3"])
    assert len(out) == 1
    assert out[0]["significant"] is False
    assert out[0]["wilcoxonP"] is None or out[0]["wilcoxonP"] > 0.05


def test_normality_reports_actual_pvalue() -> None:
    vals = np.random.default_rng(0).normal(10.0, 1.0, size=40)
    demand = {"P1": {f"m{i:02d}": float(v) for i, v in enumerate(vals)}}
    inventory = {"byCombo": [{"combo": "AX", "partCode": "P1"}]}
    out = ne._normality(demand, inventory)
    assert out
    assert out[0]["rejectNormal"] == (out[0]["shapiroP"] < 0.05)
    assert 0.0 <= out[0]["shapiroP"] <= 1.0
