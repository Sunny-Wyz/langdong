#!/usr/bin/env bash
# 论文量级实验一键重跑
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "=== [1/4] seed v6 ==="
python3 sql/seed_paper_repro_consumption.py

echo "=== [2/4] narrative offline eval ==="
python3 scripts/run_narrative_offline.py

echo "=== [3/4] export xlsx ==="
python3 scripts/export_paper_xlsx.py

echo "=== [4/4] validate ==="
python3 scripts/validate_paper_xlsx.py

echo "DONE"
