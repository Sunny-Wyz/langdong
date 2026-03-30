from __future__ import annotations

from fastapi import APIRouter, HTTPException
import logging

from app.schemas import RulPredictRequest, RulPredictResponse
from app.services.legacy_bridge import predict_rul

router = APIRouter(prefix="/api/v1", tags=["maintenance"])
logger = logging.getLogger(__name__)


@router.post("/rul/predict", response_model=RulPredictResponse)
def predict_rul_endpoint(request: RulPredictRequest) -> RulPredictResponse:
    try:
        payload = predict_rul(request.spare_part_id)
        return RulPredictResponse(**payload)
    except Exception as exc:
        logger.exception("RUL prediction failed for spare_part_id=%s", request.spare_part_id)
        raise HTTPException(status_code=500, detail="AI service temporarily unavailable") from exc
