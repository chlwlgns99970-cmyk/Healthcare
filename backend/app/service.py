from __future__ import annotations

import asyncio
from datetime import UTC, datetime
from uuid import uuid4

from .config import Settings
from .errors import (
    FoodAnalysisError,
    ProviderAuthenticationError,
    ProviderIncompleteResponseError,
    ProviderInvalidResponseError,
    ProviderModelAccessError,
    ProviderNotConfiguredError,
    ProviderRateLimitError,
    ProviderRefusalError,
    ProviderTimeoutError,
    ProviderUpstreamError,
)
from .idempotency import IdempotencyStore
from .models import AnalysisItem, AnalysisStatus, FoodAnalysisResponse, ProviderAnalysis
from .provider import FoodAnalysisProvider


class FoodAnalysisService:
    def __init__(
        self,
        settings: Settings,
        provider: FoodAnalysisProvider,
        idempotency_store: IdempotencyStore,
    ) -> None:
        self._settings = settings
        self._provider = provider
        self._idempotency_store = idempotency_store

    async def analyze(
        self,
        *,
        image_bytes: bytes,
        image_hash: str,
        request_id: str,
        locale: str,
        timezone: str,
        captured_at: str,
    ) -> FoodAnalysisResponse:
        async def operation() -> FoodAnalysisResponse:
            try:
                async with asyncio.timeout(self._settings.request_timeout_seconds):
                    provider_result = await self._provider.analyze(
                        image_bytes,
                        locale=locale,
                        timezone=timezone,
                        captured_at=captured_at,
                    )
                return self._build_response(provider_result, request_id)
            except ProviderNotConfiguredError as exc:
                raise FoodAnalysisError(
                    "PROVIDER_NOT_CONFIGURED",
                    "사진 분석 공급자가 설정되지 않았습니다.",
                    status_code=503,
                    request_id=request_id,
                ) from exc
            except (ProviderTimeoutError, TimeoutError) as exc:
                raise FoodAnalysisError(
                    "UPSTREAM_TIMEOUT",
                    "사진 분석 시간이 초과되었습니다.",
                    status_code=504,
                    retryable=True,
                    request_id=request_id,
                ) from exc
            except ProviderRateLimitError as exc:
                raise FoodAnalysisError(
                    "UPSTREAM_RATE_LIMITED",
                    "분석 공급자 요청 한도에 도달했습니다.",
                    status_code=429,
                    retryable=True,
                    request_id=request_id,
                ) from exc
            except ProviderAuthenticationError as exc:
                raise FoodAnalysisError(
                    "UPSTREAM_AUTHENTICATION_FAILED",
                    "분석 공급자 인증 설정이 올바르지 않습니다.",
                    status_code=503,
                    request_id=request_id,
                ) from exc
            except ProviderModelAccessError as exc:
                raise FoodAnalysisError(
                    "UPSTREAM_MODEL_ACCESS_DENIED",
                    "설정된 분석 모델에 접근할 수 없습니다.",
                    status_code=503,
                    request_id=request_id,
                ) from exc
            except ProviderIncompleteResponseError as exc:
                raise FoodAnalysisError(
                    "UPSTREAM_INCOMPLETE_RESPONSE",
                    "분석 공급자의 응답이 완료되지 않았습니다.",
                    status_code=502,
                    retryable=True,
                    request_id=request_id,
                ) from exc
            except ProviderRefusalError as exc:
                raise FoodAnalysisError(
                    "UPSTREAM_REFUSED",
                    "분석 공급자가 이 사진의 분석을 완료하지 못했습니다.",
                    status_code=422,
                    request_id=request_id,
                ) from exc
            except ProviderInvalidResponseError as exc:
                raise FoodAnalysisError(
                    "UPSTREAM_INVALID_RESPONSE",
                    "분석 공급자가 유효한 결과를 반환하지 않았습니다.",
                    status_code=502,
                    retryable=True,
                    request_id=request_id,
                ) from exc
            except ProviderUpstreamError as exc:
                raise FoodAnalysisError(
                    "UPSTREAM_ERROR",
                    "분석 공급자 호출에 실패했습니다.",
                    status_code=502,
                    retryable=True,
                    request_id=request_id,
                ) from exc

        return await self._idempotency_store.execute(request_id, image_hash, operation)

    def _build_response(self, result: ProviderAnalysis, request_id: str) -> FoodAnalysisResponse:
        if len(result.items) > self._settings.max_food_items:
            raise FoodAnalysisError(
                "UPSTREAM_INVALID_RESPONSE",
                "분석 항목 수가 허용 범위를 초과했습니다.",
                status_code=502,
                retryable=True,
                request_id=request_id,
            )

        items = [
            AnalysisItem(
                itemId=f"item-{index + 1}",
                foodName=item.foodName.strip(),
                alternativeNames=[name.strip() for name in item.alternativeNames if name.strip()],
                estimatedAmount=item.estimatedAmount,
                amountUnit=item.amountUnit.value,
                estimatedKcal=item.estimatedKcal,
                minimumKcal=item.minimumKcal,
                maximumKcal=item.maximumKcal,
                confidence=None,
                confidenceLevel=item.confidenceLevel,
                description=item.description.strip(),
                assumptions=[value.strip() for value in item.assumptions if value.strip()],
            )
            for index, item in enumerate(result.items)
        ]
        total_estimated = sum(item.estimatedKcal for item in items) if items else None
        has_complete_range = bool(items) and all(
            item.minimumKcal is not None and item.maximumKcal is not None for item in items
        )
        total_minimum = sum(item.minimumKcal for item in items if item.minimumKcal is not None) if has_complete_range else None
        total_maximum = sum(item.maximumKcal for item in items if item.maximumKcal is not None) if has_complete_range else None

        return FoodAnalysisResponse(
            analysisId=str(uuid4()),
            requestId=request_id,
            status=result.status,
            items=items,
            totalEstimatedKcal=total_estimated,
            totalMinimumKcal=total_minimum,
            totalMaximumKcal=total_maximum,
            warnings=[warning.strip() for warning in result.warnings if warning.strip()],
            modelVersion=self._settings.openai_model or "unconfigured",
            analyzedAt=datetime.now(UTC),
        )
