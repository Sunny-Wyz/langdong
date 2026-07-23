#!/usr/bin/env python3
"""
离线滚动回测：直接读 spare_db 月度消耗 + 本地 HurdleGammaModel，
输出 wMAPE/MASE/Brier/覆盖率等评估指标（不依赖 Java）。
"""
from __future__ import annotations

import json
import math
import sys
from collections import defaultdict
from datetime import date
from pathlib import Path

import numpy as np
import pymysql

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "python-ai-service"))

from app.models.demand_forecast import HurdleGammaModel  # noqa: E402


def month_range(y0, m0, y1, m1):
    out = []
    y, m = y0, m0
    while (y, m) <= (y1, m1):
        out.append(f"{y:04d}-{m:02d}")
        m += 1
        if m > 12:
            m, y = 1, y + 1
    return out


def load_demand(conn):
    sql = """
    SELECT sp.code AS part_code,
           DATE_FORMAT(r.approve_time, '%Y-%m') AS month,
           CAST(COALESCE(SUM(ri.out_qty), 0) AS SIGNED) AS qty
    FROM biz_requisition_item ri
    INNER JOIN biz_requisition r ON ri.req_id = r.id
    INNER JOIN spare_part sp ON ri.spare_part_id = sp.id
    WHERE r.req_status IN ('OUTBOUND', 'INSTALLED')
      AND ri.out_qty IS NOT NULL AND ri.out_qty > 0
    GROUP BY sp.code, DATE_FORMAT(r.approve_time, '%Y-%m')
    """
    dem = defaultdict(dict)
    with conn.cursor() as cur:
        cur.execute(sql)
        for code, month, qty in cur.fetchall():
            dem[code][month] = float(qty)
    return dem


def xyz_of(series: dict, months: list[str]) -> str:
    vals = [series.get(m, 0.0) for m in months]
    pos = [v for v in vals if v > 0]
    if len(pos) < 3:
        return "Z"
    mean = np.mean(pos)
    var = np.var(pos)
    cv2 = var / (mean * mean + 1e-8)
    if cv2 < 0.5:
        return "X"
    if cv2 < 1.0:
        return "Y"
    return "Z"


def abc_of(annual: float, ranks: list[tuple[str, float]], code: str) -> str:
    # 帕累托 70/90
    total = sum(v for _, v in ranks) or 1.0
    cum = 0.0
    for c, v in ranks:
        cum += v
        if c == code:
            share = cum / total
            if share <= 0.70:
                return "A"
            if share <= 0.90:
                return "B"
            return "C"
    return "C"


def build_features(series: dict, month: str, abc_code: float, xyz_code: float):
    """对齐论文表 3-2 / Java FeatureBuilder 11 维（简化：无设备特征填 0）。"""
    y, m = int(month[:4]), int(month[5:7])
    # prev months
    def prev(k):
        mm = m - k
        yy = y
        while mm <= 0:
            mm += 12
            yy -= 1
        return f"{yy:04d}-{mm:02d}"

    lag1 = series.get(prev(1), 0.0)
    lag3 = [series.get(prev(i), 0.0) for i in range(1, 4)]
    lag3_mean = float(np.mean(lag3))
    lag3_std = float(np.std(lag3, ddof=1)) if len(lag3) > 1 else 0.0
    zero6 = [series.get(prev(i), 0.0) for i in range(1, 7)]
    zero_ratio_6 = sum(1 for v in zero6 if v == 0) / 6.0
    # pos lags
    pos = []
    for i in range(1, 80):
        v = series.get(prev(i), 0.0)
        if v > 0:
            pos.append(v)
        if len(pos) >= 3:
            break
    pos_lag1 = pos[0] if pos else 0.0
    pos_lag3 = float(np.mean(pos[:3])) if pos else 0.0
    return [
        lag1, lag3_mean, lag3_std, zero_ratio_6,
        0.0, 0.0, float(m), abc_code, xyz_code,
        pos_lag1, pos_lag3,
    ]


def wmape(y, yh):
    sa = sum(abs(a) for a in y)
    if sa <= 1e-12:
        return 0.0
    return 100.0 * sum(abs(a - b) for a, b in zip(y, yh)) / sa


def brier(y, p):
    if not y:
        return float("nan")
    s = 0.0
    for a, pr in zip(y, p):
        ind = 1.0 if a > 0 else 0.0
        s += (pr - ind) ** 2
    return s / len(y)


def coverage90(y, L, U):
    n = c = 0
    for a, lo, hi in zip(y, L, U):
        if a <= 0:
            continue
        n += 1
        if lo <= a <= hi:
            c += 1
    return (100.0 * c / n if n else None), n, c


