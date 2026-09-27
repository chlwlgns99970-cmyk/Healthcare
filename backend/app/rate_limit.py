from __future__ import annotations

from collections import defaultdict, deque
from datetime import UTC, date, datetime
import re
import time

from .errors import FoodAnalysisError


_RATE_PATTERN = re.compile(r"^(\d+)/(second|minute|hour)$")
_WINDOW_SECONDS = {"second": 1.0, "minute": 60.0, "hour": 3600.0}


class InMemoryRateLimiter:
    def __init__(self, rate: str, daily_limit: int) -> None:
        match = _RATE_PATTERN.fullmatch(rate.strip())
        if match is None:
            raise ValueError("FOOD_ANALYSIS_RATE_LIMIT must look like 20/minute")
        self.limit = int(match.group(1))
        if self.limit < 1:
            raise ValueError("FOOD_ANALYSIS_RATE_LIMIT must be positive")
        self.window_seconds = _WINDOW_SECONDS[match.group(2)]
        self.daily_limit = daily_limit
        self._events: dict[str, deque[float]] = defaultdict(deque)
        self._daily_date: date = datetime.now(UTC).date()
        self._daily_count = 0

    def check(self, client_key: str, request_id: str | None) -> None:
        today = datetime.now(UTC).date()
        if today != self._daily_date:
            self._daily_date = today
            self._daily_count = 0
        if self._daily_count >= self.daily_limit:
            raise FoodAnalysisError(
                "RATE_LIMITED",
                "일일 분석 한도에 도달했습니다. 나중에 다시 시도해 주세요.",
                status_code=429,
                retryable=True,
                request_id=request_id,
            )

        now = time.monotonic()
        events = self._events[client_key]
        while events and events[0] <= now - self.window_seconds:
            events.popleft()
        if len(events) >= self.limit:
            raise FoodAnalysisError(
                "RATE_LIMITED",
                "분석 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
                status_code=429,
                retryable=True,
                request_id=request_id,
            )
        events.append(now)
        self._daily_count += 1

