from __future__ import annotations

import asyncio
from abc import ABC, abstractmethod
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
import time

from .errors import FoodAnalysisError
from .models import FoodAnalysisResponse


@dataclass(frozen=True)
class IdempotencyEntry:
    image_hash: str
    response: FoodAnalysisResponse
    expires_at: float


class IdempotencyStore(ABC):
    @abstractmethod
    async def execute(
        self,
        request_id: str,
        image_hash: str,
        operation: Callable[[], Awaitable[FoodAnalysisResponse]],
    ) -> FoodAnalysisResponse:
        raise NotImplementedError


class MemoryIdempotencyStore(IdempotencyStore):
    """Development/test store. Use a shared persistent implementation in multi-instance production."""

    def __init__(self, ttl_seconds: int) -> None:
        self._ttl_seconds = ttl_seconds
        self._entries: dict[str, IdempotencyEntry] = {}
        self._locks: dict[str, asyncio.Lock] = {}
        self._guard = asyncio.Lock()

    async def execute(
        self,
        request_id: str,
        image_hash: str,
        operation: Callable[[], Awaitable[FoodAnalysisResponse]],
    ) -> FoodAnalysisResponse:
        async with self._guard:
            now = time.monotonic()
            expired_keys = [key for key, entry in self._entries.items() if entry.expires_at <= now]
            for key in expired_keys:
                self._entries.pop(key, None)
                lock = self._locks.get(key)
                if lock is not None and not lock.locked():
                    self._locks.pop(key, None)
            lock = self._locks.setdefault(request_id, asyncio.Lock())

        async with lock:
            now = time.monotonic()
            entry = self._entries.get(request_id)
            if entry is not None and entry.expires_at <= now:
                self._entries.pop(request_id, None)
                entry = None
            if entry is not None:
                if entry.image_hash != image_hash:
                    raise FoodAnalysisError(
                        "REQUEST_ID_CONFLICT",
                        "같은 requestId에 다른 이미지가 전송되었습니다.",
                        status_code=409,
                        request_id=request_id,
                    )
                return entry.response

            response = await operation()
            self._entries[request_id] = IdempotencyEntry(
                image_hash=image_hash,
                response=response,
                expires_at=now + self._ttl_seconds,
            )
            return response
