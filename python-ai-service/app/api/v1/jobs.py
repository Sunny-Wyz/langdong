from __future__ import annotations

from celery.result import AsyncResult
from fastapi import APIRouter, HTTPException

from app.schemas import AsyncJobCreateResponse, AsyncJobStatusResponse, ReplenishmentRequest
from app.services.async_tasks import run_replenishment_job
from app.services.celery_app import celery_app
from app.services.task_registry import has_task, register_task

router = APIRouter(prefix="/api/v1/jobs", tags=["jobs"])


@router.post("/replenishment", response_model=AsyncJobCreateResponse)
def submit_replenishment_job(request: ReplenishmentRequest) -> AsyncJobCreateResponse:
    task = run_replenishment_job.delay(request.spare_part_ids)
    register_task(task.id)
    return AsyncJobCreateResponse(task_id=task.id, status="PENDING")


@router.get("/{task_id}", response_model=AsyncJobStatusResponse)
def get_job_status(task_id: str) -> AsyncJobStatusResponse:
    if not has_task(task_id):
        raise HTTPException(status_code=404, detail="Task not found")

    result = AsyncResult(task_id, app=celery_app)

    status = result.status

    if status == "SUCCESS":
        payload = result.result if isinstance(result.result, dict) else {"result": result.result}
        return AsyncJobStatusResponse(task_id=task_id, status=status, payload=payload)

    if status == "FAILURE":
        return AsyncJobStatusResponse(task_id=task_id, status=status, error="TASK_FAILED")

    return AsyncJobStatusResponse(task_id=task_id, status=status)
