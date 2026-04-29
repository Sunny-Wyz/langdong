"""特征工程：需求分类、时序特征、传感器滚动特征"""
from __future__ import annotations

import numpy as np
import pandas as pd


def compute_demand_classification(df: pd.DataFrame) -> pd.DataFrame:
    """
    按 Syntetos-Boylan 方法计算 ADI 和 CV²，用于选择 TFT 或 DeepAR。

    ADI > 1.32 → 间歇性需求 → DeepAR (NegBinomial)
    ADI ≤ 1.32 → 规律需求 → TFT (Normal)
    """
    result = []
    for uid, grp in df.groupby("unique_id"):
        demand = grp["y"].values
        nonzero = demand[demand > 0]

        # ADI：非零需求间隔期望
        if len(nonzero) < 2:
            adi, cv2, algo = 99.0, 0.0, "DeepAR"
        else:
            zero_mask = demand == 0
            intervals = np.diff(np.where(~zero_mask)[0])
            adi = float(np.mean(intervals)) if len(intervals) > 0 else 1.0
            cv2 = float(np.var(nonzero) / (np.mean(nonzero) ** 2 + 1e-8))
            algo = "DeepAR" if adi > 1.32 else "TFT"

        result.append({"unique_id": uid, "adi": adi, "cv2": cv2, "algo_type": algo})

    return pd.DataFrame(result)


def add_static_features(df: pd.DataFrame, part_info: pd.DataFrame | None = None) -> pd.DataFrame:
    """添加静态协变量：lead_time_bucket、price_bucket、algo_type 等。"""
    df = df.copy()
    cls = compute_demand_classification(df)
    df = df.merge(cls.drop(columns=["algo_type"]), on="unique_id", how="left")

    if part_info is not None and not part_info.empty:
        if "lead_time" in part_info.columns:
            df = df.merge(part_info[["unique_id", "lead_time"]], on="unique_id", how="left")
            df["lead_time_bucket"] = pd.cut(
                df["lead_time"].fillna(14),
                bins=[0, 7, 14, 30, 90, 9999],
                labels=[0, 1, 2, 3, 4],
            ).astype(int)
        if "unit_price" in part_info.columns:
            df = df.merge(part_info[["unique_id", "unit_price"]], on="unique_id", how="left")
            df["price_bucket"] = pd.cut(
                df["unit_price"].fillna(100),
                bins=[0, 50, 200, 1000, 9999],
                labels=[0, 1, 2, 3],
            ).astype(int)

    df["lead_time_bucket"] = df.get("lead_time_bucket", pd.Series(1, index=df.index)).fillna(1).astype(int)
    df["price_bucket"] = df.get("price_bucket", pd.Series(1, index=df.index)).fillna(1).astype(int)
    return df


def add_temporal_features(df: pd.DataFrame) -> pd.DataFrame:
    """添加时序协变量（已知未来特征）：周次、月份、季度末、年末。"""
    df = df.copy()
    df["ds"] = pd.to_datetime(df["ds"])
    df["week_of_year"] = df["ds"].dt.isocalendar().week.astype(int)
    df["month"] = df["ds"].dt.month
    df["quarter"] = df["ds"].dt.quarter
    df["is_quarter_end"] = df["ds"].dt.is_quarter_end.astype(int)
    df["is_year_end"] = (df["ds"].dt.month == 12).astype(int)
    return df


def add_sensor_rolling_features(df: pd.DataFrame, window: int = 5) -> pd.DataFrame:
    """
    为 RUL 传感器数据添加滚动均值、滚动标准差和一阶差分。
    """
    df = df.copy()
    sensor_cols = [c for c in df.columns if c not in ("device_id", "timestamp", "rul")]
    for col in sensor_cols:
        df[f"{col}_mean{window}"] = df[col].rolling(window, min_periods=1).mean()
        df[f"{col}_std{window}"] = df[col].rolling(window, min_periods=1).std().fillna(0)
        df[f"{col}_diff1"] = df[col].diff().fillna(0)
    return df
