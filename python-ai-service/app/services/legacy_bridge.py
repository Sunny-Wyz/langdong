from __future__ import annotations

import importlib.util
import sys
from pathlib import Path
from types import ModuleType
from typing import Any

_PROJECT_ROOT = Path(__file__).resolve().parents[3]

# Make root-level legacy modules importable when executed from python-ai-service.
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))


def _load_module(module_name: str, file_name: str) -> ModuleType:
    module_path = _PROJECT_ROOT / file_name
    spec = importlib.util.spec_from_file_location(module_name, module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Failed to load module from {module_path}")

    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


_predictive_module: ModuleType | None = None
_replenishment_module: ModuleType | None = None


def predict_rul(spare_part_id: int) -> dict[str, Any]:
    global _predictive_module
    if _predictive_module is None:
        _predictive_module = _load_module("predictive_maintenance", "predictive_maintenance.py")

    result = _predictive_module.predict_rul(spare_part_id)
    if not isinstance(result, dict):
        raise RuntimeError("predict_rul returned non-dict result")
    return result


def suggest_replenishment(spare_part_ids: list[int]) -> list[dict[str, Any]]:
    global _replenishment_module
    if _replenishment_module is None:
        _replenishment_module = _load_module("smart_replenishment", "smart_replenishment.py")

    result = _replenishment_module.suggest_replenishment(spare_part_ids)
    if not isinstance(result, list):
        raise RuntimeError("suggest_replenishment returned non-list result")
    return result
