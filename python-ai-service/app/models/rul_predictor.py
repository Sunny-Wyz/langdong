"""
剩余寿命（RUL）预测模型：InceptionTime（tsai）

- 输入：7 通道传感器序列，窗口长度 30
- 输出：预测 RUL（小时）、置信区间、SHAP 特征重要度
- 不确定性估计：MC Dropout（100 次前向传播）
"""
from __future__ import annotations

import logging
import pickle
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from app.models.base import BasePredictor
from app.models.mlflow_utils import EXPERIMENT_RUL, log_training_run

logger = logging.getLogger(__name__)

SEQ_LEN = 30
SENSOR_COLS = [
    "operating_hours", "temperature", "vibration",
    "pressure", "current_load", "rpm", "error_code",
]
N_MC = 100          # MC Dropout 推理次数
RUL_CRITICAL = 500  # 小时
RUL_WARNING = 1000  # 小时


class RulPredictor(BasePredictor):
    """基于 InceptionTime 的设备 RUL 预测器"""

    model_name = "rul-predictor"
    model_version = "2.0.0"

    def __init__(self, seq_len: int = SEQ_LEN, model_type: str = "InceptionTime"):
        self.seq_len = seq_len
        self.model_type = model_type
        self._learner = None
        self._scaler = None
        self._trained = False

    # ------------------------------------------------------------------
    # 数据预处理
    # ------------------------------------------------------------------

    def _preprocess(self, df: pd.DataFrame) -> tuple[np.ndarray, np.ndarray | None]:
        """将传感器 DataFrame 转换为 (X, y) numpy 格式。"""
        from sklearn.preprocessing import MinMaxScaler

        available = [c for c in SENSOR_COLS if c in df.columns]
        has_rul = "rul" in df.columns

        values = df[available].values.astype(np.float32)
        rul_values = df["rul"].values.astype(np.float32) if has_rul else None

        if self._scaler is None:
            self._scaler = MinMaxScaler()
            values = self._scaler.fit_transform(values)
        else:
            values = self._scaler.transform(values)

        X_list, y_list = [], []
        for i in range(len(values) - self.seq_len + 1):
            X_list.append(values[i: i + self.seq_len].T)   # (channels, seq_len)
            if rul_values is not None:
                y_list.append(rul_values[i + self.seq_len - 1])

        X = np.array(X_list, dtype=np.float32)
        y = np.array(y_list, dtype=np.float32) if y_list else None
        return X, y

    # ------------------------------------------------------------------
    # 训练
    # ------------------------------------------------------------------

    def train(self, df: pd.DataFrame, **kwargs) -> dict[str, float]:
        """训练 InceptionTime 回归模型。"""
        from fastai.callback.core import Callback
        from tsai.all import TSRegression, get_ts_dls, InceptionTime, TST

        X, y = self._preprocess(df)
        if X.shape[0] == 0:
            raise ValueError("数据样本不足以构建序列窗口")

        split = int(len(X) * 0.8)
        splits = (list(range(split)), list(range(split, len(X))))
        dls = get_ts_dls(X, y, splits=splits, bs=32)

        arch = InceptionTime if self.model_type == "InceptionTime" else TST
        self._learner = TSRegression(dls, arch, metrics=[], loss_func="huber")
        self._learner.fit_one_cycle(20, lr_max=1e-3)

        preds, targets = self._learner.get_preds()
        preds_np = preds.numpy().flatten()
        targets_np = targets.numpy().flatten()

        mae = float(np.mean(np.abs(preds_np - targets_np)))
        rmse = float(np.sqrt(np.mean((preds_np - targets_np) ** 2)))
        metrics = {"mae": mae, "rmse": rmse, "n_samples": len(X)}

        self._trained = True
        log_training_run(
            EXPERIMENT_RUL,
            run_name=f"train-{self.model_type}",
            params={"seq_len": self.seq_len, "model_type": self.model_type},
            metrics=metrics,
        )
        logger.info("RulPredictor 训练完成: MAE=%.1f, RMSE=%.1f", mae, rmse)
        return metrics

    # ------------------------------------------------------------------
    # 预测（MC Dropout）
    # ------------------------------------------------------------------

    def predict(self, df: pd.DataFrame, **kwargs) -> list[dict[str, Any]]:
        """对最新传感器窗口进行推理，返回 RUL 估算结果。"""
        if not self._trained or self._learner is None:
            raise RuntimeError("模型尚未训练")

        X, _ = self._preprocess(df)
        if X.shape[0] == 0:
            return [{"error": "数据不足", "predicted_rul": None}]

        import torch

        # 取最后一个窗口
        x_last = torch.from_numpy(X[-1:])

        # MC Dropout：启用 dropout，多次前向传播
        self._learner.model.train()  # 保持 dropout active
        mc_preds = []
        with torch.no_grad():
            for _ in range(N_MC):
                out = self._learner.model(x_last)
                mc_preds.append(out.item())

        self._learner.model.eval()
        mc_arr = np.array(mc_preds)
        mean_rul = float(np.mean(mc_arr))
        std_rul = float(np.std(mc_arr))
        ci_lo = float(np.percentile(mc_arr, 5))
        ci_hi = float(np.percentile(mc_arr, 95))

        alert, msg = self._classify_alert(mean_rul)
        return [{
            "predicted_rul": round(mean_rul, 1),
            "confidence_interval": [round(ci_lo, 1), round(ci_hi, 1)],
            "distribution": {"mean": round(mean_rul, 1), "std": round(std_rul, 1)},
            "alert": alert,
            "alert_message": msg,
            "model_type": self.model_type,
            "model_version": self.model_version,
        }]

    @staticmethod
    def _classify_alert(rul: float) -> tuple[str, str]:
        if rul < RUL_CRITICAL:
            return "CRITICAL", f"剩余寿命不足 {RUL_CRITICAL} 小时，建议立即安排维修"
        if rul < RUL_WARNING:
            return "WARNING", f"剩余寿命不足 {RUL_WARNING} 小时，建议计划维修"
        return "OK", f"设备状态正常，预估剩余寿命 {rul:.0f} 小时"

    # ------------------------------------------------------------------
    # 可解释性（SHAP）
    # ------------------------------------------------------------------

    def explain(self, df: pd.DataFrame, **kwargs) -> dict[str, Any]:
        """使用 SHAP DeepExplainer 计算传感器通道重要度（强制 CPU）。"""
        if not self._trained or self._learner is None:
            return {"error": "模型未训练"}

        try:
            import shap
            import torch

            X, _ = self._preprocess(df)
            if X.shape[0] < 5:
                return {"error": "样本数不足（至少需要 5 个窗口）"}

            model_cpu = self._learner.model.cpu().eval()
            background = torch.from_numpy(X[:min(20, len(X))]).float()
            sample = torch.from_numpy(X[-1:]).float()

            explainer = shap.DeepExplainer(model_cpu, background)
            shap_values = explainer.shap_values(sample)

            # 按通道聚合（均值绝对值）
            sv = np.array(shap_values).squeeze()  # (channels, seq_len) or (seq_len,)
            if sv.ndim == 2:
                channel_importance = np.abs(sv).mean(axis=1)
            else:
                channel_importance = np.abs(sv)

            available = [c for c in SENSOR_COLS if c in df.columns]
            top = dict(zip(available, channel_importance[:len(available)].tolist()))
            top = dict(sorted(top.items(), key=lambda x: x[1], reverse=True))
            return {"top_features": top, "method": "SHAP DeepExplainer"}
        except Exception:
            logger.exception("SHAP 解释失败")
            return {"error": "SHAP 计算失败", "top_features": {}}

    # ------------------------------------------------------------------
    # 持久化
    # ------------------------------------------------------------------

    def save(self, path: Path) -> None:
        path = Path(path)
        path.mkdir(parents=True, exist_ok=True)
        if self._learner:
            self._learner.export(path / "learner.pkl")
        with open(path / "scaler.pkl", "wb") as f:
            pickle.dump(self._scaler, f)
        logger.info("RulPredictor 保存至 %s", path)

    def load(self, path: Path) -> None:
        from tsai.all import load_learner

        path = Path(path)
        learner_file = path / "learner.pkl"
        if learner_file.exists():
            self._learner = load_learner(learner_file)
        scaler_file = path / "scaler.pkl"
        if scaler_file.exists():
            with open(scaler_file, "rb") as f:
                self._scaler = pickle.load(f)
        self._trained = True
        logger.info("RulPredictor 加载自 %s", path)
