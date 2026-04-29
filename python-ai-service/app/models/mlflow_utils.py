"""MLflow 实验跟踪与模型注册工具"""
from __future__ import annotations

import logging
import os
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

EXPERIMENT_DEMAND = "demand-forecast"
EXPERIMENT_RUL = "rul-prediction"


def init_mlflow(tracking_uri: str | None = None) -> None:
    """初始化 MLflow，失败时回退到本地文件追踪。"""
    import mlflow

    uri = tracking_uri or os.getenv("MLFLOW_TRACKING_URI", "http://localhost:5000")
    try:
        mlflow.set_tracking_uri(uri)
        mlflow.get_experiment_by_name(EXPERIMENT_DEMAND)
        logger.info("MLflow 连接成功: %s", uri)
    except Exception:
        local_dir = Path("mlruns")
        local_dir.mkdir(exist_ok=True)
        mlflow.set_tracking_uri(local_dir.as_uri())
        logger.warning("MLflow 服务不可用，回退到本地追踪: %s", local_dir.resolve())


def log_training_run(
    experiment_name: str,
    run_name: str,
    params: dict[str, Any],
    metrics: dict[str, float],
    artifacts: list[str] | None = None,
    tags: dict[str, str] | None = None,
) -> str:
    """记录一次训练 run，返回 run_id。"""
    import mlflow

    mlflow.set_experiment(experiment_name)
    with mlflow.start_run(run_name=run_name, tags=tags or {}) as run:
        mlflow.log_params(params)
        mlflow.log_metrics(metrics)
        for path in artifacts or []:
            if Path(path).exists():
                mlflow.log_artifact(path)
        run_id = run.info.run_id
    logger.info("MLflow run 记录完成: %s / %s (run_id=%s)", experiment_name, run_name, run_id)
    return run_id


def register_model(run_id: str, model_uri: str, model_name: str) -> str:
    """注册模型到 MLflow Model Registry，返回版本号。"""
    from mlflow.tracking import MlflowClient

    client = MlflowClient()
    try:
        client.create_registered_model(model_name)
    except Exception:
        pass  # 模型已存在
    mv = client.create_model_version(
        name=model_name,
        source=model_uri,
        run_id=run_id,
    )
    logger.info("模型注册: %s v%s", model_name, mv.version)
    return mv.version


def get_latest_model_version(model_name: str, stage: str = "Production") -> str | None:
    """获取指定阶段最新模型版本的 URI。"""
    from mlflow.tracking import MlflowClient

    client = MlflowClient()
    try:
        versions = client.get_latest_versions(model_name, stages=[stage])
        if versions:
            return f"models:/{model_name}/{stage}"
    except Exception:
        logger.warning("获取模型版本失败: %s/%s", model_name, stage)
    return None
