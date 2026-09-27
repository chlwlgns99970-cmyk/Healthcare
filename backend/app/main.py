from __future__ import annotations

from datetime import datetime
import json
import logging
import re
import time
from typing import Annotated

from fastapi import FastAPI, File, Form, Request, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from .config import Settings, get_settings
from .errors import FoodAnalysisError
from .idempotency import IdempotencyStore, MemoryIdempotencyStore
from .image_processing import read_and_process_image
from .models import ErrorDetail, ErrorResponse, FoodAnalysisResponse
from .provider import FoodAnalysisProvider, OpenAIFoodAnalysisProvider
from .rate_limit import InMemoryRateLimiter
from .service import FoodAnalysisService


logger = logging.getLogger("food_photo_analysis")
_REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")


def _masked_request_id(request_id: str | None) -> str | None:
    if not request_id:
        return None
    return request_id[:8] + "…" if len(request_id) > 8 else request_id


def _log_result(request_id: str | None, status: str, started: float, error_code: str | None = None) -> None:
    logger.info(
        json.dumps(
            {
                "requestId": _masked_request_id(request_id),
                "status": status,
                "durationMs": round((time.monotonic() - started) * 1000),
                "errorCode": error_code,
            },
            ensure_ascii=False,
        )
    )


def _error_response(error: FoodAnalysisError) -> JSONResponse:
    body = ErrorResponse(
        error=ErrorDetail(
            code=error.code,
            message=error.message,
            retryable=error.retryable,
            requestId=error.request_id,
        )
    )
    return JSONResponse(status_code=error.status_code, content=body.model_dump(mode="json"))


def _validate_metadata(request_id: str, locale: str, timezone: str, captured_at: str) -> None:
    if not _REQUEST_ID_PATTERN.fullmatch(request_id):
        raise FoodAnalysisError(
            "INVALID_REQUEST",
            "requestId 형식이 올바르지 않습니다.",
            status_code=400,
            request_id=request_id[:128] or None,
        )
    if not 1 <= len(locale) <= 35 or not 1 <= len(timezone) <= 64:
        raise FoodAnalysisError(
            "INVALID_REQUEST",
            "locale 또는 timezone 형식이 올바르지 않습니다.",
            status_code=400,
            request_id=request_id,
        )
    try:
        parsed = datetime.fromisoformat(captured_at.replace("Z", "+00:00"))
    except ValueError as exc:
        raise FoodAnalysisError(
            "INVALID_REQUEST",
            "capturedAt은 ISO-8601 형식이어야 합니다.",
            status_code=400,
            request_id=request_id,
        ) from exc
    if parsed.tzinfo is None:
        raise FoodAnalysisError(
            "INVALID_REQUEST",
            "capturedAt에는 시간대가 포함되어야 합니다.",
            status_code=400,
            request_id=request_id,
        )


def create_app(
    settings: Settings | None = None,
    provider: FoodAnalysisProvider | None = None,
    idempotency_store: IdempotencyStore | None = None,
    rate_limiter: InMemoryRateLimiter | None = None,
) -> FastAPI:
    settings = settings or get_settings()
    provider = provider or OpenAIFoodAnalysisProvider(settings)
    idempotency_store = idempotency_store or MemoryIdempotencyStore(settings.idempotency_ttl_seconds)
    rate_limiter = rate_limiter or InMemoryRateLimiter(settings.rate_limit, settings.daily_request_limit)
    service = FoodAnalysisService(settings, provider, idempotency_store)

    application = FastAPI(
        title="Food Photo Analysis",
        version="1.0.0",
        docs_url="/docs" if settings.environment != "production" else None,
        redoc_url=None,
    )

    @application.exception_handler(FoodAnalysisError)
    async def food_analysis_error_handler(_: Request, exc: FoodAnalysisError) -> JSONResponse:
        return _error_response(exc)

    @application.exception_handler(RequestValidationError)
    async def validation_error_handler(_: Request, __: RequestValidationError) -> JSONResponse:
        return _error_response(
            FoodAnalysisError(
                "INVALID_REQUEST",
                "필수 요청 필드가 없거나 형식이 올바르지 않습니다.",
                status_code=400,
            )
        )

    @application.exception_handler(Exception)
    async def internal_error_handler(_: Request, exc: Exception) -> JSONResponse:
        logger.error("Unhandled food analysis error type=%s", type(exc).__name__)
        return _error_response(
            FoodAnalysisError(
                "INTERNAL_ERROR",
                "요청을 처리하지 못했습니다.",
                status_code=500,
                retryable=True,
            )
        )

    @application.get("/health")
    async def health() -> dict[str, object]:
        return {
            "status": "ok",
            "service": "food-photo-analysis",
            "providerConfigured": provider.is_configured,
        }

    @application.get("/ready")
    async def ready() -> JSONResponse:
        is_ready = provider.is_configured
        return JSONResponse(
            status_code=200 if is_ready else 503,
            content={
                "status": "ready" if is_ready else "not_ready",
                "service": "food-photo-analysis",
                "providerConfigured": is_ready,
            },
        )

    @application.post(
        "/food-photo/analyze",
        response_model=FoodAnalysisResponse,
        responses={
            400: {"model": ErrorResponse},
            409: {"model": ErrorResponse},
            413: {"model": ErrorResponse},
            415: {"model": ErrorResponse},
            422: {"model": ErrorResponse},
            429: {"model": ErrorResponse},
            500: {"model": ErrorResponse},
            502: {"model": ErrorResponse},
            503: {"model": ErrorResponse},
            504: {"model": ErrorResponse},
        },
    )
    async def analyze_food_photo(
        request: Request,
        image: Annotated[UploadFile, File()],
        request_id: Annotated[str, Form(alias="requestId")],
        locale: Annotated[str, Form()],
        timezone: Annotated[str, Form()],
        captured_at: Annotated[str, Form(alias="capturedAt")],
    ) -> FoodAnalysisResponse:
        started = time.monotonic()
        try:
            _validate_metadata(request_id, locale, timezone, captured_at)
            client_key = request.client.host if request.client else "unknown"
            rate_limiter.check(client_key, request_id)
            processed = await read_and_process_image(image, settings, request_id)
            result = await service.analyze(
                image_bytes=processed.jpeg_bytes,
                image_hash=processed.original_sha256,
                request_id=request_id,
                locale=locale,
                timezone=timezone,
                captured_at=captured_at,
            )
            _log_result(request_id, result.status.value, started)
            return result
        except FoodAnalysisError as exc:
            _log_result(request_id, "ERROR", started, exc.code)
            raise
        finally:
            await image.close()

    return application


app = create_app()
