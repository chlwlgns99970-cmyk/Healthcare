from __future__ import annotations

import json

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.errors import (
    ProviderAuthenticationError,
    ProviderIncompleteResponseError,
    ProviderInvalidResponseError,
    ProviderModelAccessError,
    ProviderRateLimitError,
    ProviderRefusalError,
    ProviderTimeoutError,
    ProviderUpstreamError,
)
from app.main import create_app
from app.models import AnalysisStatus, ProviderAnalysis, ProviderFoodItem
from app.rate_limit import InMemoryRateLimiter
from tests.fakes import FakeProvider, food_item


def post_image(client: TestClient, image: bytes, request_id: str = "request-1", mime: str = "image/png"):
    return client.post(
        "/food-photo/analyze",
        files={"image": ("meal.png", image, mime)},
        data={
            "requestId": request_id,
            "locale": "ko-KR",
            "timezone": "Asia/Seoul",
            "capturedAt": "2026-09-12T12:00:00+09:00",
        },
    )


def test_health_reports_only_safe_configuration_state(settings):
    response = TestClient(create_app(settings, FakeProvider())).get("/health")
    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "food-photo-analysis",
        "providerConfigured": True,
    }
    assert settings.openai_api_key not in response.text


@pytest.mark.parametrize(
    ("configured", "status_code", "status"),
    [(True, 200, "ready"), (False, 503, "not_ready")],
)
def test_readiness_reflects_provider_configuration(settings, configured, status_code, status):
    response = TestClient(create_app(settings, FakeProvider(configured=configured))).get("/ready")
    assert response.status_code == status_code
    assert response.json() == {
        "status": status,
        "service": "food-photo-analysis",
        "providerConfigured": configured,
    }
    assert settings.openai_api_key not in response.text


def test_provider_not_configured_is_explicit(settings, png_bytes):
    response = post_image(TestClient(create_app(settings, FakeProvider(configured=False))), png_bytes)
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "PROVIDER_NOT_CONFIGURED"


def test_single_food_analysis(settings, png_bytes):
    response = post_image(TestClient(create_app(settings, FakeProvider())), png_bytes)
    body = response.json()
    assert response.status_code == 200
    assert body["status"] == "SUCCESS"
    assert body["items"][0]["foodName"] == "비빔밥"
    assert body["items"][0]["confidence"] is None
    assert body["totalEstimatedKcal"] == 610


def test_multiple_foods_and_totals_are_recomputed(settings, png_bytes):
    provider = FakeProvider(
        ProviderAnalysis(
            status=AnalysisStatus.SUCCESS,
            items=[food_item(kcal=610), food_item("김치", 30, 20, 50)],
            warnings=[],
        )
    )
    body = post_image(TestClient(create_app(settings, provider)), png_bytes).json()
    assert len(body["items"]) == 2
    assert body["totalEstimatedKcal"] == 640
    assert body["totalMinimumKcal"] == 520
    assert body["totalMaximumKcal"] == 800


def test_missing_one_range_does_not_invent_aggregate_range(settings, png_bytes):
    provider = FakeProvider(
        ProviderAnalysis(
            status=AnalysisStatus.SUCCESS,
            items=[food_item(), food_item("김치", 30, None, None)],
            warnings=[],
        )
    )
    body = post_image(TestClient(create_app(settings, provider)), png_bytes).json()
    assert body["totalMinimumKcal"] is None
    assert body["totalMaximumKcal"] is None


def test_no_food_does_not_generate_calories(settings, png_bytes):
    provider = FakeProvider(ProviderAnalysis(status=AnalysisStatus.NO_FOOD, items=[], warnings=["음식을 찾지 못했습니다."]))
    body = post_image(TestClient(create_app(settings, provider)), png_bytes).json()
    assert body["status"] == "NO_FOOD"
    assert body["items"] == []
    assert body["totalEstimatedKcal"] is None


def test_uncertain_status_is_preserved(settings, png_bytes):
    provider = FakeProvider(ProviderAnalysis(status=AnalysisStatus.UNCERTAIN, items=[food_item()], warnings=["구분이 어렵습니다."]))
    body = post_image(TestClient(create_app(settings, provider)), png_bytes).json()
    assert body["status"] == "UNCERTAIN"


