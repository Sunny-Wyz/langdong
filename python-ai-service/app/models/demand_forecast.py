"""
需求预测模型：TFT（规律需求）+ DeepAR（间歇性需求）

- 输入：NeuralForecast 格式 DataFrame（unique_id, ds, y + 协变量）
- 输出：12 周概率预测（p10/p25/p50/p75/p90）+ 注意力权重
- 算法选择：ADI ≤ 1.32 → TFT (Normal loss)，ADI > 1.32 → DeepAR (NegBinomial)
"""
from __future__ import annotations

import logging
import os
import pickle
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from app.models.base import BasePredictor
from app.models.feature_engineering import (
    add_static_features,
    add_temporal_features,
    compute_demand_classification,
)
from app.models.mlflow_utils import EXPERIMENT_DEMAND, log_training_run

logger = logging.getLogger(__name__)
os.environ.setdefault("PYTORCH_ENABLE_MPS_FALLBACK", "1")

HORIZON = 12          # 预测未来 12 周
FREQ = "W-MON"        # 周一对齐
HIST_EXOG_COLS = [
    "weekly_requisition_apply_qty",
    "weekly_install_qty",
    "weekly_work_order_cnt",
    "weekly_purchase_arrival_qty",
]


def _build_tft(horizon: int):
    from neuralforecast.models import TFT
    from neuralforecast.losses.pytorch import DistributionLoss

    return TFT(
        h=horizon,
        input_size=2 * horizon,
        hidden_size=64,
        n_head=4,
        dropout=0.1,
        loss=DistributionLoss(distribution="Normal", level=[10, 25, 75, 90]),
        learning_rate=1e-3,
        max_steps=500,
        batch_size=32,
        futr_exog_list=["week_of_year", "month", "quarter", "is_quarter_end", "is_year_end"],
        hist_exog_list=HIST_EXOG_COLS,
        scaler_type="standard",
        enable_progress_bar=False,
        logger=False,
        enable_checkpointing=False,
        accelerator="cpu",
        devices=1,
    )


def _build_deepar(horizon: int):
    from neuralforecast.models import DeepAR
    from neuralforecast.losses.pytorch import DistributionLoss

    return DeepAR(
        h=horizon,
        input_size=2 * horizon,
        lstm_n_layers=2,
        lstm_hidden_size=64,
        loss=DistributionLoss(distribution="NegativeBinomial", level=[10, 25, 75, 90]),
        learning_rate=1e-3,
        max_steps=300,
        batch_size=32,
        enable_progress_bar=False,
        logger=False,
        enable_checkpointing=False,
        accelerator="cpu",
        devices=1,
    )


