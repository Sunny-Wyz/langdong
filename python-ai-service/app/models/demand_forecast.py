"""
需求预测模型：XGBoost 两阶段 Hurdle-Gamma 概率预测模型

- 阶段一（需求发生分类）：XGBClassifier(objective='binary:logistic') 估计月度/周度需求发生概率 p_t。
- 阶段二（正需求量回归）：在正需求样本 (y > 0) 上使用 XGBRegressor(objective='reg:gamma') 预测正需求的条件均值 mu_t。
- 形状参数 k 的估计：对标准化残差 r_t = y / mu_t 进行极大似然估计 (MLE)，按 XYZ 波动分组共享 k 值以稳定小样本估计。
  - Newton-Raphson 求解极大似然方程 ln(k) - psi(k) = c。
  - 与论文表 3-11 工程选型一致：XYZ 共享整体条件覆盖率 90.9%（独立估计仅 78.6%）。
  - 组内正样本 <2 时回退 global k；无正样本时默认 k=1.0。
- 兼容接口：DemandForecaster (BasePredictor) 实现 12 周自回归递归概率预测。
"""
from __future__ import annotations

import logging
import os
import pickle
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import xgboost as xgb
import scipy.special
import scipy.stats as stats

from app.models.base import BasePredictor
from app.models.feature_engineering import (
    add_static_features,
    add_temporal_features,
)
from app.models.mlflow_utils import EXPERIMENT_DEMAND, log_training_run

logger = logging.getLogger(__name__)

HORIZON = 12          # 预测未来 12 周
FREQ = "W-MON"        # 周一对齐
HIST_EXOG_COLS = [
    "weekly_requisition_apply_qty",
    "weekly_install_qty",
    "weekly_work_order_cnt",
    "weekly_purchase_arrival_qty",
]


def solve_gamma_k(r: np.ndarray, max_iter: int = 100, tol: float = 1e-6) -> float:
    """
    用 Newton-Raphson 迭代法求解极大似然方程：ln(k) - psi(k) = c
    其中 c = ln(mean(r)) - mean(ln(r))，psi 为 digamma 函数。
    """
    r = np.asarray(r)
    r = r[r > 0]
    if len(r) < 2:
        return 1.0
    
    mean_r = np.mean(r)
    mean_ln_r = np.mean(np.log(r))
    c = np.log(mean_r) - mean_ln_r
    
    if c <= 0:
        return 1.0

    # 矩估计作为初值: k_init = 1 / CV^2(r) = mean^2 / var
    var_r = np.var(r)
    if var_r <= 0:
        return 1.0
    k = (mean_r ** 2) / var_r

    for _ in range(max_iter):
        # g(k) = ln(k) - psi(k) - c
        # g'(k) = 1/k - trigamma(k) ， 其中 trigamma 为 polygamma(1, k)
        g = np.log(k) - scipy.special.digamma(k) - c
        g_prime = 1.0 / k - scipy.special.polygamma(1, k)
        
        if g_prime == 0:
            break
            
        step = g / g_prime
        k_next = k - step
        
        # 保证 k_next 为正数，若越界则采取折半回退
        if k_next <= 0:
            k_next = k / 2.0
            
        if abs(k_next - k) < tol:
            k = k_next
            break
        k = k_next
        
    return float(np.clip(k, 1e-3, 1e5))


def compute_xyz_classification(df: pd.DataFrame) -> pd.DataFrame:
    """
    按规则计算各 unique_id 的 XYZ 分类。
    历史数据不足 3 个正样本点 -> Z类
    CV² < 0.5 -> X类（需求稳定）
    0.5 <= CV² < 1.0 -> Y类（需求波动）
    CV² >= 1.0 -> Z类（需求随机）
    """
    if df.empty:
        return pd.DataFrame(columns=["unique_id", "xyz_group"])

    result = []
    for uid, grp in df.groupby("unique_id"):
        demand = grp["y"].values
        nonzero = demand[demand > 0]
        if len(nonzero) < 3:
            xyz = "Z"
        else:
            mean = np.mean(nonzero)
            var = np.var(nonzero)
            cv2 = var / (mean * mean + 1e-8)
            if cv2 < 0.5:
                xyz = "X"
            elif cv2 < 1.0:
                xyz = "Y"
            else:
                xyz = "Z"
        result.append({"unique_id": uid, "xyz_group": xyz})
    return pd.DataFrame(result)


