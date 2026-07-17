#!/usr/bin/env python3
"""离线：读 spare_db 消耗 + labels → narrative_eval → JSON。"""
from __future__ import annotations

import json
import sys
from collections import defaultdict
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "python-ai-service"))

from app.models.narrative_eval import run_narrative_experiment  # noqa: E402


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
      AND DATE_FORMAT(r.approve_time, '%Y-%m') <= '2026-06'
    GROUP BY sp.code, DATE_FORMAT(r.approve_time, '%Y-%m')
    """
    dem = defaultdict(dict)
    with conn.cursor() as cur:
        cur.execute(sql)
        for code, month, qty in cur.fetchall():
            dem[str(code)][str(month)] = float(qty)
    return dem


def main():
    labels_path = ROOT / "sql" / ".paper_part_labels.json"
    focus = None
    part_meta = None
    if labels_path.exists():
        meta = json.loads(labels_path.read_text(encoding="utf-8"))
        focus = meta.get("focus")
        part_meta = {
            c: {"abc": v["abc"], "xyz": v["xyz"]}
            for c, v in meta.get("labels", {}).items()
        }

    conn = pymysql.connect(
        host="127.0.0.1",
        user="admin",
        password="123456",
        database="spare_db",
        charset="utf8mb4",
    )
    try:
        demand = load_demand(conn)
    finally:
        conn.close()

    print(f"loaded {len(demand)} parts, months sample:", sorted({m for s in demand.values() for m in s})[-8:])
    result = run_narrative_experiment(
        demand=demand,
        test_months=6,
        focus_code=focus,
        part_meta=part_meta,
    )
    out = ROOT / "scripts" / "paper_narrative_result.json"
    # detail 可能较大，完整写出
    out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    o = result["overall"]
    print("testMonths:", result["testMonths"])
    print("samples:", result["sampleCount"], "parts:", result["partCount"])
    print("two_stage wMAPE:", o.get("wmapeTwoStage"), "sma3:", o.get("wmapeSma3"))
    print("brier:", o.get("brier"), "coverage:", o.get("cov_coverageRate"))
    print("methods:", result["overallMethods"])
    inv = result["inventory"]["summary"]
    print("inventory:", inv)
    print("saved", out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
