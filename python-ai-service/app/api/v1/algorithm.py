from __future__ import annotations

import logging
import os
import threading
from pathlib import Path
from typing import Any

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.models.demand_forecast import HurdleGammaModel

logger = logging.getLogger(__name__)

# 使用空前缀或特定前缀，按要求直接暴露为 /api/algorithm/...
router = APIRouter(prefix="/api/algorithm", tags=["algorithm"])

_MODEL_DIR = Path(os.getenv("MODEL_DIR", "/tmp/langdong_models"))
_MODEL_PATH = _MODEL_DIR / "algorithm_model"

# 懒加载单例模型
_model_instance = None
_model_lock = threading.Lock()

def _get_model():
    global _model_instance
    if _model_instance is None:
        with _model_lock:
            if _model_instance is None:
                if _MODEL_PATH.exists():
                    try:
                        _model_instance = HurdleGammaModel.load(_MODEL_PATH)
                        logger.info("已成功从本地磁盘加载 HurdleGammaModel 算法模型")
                    except Exception as e:
                        logger.warning("从本地磁盘加载算法模型失败，将在训练后重建: %s", e)
                        _model_instance = HurdleGammaModel()
                else:
                    _model_instance = HurdleGammaModel()
    return _model_instance


class TrainRequest(BaseModel):
    X: list[list[float]] = Field(..., description="特征矩阵 [N, D]")
    y: list[float] = Field(..., description="目标变量 (真实需求量) [N]")
    xyz_groups: list[str] = Field(..., description="样本对应的 XYZ 分组 [N]")


class TrainResponse(BaseModel):
    status: str
    message: str
    metrics: dict[str, Any]


class PredictRequest(BaseModel):
    X: list[list[float]] = Field(..., description="特征矩阵 [N, D]")
    xyz_groups: list[str] = Field(..., description="样本对应的 XYZ 分组 [N]")


class PredictItem(BaseModel):
    p_t: float = Field(..., description="需求发生概率 p_t")
    mu_t: float = Field(..., description="正需求的条件均值 mu_t")
    k: float = Field(..., description="形状参数 k")
    lower_bound: float = Field(..., description="90% 置信区间下界")
    upper_bound: float = Field(..., description="90% 置信区间上界")


class PredictResponse(BaseModel):
    predictions: list[PredictItem]


@router.post("/train", response_model=TrainResponse)
def train_algorithm(req: TrainRequest) -> TrainResponse:
    """
    接收特征矩阵和目标值进行模型训练，并按 XYZ 分组估计保存形状参数 k
    """
    try:
        model = _get_model()
        
        import numpy as np
        X_arr = np.array(req.X, dtype=float)
        y_arr = np.array(req.y, dtype=float)
        groups = np.array(req.xyz_groups)

        res = model.train(X_arr, y_arr, groups)
        
        # 将模型序列化保存到本地磁盘
        with _model_lock:
            model.save(_MODEL_PATH)
            
        return TrainResponse(
            status="success",
            message="模型及各 XYZ 分组的形状参数 k 训练并保存成功",
            metrics={
                "k_params": res["k_params"],
                "samples": res["samples"],
                "pos_samples": res["pos_samples"]
            }
        )
    except Exception as exc:
        logger.exception("算法模型训练失败")
        raise HTTPException(status_code=500, detail=f"Model training failed: {str(exc)}")


@router.post("/predict", response_model=PredictResponse)
def predict_algorithm(req: PredictRequest) -> PredictResponse:
    """
    使用训练好的模型预测特征的需求概率 p_t、正需求条件均值 mu_t、估计形状参数 k 以及 90% 置信区间
    """
    model = _get_model()
    if not model.is_fitted:
        raise HTTPException(status_code=400, detail="模型尚未训练，请先调用 /api/algorithm/train")

    try:
        import numpy as np
        X_arr = np.array(req.X, dtype=float)
        groups = np.array(req.xyz_groups)

        if len(X_arr) != len(groups):
            raise ValueError(f"X 和 xyz_groups 长度不匹配: X={len(X_arr)}, xyz_groups={len(groups)}")

        res = model.predict(X_arr, groups)
        
        pred_items = [
            PredictItem(
                p_t=item["p_t"],
                mu_t=item["mu_t"],
                k=item["k"],
                lower_bound=item["lower_bound"],
                upper_bound=item["upper_bound"]
            )
            for item in res
        ]
        
        return PredictResponse(predictions=pred_items)
    except Exception as exc:
        logger.exception("预测执行失败")
        raise HTTPException(status_code=500, detail=f"Prediction failed: {str(exc)}")


class InventoryCalcRequest(BaseModel):
    p_t: float = Field(..., description="需求发生概率")
    mu_t: float = Field(..., description="正需求预测均值")
    k: float = Field(..., description="形状参数 k")
    L: float = Field(..., description="采购提前期 (工作天数)")
    W: int = Field(22, description="月工作天数, 默认 22")
    M: int = Field(10000, description="模拟次数, 默认 10000")
    alpha: float = Field(..., description="目标服务水平 (如 0.95)")


class InventoryCalcResponse(BaseModel):
    rop: int = Field(..., description="补货点 ROP")
    ss: int = Field(..., description="安全库存 SS")
    mean_demand: float = Field(..., description="提前期平均需求量")


@router.post("/inventory-calc", response_model=InventoryCalcResponse)
def inventory_calc(req: InventoryCalcRequest) -> InventoryCalcResponse:
    """
    基于服务水平约束 (CSL) 的“工作日比例分配”蒙特卡洛提前期需求模拟计算，返回 ROP 和 SS。
    """
    try:
        from app.services.inventory_calc import simulate_lead_time_demand
        res = simulate_lead_time_demand(
            p_t=req.p_t,
            mu_t=req.mu_t,
            k=req.k,
            L=req.L,
            W=req.W,
            M=req.M,
            alpha=req.alpha
        )
        return InventoryCalcResponse(
            rop=res["rop"],
            ss=res["ss"],
            mean_demand=res["mean_demand"]
        )
    except Exception as exc:
        logger.exception("安全库存蒙特卡洛计算失败")
        raise HTTPException(status_code=500, detail=f"Inventory simulation failed: {str(exc)}")

