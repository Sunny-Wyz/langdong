"""数据加载与格式转换：Java API → NeuralForecast / tsai 格式"""
from __future__ import annotations

import logging
from typing import Any

import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

_SENSOR_COLS = [
    "operating_hours", "temperature", "vibration",
    "pressure", "current_load", "rpm", "error_code",
]


def load_daily_train_data(
    java_client,
    start_date: str | None = None,
    end_date: str | None = None,
) -> pd.DataFrame:
    """
    通过 Java 内部 API 获取日粒度出库数据。
    端点：GET /internal/ai/train-data/daily
    返回含 part_code / date / outbound_qty 等 5 列的 DataFrame。
    """
    try:
        params: dict[str, Any] = {}
        if start_date:
            params["startDate"] = start_date
        if end_date:
            params["endDate"] = end_date
        result = java_client._get("/internal/ai/train-data/daily", params=params)
        data = result.get("data", [])
        df = pd.DataFrame(data)
        if not df.empty:
            df["date"] = pd.to_datetime(df["date"])
            num_cols = ["outbound_qty", "requisition_apply_qty",
                        "install_qty", "work_order_cnt", "purchase_arrival_qty"]
            for col in num_cols:
                if col in df.columns:
                    df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0)
        logger.info("日粒度训练数据: %d 条记录", len(df))
        return df
    except Exception:
        logger.exception("加载日粒度训练数据失败，返回空 DataFrame")
        return pd.DataFrame()


def aggregate_daily_to_weekly(df: pd.DataFrame) -> pd.DataFrame:
    """
    日粒度 → 周粒度聚合（周一为起始）。
    输入列：part_code, date, outbound_qty（以及其他数值列）
    输出列：part_code, week_start, outbound_qty（以及其他数值列聚合值）
    """
    if df.empty:
        return df

    df = df.copy()
    df["date"] = pd.to_datetime(df["date"])
    df["week_start"] = df["date"] - pd.to_timedelta(df["date"].dt.dayofweek, unit="D")

    num_cols = df.select_dtypes(include="number").columns.tolist()
    agg_dict = {col: "sum" for col in num_cols if col != "part_code"}
    grouped = df.groupby(["part_code", "week_start"], as_index=False).agg(agg_dict)
    return grouped


def to_neuralforecast_format(df: pd.DataFrame) -> pd.DataFrame:
    """
    将业务 DataFrame 转换为 NeuralForecast 所需格式：
    - unique_id: 备件编码
    - ds: 周起始日期（datetime）
    - y: 周出库量
    - 其他历史协变量列（hist_exog）
    """
    nf = pd.DataFrame()
    nf["unique_id"] = df["part_code"]
    nf["ds"] = pd.to_datetime(df["week_start"])
    nf["y"] = df["outbound_qty"].fillna(0).astype(float)

    hist_cols = ["requisition_apply_qty", "install_qty", "work_order_cnt", "purchase_arrival_qty"]
    for col in hist_cols:
        if col in df.columns:
            nf[f"weekly_{col}"] = df[col].fillna(0).astype(float)

    return nf.sort_values(["unique_id", "ds"]).reset_index(drop=True)


def to_tsai_format(
    df: pd.DataFrame,
    seq_len: int = 30,
    sensor_cols: list[str] | None = None,
) -> tuple[np.ndarray, np.ndarray]:
    """
    将传感器 DataFrame 转换为 tsai 所需的 (X, y) 格式。
    X shape: (n_samples, n_channels, seq_len)
    y shape: (n_samples,) — RUL 目标值
    """
    cols = sensor_cols or _SENSOR_COLS
    available = [c for c in cols if c in df.columns]
    if not available:
        raise ValueError(f"DataFrame 缺少传感器列，期望: {cols}")

    records = df[available + ["rul"]].values
    X_list, y_list = [], []
    for i in range(len(records) - seq_len + 1):
        window = records[i: i + seq_len]
        X_list.append(window[:-1, :-1].T)   # (channels, seq_len-1)
        y_list.append(window[-1, -1])         # 最后时刻的 RUL

    if not X_list:
        return np.empty((0, len(available), seq_len - 1)), np.empty(0)

    return np.array(X_list, dtype=np.float32), np.array(y_list, dtype=np.float32)


def split_train_val(
    df: pd.DataFrame,
    val_weeks: int = 12,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """按时间分割训练集和验证集，保持时序顺序。"""
    df = df.sort_values("ds")
    cutoff = df["ds"].max() - pd.Timedelta(weeks=val_weeks)
    return df[df["ds"] <= cutoff].copy(), df[df["ds"] > cutoff].copy()
