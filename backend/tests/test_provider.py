from __future__ import annotations

import json
from types import SimpleNamespace

import pytest

from app.errors import ProviderIncompleteResponseError, ProviderRefusalError
from app.provider import OpenAIFoodAnalysisProvider


class FakeResponses:
    def __init__(self, response=None) -> None:
        self.arguments = None
        self.response = response

    async def create(self, **kwargs):
        self.arguments = kwargs
        if self.response is not None:
            return self.response
        output = {
            "status": "SUCCESS",
            "items": [
                {
                    "foodName": "비빔밥",
                    "alternativeNames": [],
                    "estimatedAmount": 1,
                    "amountUnit": "인분",
                    "estimatedKcal": 610,
                    "minimumKcal": 500,
                    "maximumKcal": 750,
                    "confidenceLevel": "MEDIUM",
                    "description": "일반적인 1인분 기준 추정",
                    "assumptions": ["일반적인 조리법으로 추정"],
                }
            ],
            "warnings": [],
        }
        return SimpleNamespace(status="completed", output_text=json.dumps(output, ensure_ascii=False))


@pytest.mark.asyncio
async def test_openai_provider_uses_private_data_url_strict_schema_and_no_storage(settings):
    provider = OpenAIFoodAnalysisProvider(settings)
    responses = FakeResponses()
    provider._client = SimpleNamespace(responses=responses)

    result = await provider.analyze(
        b"sanitized-image",
        locale="ko-KR",
        timezone="Asia/Seoul",
        captured_at="2026-09-12T12:00:00+09:00",
    )

    arguments = responses.arguments
    assert arguments["model"] == settings.openai_model
    assert arguments["store"] is False
    assert arguments["text"]["format"]["type"] == "json_schema"
    assert arguments["text"]["format"]["strict"] is True
    image_url = arguments["input"][0]["content"][1]["image_url"]
    assert image_url.startswith("data:image/jpeg;base64,")
    assert not image_url.startswith("http")
    assert result.items[0].foodName == "비빔밥"


@pytest.mark.asyncio
async def test_openai_provider_maps_incomplete_response(settings):
    provider = OpenAIFoodAnalysisProvider(settings)
    provider._client = SimpleNamespace(
        responses=FakeResponses(SimpleNamespace(status="incomplete", output_text="", output=[]))
    )

    with pytest.raises(ProviderIncompleteResponseError):
        await provider.analyze(b"image", locale="ko-KR", timezone="Asia/Seoul", captured_at="now")


@pytest.mark.asyncio
async def test_openai_provider_maps_refusal_response(settings):
    refusal = SimpleNamespace(type="refusal", refusal="cannot analyze")
    output = [SimpleNamespace(content=[refusal])]
    provider = OpenAIFoodAnalysisProvider(settings)
    provider._client = SimpleNamespace(
        responses=FakeResponses(SimpleNamespace(status="completed", output_text="", output=output))
    )

    with pytest.raises(ProviderRefusalError):
        await provider.analyze(b"image", locale="ko-KR", timezone="Asia/Seoul", captured_at="now")