def main():
    conn = pymysql.connect(
        host="127.0.0.1", user="admin", password="123456",
        database="spare_db", charset="utf8mb4",
    )
    dem = load_demand(conn)
    conn.close()
    if not dem:
        print("无消耗数据")
        sys.exit(1)

    all_months = sorted({m for s in dem.values() for m in s})
    max_m = all_months[-1]
    # last 6 months
    ym = [int(x) for x in max_m.split("-")]
    test_months = []
    y, m = ym[0], ym[1]
    for _ in range(6):
        test_months.append(f"{y:04d}-{m:02d}")
        m -= 1
        if m <= 0:
            m, y = 12, y - 1
    test_months = list(reversed(test_months))

    # annual cost proxy = sum last 12 months demand * 1
    annual = {c: sum(s.values()) for c, s in dem.items()}
    ranks = sorted(annual.items(), key=lambda x: -x[1])
    # pick top 36 by volume for stability (paper-like sample size)
    parts = [c for c, _ in ranks[:36]]

    print("test_months", test_months)
    print("parts", len(parts), "history_max", max_m)

    all_y, all_yh, all_sma, all_p, all_L, all_U = [], [], [], [], [], []
    detail = []

    # 论文：逐备件独立训练（共享超参，形状 k 按 XYZ 在组内估计——此处单件用自身 + 同组样本）
    for t_month in test_months:
        train_months = [m for m in all_months if m < t_month]
        if len(train_months) < 12:
            continue

        meta = {}
        for code in parts:
            series = dem[code]
            xyz = xyz_of(series, train_months[-24:])
            abc = abc_of(annual[code], ranks, code)
            meta[code] = (abc, xyz)

        # 组内共享 k：先为每个 XYZ 组收集正残差用的训练（同组所有件）
        group_models = {}
        for g in ("X", "Y", "Z"):
            Xtr, ytr, gtr = [], [], []
            for code in parts:
                if meta[code][1] != g:
                    continue
                series = dem[code]
                abc, xyz = meta[code]
                ac, xc = {"A": 3, "B": 2, "C": 1}[abc], {"X": 1, "Y": 2, "Z": 3}[xyz]
                for m in train_months[3:]:
                    Xtr.append(build_features(series, m, ac, xc))
                    ytr.append(series.get(m, 0.0))
                    gtr.append(xyz)
            if len(Xtr) < 20:
                continue
            m = HurdleGammaModel()
            m.train(np.array(Xtr, float), np.array(ytr, float), gtr)
            group_models[g] = m

        for code in parts:
            series = dem[code]
            abc, xyz = meta[code]
            ac, xc = {"A": 3, "B": 2, "C": 1}[abc], {"X": 1, "Y": 2, "Z": 3}[xyz]
            # 逐备件训练（样本足够时），否则回退组模型
            Xtr, ytr, gtr = [], [], []
            for m in train_months[3:]:
                Xtr.append(build_features(series, m, ac, xc))
                ytr.append(series.get(m, 0.0))
                gtr.append(xyz)
            if sum(1 for v in ytr if v > 0) >= 8 and len(Xtr) >= 18:
                model = HurdleGammaModel()
                # 混入同组 30% 样本稳定 k
                Xg, yg, gg = [], [], []
                for c2 in parts:
                    if meta[c2][1] != xyz or c2 == code:
                        continue
                    s2 = dem[c2]
                    a2, x2 = meta[c2]
                    ac2, xc2 = {"A": 3, "B": 2, "C": 1}[a2], {"X": 1, "Y": 2, "Z": 3}[x2]
                    for m in train_months[3::2]:  # 降采样
                        Xg.append(build_features(s2, m, ac2, xc2))
                        yg.append(s2.get(m, 0.0))
                        gg.append(x2)
                if Xg:
                    # 取最多 40 条同组样本
                    Xtr = Xtr + Xg[:40]
                    ytr = ytr + yg[:40]
                    gtr = gtr + gg[:40]
                model.train(np.array(Xtr, float), np.array(ytr, float), gtr)
            else:
                model = group_models.get(xyz)
                if model is None:
                    continue

            pr = model.predict(np.array([build_features(series, t_month, ac, xc)], float), [xyz])[0]
            y = series.get(t_month, 0.0)
            p = float(pr["p_t"])
            mu = float(pr["mu_t"])
            yhat = p * mu
            L, U = float(pr["lower_bound"]), float(pr["upper_bound"])
            y_, m_ = int(t_month[:4]), int(t_month[5:7])

            def pm(k, _m=m_, _y=y_, _s=series):
                mm, yy = _m - k, _y
                while mm <= 0:
                    mm += 12
                    yy -= 1
                return _s.get(f"{yy:04d}-{mm:02d}", 0.0)

            sma = (pm(1) + pm(2) + pm(3)) / 3.0
            all_y.append(y)
            all_yh.append(yhat)
            all_sma.append(sma)
            all_p.append(p)
            all_L.append(L)
            all_U.append(U)
            detail.append({
                "part": code, "month": t_month, "y": y, "yhat": round(yhat, 2),
                "sma": round(sma, 2), "p": round(p, 4), "L": round(L, 2), "U": round(U, 2),
            })

    cov, npos, nc = coverage90(all_y, all_L, all_U)
    result = {
        "samples": len(all_y),
        "wmape_two_stage": round(wmape(all_y, all_yh), 2),
        "wmape_sma3": round(wmape(all_y, all_sma), 2),
        "brier": round(brier(all_y, all_p), 4),
        "coverage90": None if cov is None else round(cov, 2),
        "positive_points": npos,
        "covered": nc,
        "paper_targets": {"wmape": 13.68, "brier": 0.15, "coverage90": 90.9},
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    # by month
    for tm in test_months:
        ys = [d["y"] for d in detail if d["month"] == tm]
        yhs = [d["yhat"] for d in detail if d["month"] == tm]
        if ys:
            print(f"  {tm} n={len(ys)} wmape={wmape(ys, yhs):.2f}")
    out = ROOT / "scripts" / "paper_repro_eval_result.json"
    out.write_text(json.dumps({"summary": result, "detail_head": detail[:20]}, ensure_ascii=False, indent=2), encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    main()
