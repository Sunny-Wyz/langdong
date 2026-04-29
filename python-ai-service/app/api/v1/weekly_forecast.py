"""周粒度深度学习需求预测 API"""
from __future__ import annotations

import logging
import os
import threading
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx
from fastapi import APIRouter, BackgroundTasks, HTTPException
from pydantic import BaseModel

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/weekly", tags=["weekly-forecast"])

_MODEL_DIR = Path(os.getenv("MODEL_DIR", "/tmp/langdong_models"))
_JAVA_BASE_URL = os.getenv("JAVA_BASE_URL", "http://localhost:8080")
_CALLBACK_TOKEN = os.getenv("JAVA_CALLBACK_TOKEN") or os.getenv("PYTHON_CALLBACK_TOKEN", "")

# 全局单例（lazy 初始化）
_forecaster = None
_status_lock = threading.Lock()
_train_status: dict[str, Any] = {
    "task_id": None,
    "status": "IDLE",
    "progress": 0,
    "stage": "IDLE",
    "message": "暂无训练任务",
    "started_at": None,
    "ended_at": None,
    "elapsed_seconds": None,
    "error": None,
    "metrics": {},
    "use_synthetic": None,
}


def _get_forecaster():
    global _forecaster
    if _forecaster is None:
        from app.models.demand_forecast import DemandForecaster
        _forecaster = DemandForecaster()
        saved = _MODEL_DIR / "demand_forecaster"
        if saved.exists():
            try:
                _forecaster.load(saved)
                logger.info("已加载保存的 DemandForecaster 模型")
            except Exception:
                logger.warning("加载保存模型失败，将在训练后可用")
    return _forecaster


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _elapsed_seconds(started_at: str | None, ended_at: str | None = None) -> float | None:
    if not started_at:
        return None
    try:
        start = datetime.fromisoformat(started_at)
        end = datetime.fromisoformat(ended_at) if ended_at else datetime.now(timezone.utc)
        return round((end - start).total_seconds(), 1)
    except Exception:
        return None


def _update_train_status(**updates: Any) -> None:
    with _status_lock:
        _train_status.update(updates)
        _train_status["elapsed_seconds"] = _elapsed_seconds(
            _train_status.get("started_at"),
            _train_status.get("ended_at"),
        )


def _snapshot_train_status() -> dict[str, Any]:
    with _status_lock:
        snapshot = dict(_train_status)
    snapshot["elapsed_seconds"] = _elapsed_seconds(snapshot.get("started_at"), snapshot.get("ended_at"))
    return snapshot


class TrainRequest(BaseModel):
    start_date: str | None = None
    end_date: str | None = None
    use_synthetic: bool = False


class ForecastRequest(BaseModel):
    part_codes: list[str] | None = None
    horizon_weeks: int = 12


# ------------------------------------------------------------------ #

@router.post("/train")
def trigger_training(req: TrainRequest, background_tasks: BackgroundTasks):
    """触发深度学习模型训练（后台异步）。"""
    with _status_lock:
        if _train_status.get("status") == "RUNNING":
            raise HTTPException(status_code=409, detail="已有训练正在进行，请稍后再试")

        task_id = uuid.uuid4().hex
        started_at = _now_iso()
        _train_status.update({
            "task_id": task_id,
            "status": "RUNNING",
            "progress": 10,
            "stage": "SUBMITTED",
            "message": "训练任务已提交后台",
            "started_at": started_at,
            "ended_at": None,
            "elapsed_seconds": 0,
            "error": None,
            "metrics": {},
            "use_synthetic": req.use_synthetic,
        })

    background_tasks.add_task(_do_train, task_id, req.start_date, req.end_date, req.use_synthetic)
    return {"message": "训练任务已提交后台", "task_id": task_id, "use_synthetic": req.use_synthetic}


@router.post("/predict")
def trigger_predict(req: ForecastRequest):
    """触发预测并将结果回调写入 Java。"""
    forecaster = _get_forecaster()
    if not forecaster.is_trained():
        raise HTTPException(status_code=503, detail="模型尚未训练，请先调用 /train")

    df = _load_predict_data(req.part_codes)
    results = forecaster.predict(df)
    _push_to_java(results)
    return {"message": "预测完成", "parts": len(results)}


@router.get("/status")
def model_status():
    """返回模型训练状态。"""
    forecaster = _get_forecaster()
    return {
        "trained": forecaster.is_trained(),
        "model_name": forecaster.model_name,
        "model_version": forecaster.model_version,
        "model_dir": str(_MODEL_DIR / "demand_forecaster"),
    }


