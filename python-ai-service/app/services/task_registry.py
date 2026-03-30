from __future__ import annotations

from collections import deque

_MAX_TASKS = 2000
_registry: set[str] = set()
_order = deque()


def register_task(task_id: str) -> None:
    if task_id in _registry:
        return

    _registry.add(task_id)
    _order.append(task_id)

    while len(_registry) > _MAX_TASKS:
        oldest = _order.popleft()
        _registry.discard(oldest)


def has_task(task_id: str) -> bool:
    return task_id in _registry