class HurdleGammaModel:
    """XGBoost 两阶段 Hurdle-Gamma 算法实现（超参对齐论文表 3-3）"""

    def __init__(self):
        # 表 3-3：阶段一 / 阶段二最终选取值
        self.clf = xgb.XGBClassifier(
            objective='binary:logistic',
            eval_metric='logloss',
            n_estimators=100,
            max_depth=4,
            learning_rate=0.1,
            min_child_weight=3,
            subsample=0.8,
            colsample_bytree=0.8,
            reg_alpha=0,
            reg_lambda=1.0,
            random_state=42,
            n_jobs=2,
        )
        self.reg = xgb.XGBRegressor(
            objective='reg:gamma',
            eval_metric='mae',
            n_estimators=150,
            max_depth=5,
            learning_rate=0.08,
            min_child_weight=2,
            subsample=0.8,
            colsample_bytree=0.8,
            reg_alpha=0.01,
            reg_lambda=1.0,
            random_state=42,
            n_jobs=2,
        )
        self.k_params = {
            'X': 1.0,
            'Y': 1.0,
            'Z': 1.0,
            'global': 1.0
        }
        self.is_fitted = False

    def train(self, X: np.ndarray, y: np.ndarray, xyz_groups: list[str] | np.ndarray) -> dict[str, Any]:
        X = np.asarray(X, dtype=float)
        y = np.asarray(y, dtype=float)
        xyz_groups = np.asarray(xyz_groups)

        if len(X) != len(y) or len(X) != len(xyz_groups):
            raise ValueError(f"特征与目标维度不一致: X={len(X)}, y={len(y)}, xyz_groups={len(xyz_groups)}")

        # 阶段一：需求发生概率二分类
        y_clf = (y > 0).astype(int)
        
        # 边界情况防崩溃
        if len(np.unique(y_clf)) < 2:
            logger.warning("需求标签全部为0或全部大于0，分类模型退化")
            
        self.clf.fit(X, y_clf)

        # 阶段二：正需求量回归 (仅在 y > 0 上拟合)
        pos_mask = y > 0
        if np.sum(pos_mask) >= 2:
            X_pos = X[pos_mask]
            y_pos = y[pos_mask]
            self.reg.fit(X_pos, y_pos)

            # 标准化残差 r = y / mu
            mu_pos = self.reg.predict(X_pos)
            mu_pos = np.clip(mu_pos, 1e-5, None)
            r_pos = y_pos / mu_pos
            
            # 全局 k 估计（无 XYZ 组或组样本不足时的回退）
            self.k_params['global'] = solve_gamma_k(r_pos)

            # 论文 3.2.2 / 表 3-11：按 XYZ 波动组共享形状参数 k（非逐备件独立估计）
            xyz_pos = xyz_groups[pos_mask]
            for group in ['X', 'Y', 'Z']:
                group_mask = xyz_pos == group
                if np.sum(group_mask) >= 2:
                    self.k_params[group] = solve_gamma_k(r_pos[group_mask])
                else:
                    self.k_params[group] = self.k_params['global']
        else:
            logger.warning("正需求样本数量过少，无法执行回归训练，全部置为默认 k=1")
            self.reg.fit(X, y)
            self.k_params = {'X': 1.0, 'Y': 1.0, 'Z': 1.0, 'global': 1.0}

        self.is_fitted = True
        return {
            "k_params": self.k_params,
            "samples": len(y),
            "pos_samples": int(np.sum(pos_mask))
        }

    def predict(self, X: np.ndarray, xyz_groups: list[str] | np.ndarray) -> list[dict[str, Any]]:
        if not self.is_fitted:
            raise RuntimeError("模型尚未训练")
            
        X = np.asarray(X, dtype=float)
        xyz_groups = np.asarray(xyz_groups)

        p_t = self.clf.predict_proba(X)[:, 1]
        mu_t = self.reg.predict(X)
        mu_t = np.clip(mu_t, 1e-5, None)

        results = []
        for i in range(len(X)):
            grp = xyz_groups[i]
            k_val = float(self.k_params.get(grp, self.k_params['global']))
            # 防止小样本 MLE 把 k 估得过大导致 90% 区间塌缩（论文条件覆盖约 90%）
            # CV²=1/k；k∈[0.5, 25] 对应 CV 约 1.4～0.2
            k_val = float(np.clip(k_val, 0.8, 12.0))

            # 90% 置信区间 (scipy.stats.gamma.ppf)
            lower_bound = float(stats.gamma.ppf(0.05, a=k_val, scale=mu_t[i] / k_val))
            upper_bound = float(stats.gamma.ppf(0.95, a=k_val, scale=mu_t[i] / k_val))

            results.append({
                "p_t": float(p_t[i]),
                "mu_t": float(mu_t[i]),
                "k": float(k_val),
                "lower_bound": lower_bound,
                "upper_bound": upper_bound
            })
            
        return results

    def save(self, path: Path) -> None:
        path = Path(path)
        path.mkdir(parents=True, exist_ok=True)
        with open(path / "hurdle_gamma_model.pkl", "wb") as f:
            pickle.dump(self, f)

    @classmethod
    def load(cls, path: Path) -> HurdleGammaModel:
        path = Path(path)
        with open(path / "hurdle_gamma_model.pkl", "rb") as f:
            return pickle.load(f)


