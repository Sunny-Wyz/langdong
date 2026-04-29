"""合成数据生成器（无真实数据时用于测试与训练）"""
from __future__ import annotations

import numpy as np
import pandas as pd


def generate_weekly_demand_data(
    n_parts: int = 50,
    n_weeks: int = 104,
    seed: int = 42,
) -> pd.DataFrame:
    """
    生成多备件周粒度需求数据，包含 4 种模式：
    stable / seasonal / trending / intermittent
    返回 NeuralForecast 格式的 DataFrame（unique_id, ds, y）。
    """
    rng = np.random.default_rng(seed)
    patterns = ["stable", "seasonal", "trending", "intermittent"]
    start = pd.Timestamp("2022-01-03")  # 周一对齐
    weeks = pd.date_range(start, periods=n_weeks, freq="W-MON")
    records = []

    for i in range(n_parts):
        part_code = f"P{i:04d}"
        pattern = patterns[i % len(patterns)]
        base = rng.uniform(2, 20)

        if pattern == "stable":
            demand = rng.poisson(base, n_weeks).astype(float)
        elif pattern == "seasonal":
            t = np.arange(n_weeks)
            seasonal = base + 0.4 * base * np.sin(2 * np.pi * t / 52)
            demand = rng.poisson(np.clip(seasonal, 0.5, None)).astype(float)
        elif pattern == "trending":
            trend = np.linspace(base * 0.5, base * 1.5, n_weeks)
            demand = rng.poisson(np.clip(trend, 0.5, None)).astype(float)
        else:  # intermittent
            demand = np.zeros(n_weeks)
            active = rng.choice(n_weeks, size=int(n_weeks * 0.3), replace=False)
            demand[active] = rng.poisson(base, len(active)).astype(float)

        for j, w in enumerate(weeks):
            records.append({"unique_id": part_code, "ds": w, "y": demand[j]})

    return pd.DataFrame(records)


def generate_static_features(n_parts: int = 50) -> pd.DataFrame:
    """生成静态特征表（lead_time_bucket, price_bucket）。"""
    rng = np.random.default_rng(0)
    return pd.DataFrame({
        "unique_id": [f"P{i:04d}" for i in range(n_parts)],
        "lead_time_bucket": rng.integers(0, 5, n_parts),
        "price_bucket": rng.integers(0, 4, n_parts),
    })


def generate_sensor_data(
    n_devices: int = 20,
    seq_len: int = 200,
    seed: int = 42,
) -> pd.DataFrame:
    """
    生成设备传感器时序数据（带退化趋势）。
    包含 7 个通道：operating_hours, temperature, vibration,
    pressure, current_load, rpm, error_code。
    返回含 device_id / timestamp / 7 传感器列 / rul 的 DataFrame。
    """
    rng = np.random.default_rng(seed)
    SENSORS = ["operating_hours", "temperature", "vibration",
               "pressure", "current_load", "rpm", "error_code"]
    records = []

    for dev in range(n_devices):
        device_id = f"DEV{dev:03d}"
        total_life = rng.integers(1500, 3000)
        t = np.arange(seq_len)
        degrade = t / seq_len  # 0→1 退化进度

        op_hours = t * rng.uniform(1.5, 3.0)
        temperature = 60 + 20 * degrade + rng.normal(0, 2, seq_len)
        vibration = 0.5 + 1.5 * degrade + rng.normal(0, 0.1, seq_len)
        pressure = 100 - 20 * degrade + rng.normal(0, 3, seq_len)
        current_load = 0.6 + 0.3 * degrade + rng.normal(0, 0.05, seq_len)
        rpm = 3000 - 500 * degrade + rng.normal(0, 50, seq_len)
        error_code = (degrade > 0.8).astype(float) * rng.binomial(1, 0.4, seq_len)
        rul = np.clip(total_life - op_hours, 0, None)

        for j in range(seq_len):
            records.append({
                "device_id": device_id,
                "timestamp": pd.Timestamp("2023-01-01") + pd.Timedelta(hours=int(op_hours[j])),
                "operating_hours": op_hours[j],
                "temperature": temperature[j],
                "vibration": vibration[j],
                "pressure": pressure[j],
                "current_load": current_load[j],
                "rpm": rpm[j],
                "error_code": error_code[j],
                "rul": rul[j],
            })

    return pd.DataFrame(records)