@pytest.mark.parametrize("payload", [b"not-an-image", b""])
def test_invalid_or_damaged_image_is_rejected(settings, payload):
    response = post_image(TestClient(create_app(settings, FakeProvider())), payload)
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_IMAGE"


def test_unsupported_declared_type_is_rejected(settings, png_bytes):
    response = post_image(TestClient(create_app(settings, FakeProvider())), png_bytes, mime="image/webp")
    assert response.status_code == 415
    assert response.json()["error"]["code"] == "UNSUPPORTED_IMAGE_TYPE"


def test_maximum_size_is_enforced(settings):
    settings.max_image_bytes = 65_536
    response = post_image(TestClient(create_app(settings, FakeProvider())), b"x" * 65_537, mime="image/jpeg")
    assert response.status_code == 413
    assert response.json()["error"]["code"] == "IMAGE_TOO_LARGE"


@pytest.mark.parametrize(
    ("error", "status", "code"),
    [
        (ProviderTimeoutError(), 504, "UPSTREAM_TIMEOUT"),
        (ProviderRateLimitError(), 429, "UPSTREAM_RATE_LIMITED"),
        (ProviderAuthenticationError(), 503, "UPSTREAM_AUTHENTICATION_FAILED"),
        (ProviderModelAccessError(), 503, "UPSTREAM_MODEL_ACCESS_DENIED"),
        (ProviderIncompleteResponseError(), 502, "UPSTREAM_INCOMPLETE_RESPONSE"),
        (ProviderRefusalError(), 422, "UPSTREAM_REFUSED"),
        (ProviderInvalidResponseError(), 502, "UPSTREAM_INVALID_RESPONSE"),
        (ProviderUpstreamError(), 502, "UPSTREAM_ERROR"),
    ],
)
def test_provider_errors_are_sanitized(settings, png_bytes, error, status, code):
    response = post_image(TestClient(create_app(settings, FakeProvider(error=error))), png_bytes)
    assert response.status_code == status
    assert response.json()["error"]["code"] == code
    assert "Traceback" not in response.text
    assert settings.openai_api_key not in response.text


def test_invalid_calorie_range_is_rejected_by_schema():
    data = food_item().model_dump()
    data["minimumKcal"] = 700
    with pytest.raises(ValidationError):
        ProviderFoodItem.model_validate(data)


def test_same_request_and_image_returns_cached_result(settings, png_bytes):
    provider = FakeProvider()
    client = TestClient(create_app(settings, provider))
    first = post_image(client, png_bytes, "same-request")
    second = post_image(client, png_bytes, "same-request")
    assert first.json() == second.json()
    assert provider.calls == 1


def test_same_request_with_different_image_conflicts(settings, png_bytes):
    provider = FakeProvider()
    client = TestClient(create_app(settings, provider))
    assert post_image(client, png_bytes, "conflict-request").status_code == 200
    altered = bytearray(png_bytes)
    altered[-8] ^= 1
    response = post_image(client, bytes(altered), "conflict-request")
    assert response.status_code == 409
    assert response.json()["error"]["code"] == "REQUEST_ID_CONFLICT"
    assert provider.calls == 1


def test_rate_limit_is_applied_before_provider_call(settings, png_bytes):
    provider = FakeProvider()
    limiter = InMemoryRateLimiter("1/hour", 10)
    client = TestClient(create_app(settings, provider, rate_limiter=limiter))
    assert post_image(client, png_bytes, "rate-1").status_code == 200
    response = post_image(client, png_bytes, "rate-2")
    assert response.status_code == 429
    assert response.json()["error"]["code"] == "RATE_LIMITED"
    assert provider.calls == 1


def test_required_form_field_validation_uses_standard_error(settings, png_bytes):
    client = TestClient(create_app(settings, FakeProvider()))
    response = client.post("/food-photo/analyze", files={"image": ("meal.png", png_bytes, "image/png")})
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_REQUEST"


def test_logs_never_contain_image_food_result_or_key(settings, png_bytes, caplog):
    provider = FakeProvider()
    caplog.set_level("INFO", logger="food_photo_analysis")
    response = post_image(TestClient(create_app(settings, provider)), png_bytes, "private-request-id")
    assert response.status_code == 200
    logs = caplog.text
    assert "비빔밥" not in logs
    assert settings.openai_api_key not in logs
    assert png_bytes.hex()[:40] not in logs
    event = json.loads(caplog.records[-1].message)
    assert event["requestId"] == "private-…"
