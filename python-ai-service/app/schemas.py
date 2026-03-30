from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class RulPredictRequest(BaseModel):
    spare_part_id: int = Field(gt=0)


class RulPredictResponse(BaseModel):
    spare_part_id: int
    predicted_rul: float | None
    confidence_interval: list[float] | None
    alert: str
    alert_message: str
    top_features: dict[str, Any]
    data_quality: str
    timestamp: str


class ReplenishmentRequest(BaseModel):
    spare_part_ids: list[int] = Field(min_length=1)


class ReplenishmentItem(BaseModel):
    spare_part_id: int
    spare_part_name: str | None = None
    current_stock: float | int | None = None
    suggestion: dict[str, Any] | None = None
    suggested_qty: int | None = None
    suggested_date: str | None = None
    priority: str | None = None
    prediction_method: str | None = None
    predicted_demand: dict[str, Any] | None = None
    supplier: dict[str, Any] | None = None
    alert_message: str | None = None
    explanation: dict[str, Any] | None = None
    data_quality: str | None = None
    n_months_available: int | None = None
    error: str | None = None
    timestamp: str | None = None


class ErrorResponse(BaseModel):
    code: int
    message: str