class DemandForecaster(BasePredictor):
    """基于 XGBoost Hurdle-Gamma 模型的周粒度需求预测器包装类"""

    model_name = "demand-forecaster"
    model_version = "2.1.0"

    def __init__(self, horizon: int = HORIZON):
        self.horizon = horizon
        self._model = HurdleGammaModel()
        self._trained = False

    def train(self, df: pd.DataFrame, **kwargs) -> dict[str, float]:
        if df.empty or len(df) < 5:
            logger.warning("训练数据不足，无法训练模型")
            return {"samples": 0.0, "pos_samples": 0.0}

        df = add_temporal_features(df)
        df = add_static_features(df)

        X, y, xyz_groups, _ = self._extract_features(df, is_train=True)
        res = self._model.train(X, y, xyz_groups)
        self._trained = True

        metrics = {
            "samples": float(res["samples"]),
            "pos_samples": float(res["pos_samples"]),
            "k_global": float(res["k_params"]["global"]),
            "k_X": float(res["k_params"]["X"]),
            "k_Y": float(res["k_params"]["Y"]),
            "k_Z": float(res["k_params"]["Z"]),
        }

        try:
            log_training_run(
                EXPERIMENT_DEMAND,
                run_name=f"train-hurdle-gamma-weekly",
                params={"horizon": self.horizon, "freq": FREQ},
                metrics=metrics,
            )
        except Exception as e:
            logger.warning("MLflow logging failed: %s", e)

        return metrics

    def predict(self, df: pd.DataFrame, **kwargs) -> list[dict[str, Any]]:
        if not self._trained:
            raise RuntimeError("模型尚未训练，请先调用 train()")

        df_sorted = df.sort_values(by=["unique_id", "ds"]).copy()
        
        # 计算 XYZ 分类映射
        xyz_df = compute_xyz_classification(df_sorted)
        xyz_map = dict(zip(xyz_df["unique_id"], xyz_df["xyz_group"]))

        results = []
        for uid, grp in df_sorted.groupby("unique_id"):
            grp = grp.reset_index(drop=True)
            if grp.empty:
                continue

            last_row = grp.iloc[-1]
            last_ds = pd.to_datetime(last_row["ds"])
            
            # 滑动历史需求
            recent_demands = list(grp["y"].values[-6:])
            if len(recent_demands) < 6:
                recent_demands = [0.0] * (6 - len(recent_demands)) + recent_demands

            recent_positives = [d for d in grp["y"].values if d > 0][-3:]
            if len(recent_positives) < 3:
                recent_positives = [0.0] * (3 - len(recent_positives)) + recent_positives

            weeks = []
            future_dates = pd.date_range(last_ds + pd.Timedelta(weeks=1), periods=self.horizon, freq=FREQ)

            xyz_grp = xyz_map.get(uid, "Z")
            k_val = self._model.k_params.get(xyz_grp, self._model.k_params['global'])

            for step_idx, ds in enumerate(future_dates):
                lag_1 = recent_demands[-1]
                lag_2 = recent_demands[-2]
                lag_3 = recent_demands[-3]
                lag_3_mean = float(np.mean(recent_demands[-3:]))
                lag_3_std = float(np.std(recent_demands[-3:]))
                zero_ratio_6 = float(np.mean(np.array(recent_demands[-6:]) == 0.0))

                pos_lag_1 = recent_positives[-1]
                pos_lag_3_mean = float(np.mean([v for v in recent_positives if v > 0])) if any(v > 0 for v in recent_positives) else 0.0

                weekly_requisition_apply_qty = float(last_row.get("weekly_requisition_apply_qty", 0.0))
                weekly_install_qty = float(last_row.get("weekly_install_qty", 0.0))
                weekly_work_order_cnt = float(last_row.get("weekly_work_order_cnt", 0.0))
                weekly_purchase_arrival_qty = float(last_row.get("weekly_purchase_arrival_qty", 0.0))

                week_of_year = int(ds.isocalendar().week)
                month = int(ds.month)
                quarter = int(ds.quarter)
                is_quarter_end = 1 if ds.is_quarter_end else 0
                is_year_end = 1 if ds.month == 12 else 0

                lead_time_bucket = int(last_row.get("lead_time_bucket", 1))
                price_bucket = int(last_row.get("price_bucket", 1))

                feat_vec = np.array([[
                    lag_1, lag_2, lag_3, lag_3_mean, lag_3_std, zero_ratio_6,
                    pos_lag_1, pos_lag_3_mean,
                    weekly_requisition_apply_qty, weekly_install_qty, weekly_work_order_cnt, weekly_purchase_arrival_qty,
                    week_of_year, month, quarter, is_quarter_end, is_year_end,
                    lead_time_bucket, price_bucket
                ]], dtype=float)

                p_t = float(self._model.clf.predict_proba(feat_vec)[0, 1])
                mu_t = float(self._model.reg.predict(feat_vec)[0])
                mu_t = max(mu_t, 1e-5)

                pred_val = p_t * mu_t

                p10 = float(stats.gamma.ppf(0.10, a=k_val, scale=mu_t/k_val))
                p25 = float(stats.gamma.ppf(0.25, a=k_val, scale=mu_t/k_val))
                p50 = float(stats.gamma.ppf(0.50, a=k_val, scale=mu_t/k_val))
                p75 = float(stats.gamma.ppf(0.75, a=k_val, scale=mu_t/k_val))
                p90 = float(stats.gamma.ppf(0.90, a=k_val, scale=mu_t/k_val))

                week = {
                    "week_start": str(ds)[:10],
                    "predict_qty": round(pred_val, 2),
                    "quantiles": {
                        "p10": round(p10, 2),
                        "p25": round(p25, 2),
                        "p50": round(p50, 2),
                        "p75": round(p75, 2),
                        "p90": round(p90, 2),
                    },
                }
                weeks.append(week)

                # 递归更新状态
                recent_demands.append(pred_val)
                recent_demands.pop(0)
                if pred_val > 0:
                    recent_positives.append(pred_val)
                    recent_positives.pop(0)

            results.append({
                "part_code": uid,
                "model_type": "XGBoost-Hurdle-Gamma",
                "horizon_weeks": len(weeks),
                "forecast_weeks": weeks,
                "model_version": self.model_version,
            })

        return results

    def explain(self, df: pd.DataFrame, **kwargs) -> dict[str, Any]:
        """返回 XGBoost 模型的特征重要性"""
        if not self._trained:
            return {"message": "Model not trained yet."}
        
        clf_importances = dict(zip(
            [f"f{i}" for i in range(19)], 
            [float(val) for val in self._model.clf.feature_importances_]
        ))
        reg_importances = dict(zip(
            [f"f{i}" for i in range(19)], 
            [float(val) for val in self._model.reg.feature_importances_]
        ))
        return {
            "classifier_feature_importances": clf_importances,
            "regressor_feature_importances": reg_importances,
            "k_params": self._model.k_params
        }

    def save(self, path: Path) -> None:
        path = Path(path)
        path.mkdir(parents=True, exist_ok=True)
        self._model.save(path)
        metadata = {
            "trained": self._trained,
            "horizon": self.horizon
        }
        with open(path / "forecaster_meta.pkl", "wb") as f:
            pickle.dump(metadata, f)

    def load(self, path: Path) -> None:
        path = Path(path)
        self._model = HurdleGammaModel.load(path)
        meta_file = path / "forecaster_meta.pkl"
        if meta_file.exists():
            with open(meta_file, "rb") as f:
                meta = pickle.load(f)
                self._trained = meta.get("trained", False)
                self.horizon = meta.get("horizon", HORIZON)
        else:
            self._trained = True

    def _extract_features(self, df: pd.DataFrame, is_train: bool = True):
        df = df.sort_values(by=["unique_id", "ds"]).copy()
        
        df["lag_1"] = df.groupby("unique_id")["y"].shift(1)
        df["lag_2"] = df.groupby("unique_id")["y"].shift(2)
        df["lag_3"] = df.groupby("unique_id")["y"].shift(3)
        
        df["lag_3_mean"] = df.groupby("unique_id")["y"].shift(1).rolling(3, min_periods=1).mean()
        df["lag_3_std"] = df.groupby("unique_id")["y"].shift(1).rolling(3, min_periods=1).std().fillna(0.0)
        df["zero_ratio_6"] = (df.groupby("unique_id")["y"].shift(1).rolling(6, min_periods=1).apply(lambda x: np.mean(x == 0), raw=True)).fillna(0.0)
        
        pos_lag_1_list = []
        pos_lag_3_mean_list = []
        
        for uid, grp in df.groupby("unique_id"):
            y_vals = grp["y"].values
            pos_lag_1 = np.zeros(len(grp))
            pos_lag_3_mean = np.zeros(len(grp))
            
            recent_pos = []
            for idx in range(len(grp)):
                if idx > 0:
                    val_prev = y_vals[idx - 1]
                    if val_prev > 0:
                        recent_pos.insert(0, val_prev)
                        if len(recent_pos) > 3:
                            recent_pos.pop()
                if recent_pos:
                    pos_lag_1[idx] = recent_pos[0]
                    pos_lag_3_mean[idx] = np.mean(recent_pos)
                else:
                    pos_lag_1[idx] = 0.0
                    pos_lag_3_mean[idx] = 0.0
            
            pos_lag_1_list.extend(pos_lag_1)
            pos_lag_3_mean_list.extend(pos_lag_3_mean)
            
        df["pos_lag_1"] = pos_lag_1_list
        df["pos_lag_3_mean"] = pos_lag_3_mean_list
        
        for col in HIST_EXOG_COLS:
            if col not in df.columns:
                df[col] = 0.0
            df[col] = pd.to_numeric(df[col], errors="coerce").fillna(0.0).astype(float)
            
        if "week_of_year" not in df.columns:
            df = add_temporal_features(df)
            
        if "lead_time_bucket" not in df.columns:
            df = add_static_features(df)
            
        feat_cols = [
            "lag_1", "lag_2", "lag_3", "lag_3_mean", "lag_3_std", "zero_ratio_6",
            "pos_lag_1", "pos_lag_3_mean",
            "weekly_requisition_apply_qty", "weekly_install_qty", "weekly_work_order_cnt", "weekly_purchase_arrival_qty",
            "week_of_year", "month", "quarter", "is_quarter_end", "is_year_end",
            "lead_time_bucket", "price_bucket"
        ]
        
        df[feat_cols] = df[feat_cols].fillna(0.0)
        
        xyz_df = compute_xyz_classification(df)
        df = df.merge(xyz_df, on="unique_id", how="left")
        
        if is_train:
            df_clean = df.groupby("unique_id").apply(lambda x: x.iloc[3:]).reset_index(drop=True)
            if df_clean.empty:
                df_clean = df
            return df_clean[feat_cols].values, df_clean["y"].values, df_clean["xyz_group"].values, df_clean
        else:
            return df[feat_cols].values, df["y"].values, df["xyz_group"].values, df
