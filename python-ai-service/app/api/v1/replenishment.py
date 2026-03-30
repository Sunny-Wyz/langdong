from __future__ import annotations

from fastapi import APIRouter, HTTPException
import logging

from app.schemas import ReplenishmentItem, ReplenishmentRequest
from app.services.legacy_bridge import suggest_replenishment

router = APIRouter(prefix="/api/v1", tags=["replenishment"])
logger = logging.getLogger(__name__)


@router.post("/replenishment/suggest", response_model=list[ReplenishmentItem])
def suggest_replenishment_endpoint(request: ReplenishmentRequest) -> list[ReplenishmentItem]:
    try:
        payload = suggest_replenishment(request.spare_part_ids)
        return [ReplenishmentItem(**item) for item in payload]
    except Exception as exc:
        logger.exception("Replenishment suggestion failed for ids=%s", request.spare_part_ids)
        raise HTTPException(status_code=500, detail="AI service temporarily unavailable") from exc