@router.get("/train/status")
def training_status():
    """返回当前周粒度训练任务进度（内存态，不持久化）。"""
    forecaster = _get_forecaster()
    status = _snapshot_train_status()
    status.update({
        "trained": forecaster.is_trained(),
        "model_name": forecaster.model_name,
        "model_version": forecaster.model_version,
        "model_dir": str(_MODEL_DIR / "demand_forecaster"),
    })
    return status


# ------------------------------------------------------------------ #
# 内部函数

def _do_train(task_id: str, start_date: str | None, end_date: str | None, use_synthetic: bool) -> None:
    try:
        _update_train_status(
            task_id=task_id,
            status="RUNNING",
            progress=20,
            stage="LOAD_MODEL",
            message="正在加载模型实例",
        )
        forecaster = _get_forecaster()

        _update_train_status(
            progress=30,
            stage="LOAD_DATA",
            message="正在准备训练数据",
        )
        if use_synthetic:
            from app.models.synthetic_data import generate_weekly_demand_data
            df = generate_weekly_demand_data(n_parts=50, n_weeks=104)
        else:
            from app.services.java_data_client import _get

            class _Client:
                @staticmethod
                def _get(path, params=None):
                    return _get(path, params)

            from app.models.data_loader import load_daily_train_data, aggregate_daily_to_weekly, to_neuralforecast_format
            daily = load_daily_train_data(_Client, start_date=start_date, end_date=end_date)
            weekly = aggregate_daily_to_weekly(daily)
            df = to_neuralforecast_format(weekly)

        if df.empty or len(df) < 10:
            message = "训练数据不足，训练已终止"
            _update_train_status(
                status="FAILED",
                progress=100,
                stage="FAILED",
                message=message,
                error=message,
                ended_at=_now_iso(),
            )
            logger.warning(message)
            return

        _update_train_status(
            progress=45,
            stage="FEATURE_ENGINEERING",
            message=f"训练数据准备完成，共 {len(df)} 条记录",
        )
        _update_train_status(
            progress=65,
            stage="TRAINING",
            message="模型训练中，请稍候",
        )
        metrics = forecaster.train(df)
        _update_train_status(
            progress=90,
            stage="SAVE_MODEL",
            message="正在保存模型",
            metrics=metrics,
        )
        forecaster.save(_MODEL_DIR / "demand_forecaster")
        _update_train_status(
            status="SUCCESS",
            progress=100,
            stage="SUCCESS",
            message="深度学习模型训练完成",
            metrics=metrics,
            ended_at=_now_iso(),
            error=None,
        )
        logger.info("深度学习模型训练完成: %s", metrics)
    except Exception as exc:
        _update_train_status(
            status="FAILED",
            progress=100,
            stage="FAILED",
            message="训练失败",
            error=str(exc),
            ended_at=_now_iso(),
        )
        logger.exception("训练失败")


def _load_predict_data(part_codes: list[str] | None) -> "pd.DataFrame":
    import pandas as pd
    from app.models.synthetic_data import generate_weekly_demand_data
    df = generate_weekly_demand_data(n_parts=50, n_weeks=104)
    if part_codes:
        df = df[df["unique_id"].isin(part_codes)]
    return df


def _push_to_java(results: list[dict[str, Any]]) -> None:
    """将预测结果批量 POST 到 Java /api/python/callback/weekly。"""
    forecasts = []
    for r in results:
        for w in r.get("forecast_weeks", []):
            q = w.get("quantiles", {})
            forecasts.append({
                "part_code": r["part_code"],
                "week_start": w["week_start"],
                "predict_qty": w["predict_qty"],
                "p10": q.get("p10"),
                "p25": q.get("p25"),
                "p75": q.get("p75"),
                "p90": q.get("p90"),
                "algo_type": r.get("model_type", "TFT"),
                "model_version": r.get("model_version", "2.0.0"),
            })

    if not forecasts:
        return

    try:
        resp = httpx.post(
            f"{_JAVA_BASE_URL}/api/python/callback/weekly",
            json={"forecasts": forecasts},
            headers={"X-Callback-Token": _CALLBACK_TOKEN},
            timeout=30.0,
            trust_env=False,
        )
        resp.raise_for_status()
        logger.info("预测结果已推送 Java: %d 条", len(forecasts))
    except Exception:
        logger.exception("推送 Java 失败，结果已在内存中，可重试")
