from __future__ import annotations

import importlib.util
import logging
import os
import sys
from pathlib import Path
from types import ModuleType
from typing import Any

logger = logging.getLogger(__name__)

_PROJECT_ROOT = Path(__file__).resolve().parents[3]

# Make root-level legacy modules importable when executed from python-ai-service.
if str(_PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(_PROJECT_ROOT))

# 数据源模式：USE_JAVA_API=true 时通过 Java 内部 API 获取数据，否则直连 DB
_USE_JAVA_API = os.getenv("USE_JAVA_API", "false").lower() in ("true", "1", "yes")


def _load_module(module_name: str, file_name: str) -> ModuleType:
    module_path = _PROJECT_ROOT / file_name
    spec = importlib.util.spec_from_file_location(module_name, module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Failed to load module from {module_path}")

    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _patch_module_with_java_api(module: ModuleType) -> None:
    """
    将 legacy 模块中直连数据库的函数替换为 Java API 客户端版本。
    这样无需修改 legacy 脚本源码，即可切换数据源。
    """
    from app.services.java_data_client import (
        estimate_dynamic_monthly_demand,
        load_consumption_data,
        load_sensor_data,
        load_spare_part_info,
        load_supplier_performance,
    )

    module_name = getattr(module, "__name__", "unknown")

    # predictive_maintenance.py 的数据函数
    if hasattr(module, "load_sensor_data"):
        original_load = module.load_sensor_data

        def patched_load_sensor(spare_part_id: int, engine=None):
            return load_sensor_data(spare_part_id)

        module.load_sensor_data = patched_load_sensor
        logger.info("[%s] Patched load_sensor_data → Java API", module_name)

    if hasattr(module, "get_db_engine"):
        module.get_db_engine = lambda: None
        logger.info("[%s] Patched get_db_engine → no-op", module_name)

    if hasattr(module, "ensure_sensor_table_exists"):
        module.ensure_sensor_table_exists = lambda engine=None: None
        logger.info("[%s] Patched ensure_sensor_table_exists → no-op", module_name)

    # smart_replenishment.py 的数据函数
    if hasattr(module, "load_spare_part_info"):
        module.load_spare_part_info = lambda sid, engine=None: load_spare_part_info(sid)
        logger.info("[%s] Patched load_spare_part_info → Java API", module_name)

    if hasattr(module, "load_consumption_data"):
        module.load_consumption_data = lambda sid, engine=None: load_consumption_data(sid)
        logger.info("[%s] Patched load_consumption_data → Java API", module_name)

    if hasattr(module, "estimate_dynamic_monthly_demand"):
        def patched_demand(sid, category_id=None, engine=None):
            return estimate_dynamic_monthly_demand(sid, category_id)
        module.estimate_dynamic_monthly_demand = patched_demand
        logger.info("[%s] Patched estimate_dynamic_monthly_demand → Java API", module_name)

    if hasattr(module, "load_supplier_performance"):
        module.load_supplier_performance = lambda sid, engine=None: load_supplier_performance(sid)
        logger.info("[%s] Patched load_supplier_performance → Java API", module_name)

    if hasattr(module, "ensure_tables_exist"):
        module.ensure_tables_exist = lambda engine=None: None
        logger.info("[%s] Patched ensure_tables_exist → no-op", module_name)


_predictive_module: ModuleType | None = None
_replenishment_module: ModuleType | None = None


def predict_rul(spare_part_id: int) -> dict[str, Any]:
    global _predictive_module
    if _predictive_module is None:
        _predictive_module = _load_module("predictive_maintenance", "predictive_maintenance.py")
        if _USE_JAVA_API:
            _patch_module_with_java_api(_predictive_module)
            logger.info("predictive_maintenance: 数据源已切换为 Java API")

    result = _predictive_module.predict_rul(spare_part_id)
    if not isinstance(result, dict):
        raise RuntimeError("predict_rul returned non-dict result")
    return result


def suggest_replenishment(spare_part_ids: list[int]) -> list[dict[str, Any]]:
    global _replenishment_module
    if _replenishment_module is None:
        _replenishment_module = _load_module("smart_replenishment", "smart_replenishment.py")
        if _USE_JAVA_API:
            _patch_module_with_java_api(_replenishment_module)
            logger.info("smart_replenishment: 数据源已切换为 Java API")

    result = _replenishment_module.suggest_replenishment(spare_part_ids)
    if not isinstance(result, list):
        raise RuntimeError("suggest_replenishment returned non-list result")
    return result
