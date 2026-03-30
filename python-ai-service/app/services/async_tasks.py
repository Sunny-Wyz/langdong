from __future__ import annotations

import logging
import os
from typing import Any

import httpx

from app.services.celery_app import celery_app
from app.services.legacy_bridge import suggest_replenishment

logger = logging.getLogger(__name__)


def _send_callback(callback_url: str, callback_token: str, payload: dict[str, Any]) -> None:
    resp = httpx.post(
        callback_url,
        json=payload,
        headers={"X-Callback-Token": callback_token},
        timeout=20.0,
    )
    resp.raise_for_status()


@celery_app.task(name="ai.replenishment.forecast", bind=True)
def run_replenishment_job(self, spare_part_ids: list[int]) -> dict[str, Any]:
    callback_url = os.getenv(
        "JAVA_CALLBACK_URL",
        "http://localhost:8080/api/ai/forecast/callback/python/replenishment",
    )
    callback_token = os.getenv("JAVA_CALLBACK_TOKEN")
    if not callback_token:
        raise RuntimeError("JAVA_CALLBACK_TOKEN is required")

    try:
        results = suggest_replenishment(spare_part_ids)

        payload = {
            "task_id": self.request.id,
            "status": "SUCCESS",
            "result": results,
        }

        try:
            _send_callback(callback_url, callback_token, payload)
        except Exception as cb_exc:
            logger.exception("Callback to Java failed for task_id=%s", self.request.id)
            raise self.retry(exc=cb_exc, countdown=5, max_retries=3)

        return payload
    except Exception as exc:
        logger.exception("Async replenishment task failed. task_id=%s", self.request.id)
        payload = {
            "task_id": self.request.id,
            "status": "FAILURE",
            "error": "TASK_FAILED",
        }
        try:
            _send_callback(callback_url, callback_token, payload)
        except Exception:
            logger.exception("Failure callback to Java failed for task_id=%s", self.request.id)
        raise