class DemandForecaster(BasePredictor):
    """TFT + DeepAR 双模型需求预测器"""

    model_name = "demand-forecaster"
    model_version = "2.0.0"

    def __init__(self, horizon: int = HORIZON):
        self.horizon = horizon
        self._nf_tft = None
        self._nf_deepar = None
        self._cls_map: pd.DataFrame | None = None
        self._trained = False

    def train(self, df: pd.DataFrame, **kwargs) -> dict[str, float]:
        """
        训练 TFT 和 DeepAR。
        df 需为 NeuralForecast 格式（unique_id, ds, y 以及协变量列）。
        """
        from neuralforecast import NeuralForecast

        df = add_temporal_features(df)
        df = add_static_features(df)
        df = self._ensure_hist_exog(df)

        # 按算法分组
        cls = compute_demand_classification(df)
        self._cls_map = cls
        tft_ids = cls[cls["algo_type"] == "TFT"]["unique_id"].tolist()
        dar_ids = cls[cls["algo_type"] == "DeepAR"]["unique_id"].tolist()

        metrics: dict[str, float] = {}

        if tft_ids:
            df_tft = df[df["unique_id"].isin(tft_ids)]
            self._nf_tft = NeuralForecast(models=[_build_tft(self.horizon)], freq=FREQ)
            self._nf_tft.fit(df_tft)
            metrics["tft_parts"] = len(tft_ids)
            logger.info("TFT 训练完成，共 %d 个备件", len(tft_ids))

        if dar_ids:
            df_dar = df[df["unique_id"].isin(dar_ids)]
            self._nf_deepar = NeuralForecast(models=[_build_deepar(self.horizon)], freq=FREQ)
            self._nf_deepar.fit(df_dar)
            metrics["deepar_parts"] = len(dar_ids)
            logger.info("DeepAR 训练完成，共 %d 个备件", len(dar_ids))

        self._trained = True

        log_training_run(
            EXPERIMENT_DEMAND,
            run_name=f"train-h{self.horizon}",
            params={"horizon": self.horizon, "freq": FREQ},
            metrics=metrics,
        )
        return metrics

    def predict(self, df: pd.DataFrame, **kwargs) -> list[dict[str, Any]]:
        """
        生成 12 周概率预测，返回结构化列表。
        """
        if not self._trained:
            raise RuntimeError("模型尚未训练，请先调用 train()")

        df = add_temporal_features(df)
        df = self._ensure_hist_exog(df)
        results = []

        # TFT 预测
        if self._nf_tft is not None:
            tft_ids = (self._cls_map[self._cls_map["algo_type"] == "TFT"]["unique_id"].tolist()
                       if self._cls_map is not None else [])
            df_tft = df[df["unique_id"].isin(tft_ids)] if tft_ids else pd.DataFrame()
            if not df_tft.empty:
                preds = self._nf_tft.predict(df_tft, futr_df=self._build_future_temporal_features(df_tft))
                results.extend(self._format_predictions(preds, "TFT"))

        # DeepAR 预测
        if self._nf_deepar is not None:
            dar_ids = (self._cls_map[self._cls_map["algo_type"] == "DeepAR"]["unique_id"].tolist()
                       if self._cls_map is not None else [])
            df_dar = df[df["unique_id"].isin(dar_ids)] if dar_ids else pd.DataFrame()
            if not df_dar.empty:
                preds = self._nf_deepar.predict(df_dar)
                results.extend(self._format_predictions(preds, "DeepAR"))

        return results

    def _build_future_temporal_features(self, df: pd.DataFrame) -> pd.DataFrame:
        rows = []
        for unique_id, grp in df.groupby("unique_id"):
            last_ds = pd.to_datetime(grp["ds"]).max()
            future_dates = pd.date_range(last_ds + pd.Timedelta(weeks=1), periods=self.horizon, freq=FREQ)
            for ds in future_dates:
                rows.append({"unique_id": unique_id, "ds": ds})
        return add_temporal_features(pd.DataFrame(rows))

    def _ensure_hist_exog(self, df: pd.DataFrame) -> pd.DataFrame:
        df = df.copy()
        for col in HIST_EXOG_COLS:
            if col not in df.columns:
                df[col] = 0.0
            df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0.0).astype(float)
        return df

    def _format_predictions(
        self, preds: pd.DataFrame, model_type: str
    ) -> list[dict[str, Any]]:
        """将 NeuralForecast 预测 DataFrame 转换为 API 响应格式。"""
        output = []
        model_col = "TFT" if model_type == "TFT" else "DeepAR"

        for uid, grp in preds.groupby("unique_id"):
            grp = grp.sort_values("ds")
            weeks = []
            for _, row in grp.iterrows():
                week = {
                    "week_start": str(row["ds"])[:10],
                    "predict_qty": round(float(row.get(f"{model_col}-median", row.get(model_col, 0))), 2),
                    "quantiles": {
                        "p10": round(float(row.get(f"{model_col}-lo-90", 0)), 2),
                        "p25": round(float(row.get(f"{model_col}-lo-75", 0)), 2),
                        "p50": round(float(row.get(f"{model_col}-median", 0)), 2),
                        "p75": round(float(row.get(f"{model_col}-hi-75", 0)), 2),
                        "p90": round(float(row.get(f"{model_col}-hi-90", 0)), 2),
                    },
                }
                weeks.append(week)

            output.append({
                "part_code": uid,
                "model_type": model_type,
                "horizon_weeks": len(weeks),
                "forecast_weeks": weeks,
                "model_version": self.model_version,
            })
        return output

    def explain(self, df: pd.DataFrame, **kwargs) -> dict[str, Any]:
        """返回 TFT 注意力权重（可解释性）。"""
        # NeuralForecast TFT 内置 attention — 当前返回空占位符，后续集成
        return {"message": "TFT attention weights available after training", "model": "TFT"}

    def save(self, path: Path) -> None:
        path = Path(path)
        path.mkdir(parents=True, exist_ok=True)
        save_warnings = []
        if self._nf_tft:
            try:
                self._nf_tft.save(str(path / "tft"), overwrite=True)
            except Exception as exc:
                save_warnings.append(f"TFT NeuralForecast.save failed: {exc}")
                with open(path / "tft.pkl", "wb") as f:
                    pickle.dump(self._nf_tft, f)
        if self._nf_deepar:
            try:
                self._nf_deepar.save(str(path / "deepar"), overwrite=True)
            except Exception as exc:
                save_warnings.append(f"DeepAR NeuralForecast.save failed: {exc}")
                with open(path / "deepar.pkl", "wb") as f:
                    pickle.dump(self._nf_deepar, f)
        with open(path / "cls_map.pkl", "wb") as f:
            pickle.dump(self._cls_map, f)
        if save_warnings:
            with open(path / "save_warnings.txt", "w", encoding="utf-8") as f:
                f.write("\n".join(save_warnings))
            logger.warning("DemandForecaster 使用 pickle 兜底保存: %s", "; ".join(save_warnings))
        logger.info("DemandForecaster 保存至 %s", path)

    def load(self, path: Path) -> None:
        from neuralforecast import NeuralForecast

        path = Path(path)
        tft_path = path / "tft"
        dar_path = path / "deepar"
        if tft_path.exists():
            try:
                self._nf_tft = NeuralForecast.load(str(tft_path))
            except Exception as exc:
                logger.warning("TFT NeuralForecast.load 失败，尝试 pickle 兜底加载: %s", exc)
        if self._nf_tft is None and (path / "tft.pkl").exists():
            with open(path / "tft.pkl", "rb") as f:
                self._nf_tft = pickle.load(f)
        if dar_path.exists():
            try:
                self._nf_deepar = NeuralForecast.load(str(dar_path))
            except Exception as exc:
                logger.warning("DeepAR NeuralForecast.load 失败，尝试 pickle 兜底加载: %s", exc)
        if self._nf_deepar is None and (path / "deepar.pkl").exists():
            with open(path / "deepar.pkl", "rb") as f:
                self._nf_deepar = pickle.load(f)
        cls_file = path / "cls_map.pkl"
        if cls_file.exists():
            with open(cls_file, "rb") as f:
                self._cls_map = pickle.load(f)
        self._trained = True
        logger.info("DemandForecaster 加载自 %s", path)
