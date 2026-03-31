"""
Java 内部 API 数据客户端

替代 Python 直连 MySQL 的方式，通过 Java 后端的 /internal/ai/ 接口获取数据。
所有数据库操作统一由 Java 负责，Python 只负责算法计算。
"""
from __future__ import annotations

import logging
import os
from typing import Any

import httpx
import pandas as pd

logger = logging.getLogger(__name__)

_JAVA_BASE_URL = os.getenv("JAVA_BASE_URL", "http://localhost:8080")
_INTERNAL_TOKEN = os.getenv("JAVA_CALLBACK_TOKEN", "")
_TIMEOUT = 15.0


def _headers() -> dict[str, str]:
    return {"X-Internal-Token": _INTERNAL_TOKEN}


def _get(path: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
    url = f"{_JAVA_BASE_URL}{path}"
    resp = httpx.get(url, headers=_headers(), params=params, timeout=_TIMEOUT)
    resp.raise_for_status()
    return resp.json()


def load_sensor_data(spare_part_id: int) -> pd.DataFrame:
    """获取备件传感器历史数据，返回 DataFrame。"""
    try:
        result = _get(f"/internal/ai/spare-parts/{spare_part_id}/sensor-data")
        data = result.get("data", [])
        df = pd.DataFrame(data)
        logger.info("备件 ID=%d：从 Java API 获取 %d 条传感器记录", spare_part_id, len(df))
        return df
    except Exception:
        logger.exception("加载传感器数据失败: spare_part_id=%d", spare_part_id)
        return pd.DataFrame()


def load_spare_part_info(spare_part_id: int) -> dict[str, Any] | None:
    """获取备件基本信息。"""
    try:
        result = _get(f"/internal/ai/spare-parts/{spare_part_id}/info")
        return result.get("data")
    except httpx.HTTPStatusError as e:
        if e.response.status_code == 404:
            return None
        raise
    except Exception:
        logger.exception("加载备件信息失败: spare_part_id=%d", spare_part_id)
        return None


def load_consumption_data(spare_part_id: int) -> pd.DataFrame:
    """获取月度消耗数据，Java 已做业务表/日志表回退逻辑。"""
    try:
        result = _get(f"/internal/ai/spare-parts/{spare_part_id}/consumption")
        data = result.get("data", {})
        records = data.get("data", []) if isinstance(data, dict) else data
        df = pd.DataFrame(records)
        logger.info("备件 ID=%d：从 Java API 获取 %d 个月消耗记录", spare_part_id, len(df))
        return df
    except Exception:
        logger.exception("加载消耗数据失败: spare_part_id=%d", spare_part_id)
        return pd.DataFrame()


def estimate_dynamic_monthly_demand(
    spare_part_id: int,
    category_id: int | None,
) -> tuple[float, str]:
    """动态月度需求估算（备件 → 类目 → 全局 → 最小保护值）。"""
    try:
        params: dict[str, Any] = {}
        if category_id is not None:
            params["categoryId"] = category_id
        result = _get(f"/internal/ai/spare-parts/{spare_part_id}/demand-estimate", params=params)
        data = result.get("data", {})
        return float(data.get("estimate", 1.0)), str(data.get("source", "MIN_GUARD"))
    except Exception:
        logger.exception("需求估算失败: spare_part_id=%d", spare_part_id)
        return 1.0, "MIN_GUARD"


def load_supplier_performance(spare_part_id: int) -> pd.DataFrame:
    """获取供应商绩效数据。"""
    try:
        result = _get(f"/internal/ai/spare-parts/{spare_part_id}/supplier-performance")
        data = result.get("data", [])
        df = pd.DataFrame(data)
        df.drop_duplicates(subset=["supplier_name"], keep="first", inplace=True)
        return df
    except Exception:
        logger.exception("加载供应商绩效失败: spare_part_id=%d", spare_part_id)
        return pd.DataFrame()
