from __future__ import annotations

import logging
import os

import redis

logger = logging.getLogger(__name__)

_REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")
_TTL_SECONDS = 86400  # 24小时自动过期

_redis_client: redis.Redis | None = None


def _get_redis() -> redis.Redis:
    global _redis_client
    if _redis_client is None:
        _redis_client = redis.Redis.from_url(_REDIS_URL, decode_responses=True)
    return _redis_client


def _key(task_id: str) -> str:
    return f"ai:task:submitted:{task_id}"


def register_task(task_id: str) -> None:
    try:
        _get_redis().setex(_key(task_id), _TTL_SECONDS, "1")
    except redis.RedisError:
        logger.exception("Failed to register task %s in Redis", task_id)


def has_task(task_id: str) -> bool:
    try:
        return _get_redis().exists(_key(task_id)) > 0
    except redis.RedisError:
        logger.exception("Failed to check task %s in Redis", task_id)
        return False
