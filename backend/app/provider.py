from __future__ import annotations

from abc import ABC, abstractmethod
import base64
import json

from openai import (
    APIError,
    APITimeoutError,
    AsyncOpenAI,
    AuthenticationError,
    NotFoundError,
    PermissionDeniedError,
    RateLimitError,
)
from pydantic import ValidationError

from .config import Settings
from .errors import (
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
from .models import ProviderAnalysis


ANALYSIS_INSTRUCTIONS = """당신은 음식 사진의 대략적인 섭취량과 열량을 추정하는 분석기입니다.
사진에 실제로 보이는 음식만 분석하세요. 사람, 풍경, 문서, 빈 접시는 음식으로 처리하지 마세요.
음식인지 확인할 수 없으면 NO_FOOD를, 음식이 보이지만 구분이 어렵다면 UNCERTAIN을 반환하세요.
사진에 없는 반찬이나 음료를 추가하지 말고, 한 접시를 과도하게 여러 음식으로 나누지 마세요.
음식명은 요청 locale의 언어를 우선하세요. 불분명한 재료와 조리법은 assumptions에 적으세요.
열량은 측정값이 아닌 추정값이며, 근거가 있을 때만 최소/최대 범위를 함께 반환하세요.
최소값 <= 예상값 <= 최대값을 지키고 의료적 판단이나 식단 처방은 하지 마세요.
정밀한 확률 신뢰도를 만들지 말고 HIGH, MEDIUM, LOW 등급만 사용하세요.
구조화된 결과 외의 텍스트는 출력하지 마세요."""


class FoodAnalysisProvider(ABC):
    @property
    @abstractmethod
    def is_configured(self) -> bool:
        raise NotImplementedError

    @abstractmethod
    async def analyze(
        self,
        image_bytes: bytes,
        *,
        locale: str,
        timezone: str,
        captured_at: str,
    ) -> ProviderAnalysis:
        raise NotImplementedError


class OpenAIFoodAnalysisProvider(FoodAnalysisProvider):
    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._client = (
            AsyncOpenAI(
                api_key=settings.openai_api_key,
                timeout=settings.request_timeout_seconds,
                max_retries=settings.provider_max_retries,
            )
            if settings.provider_configured
            else None
        )

    @property
    def is_configured(self) -> bool:
        return self._client is not None

    async def analyze(
        self,
        image_bytes: bytes,
        *,
        locale: str,
        timezone: str,
        captured_at: str,
    ) -> ProviderAnalysis:
        if self._client is None or self._settings.openai_model is None:
            raise ProviderNotConfiguredError

        data_url = "data:image/jpeg;base64," + base64.b64encode(image_bytes).decode("ascii")
        context = (
            f"사용자 locale: {locale}\n"
            f"사용자 timezone: {timezone}\n"
            f"촬영 시각: {captured_at}"
        )
        schema = ProviderAnalysis.model_json_schema()
        try:
            response = await self._client.responses.create(
                model=self._settings.openai_model,
                instructions=ANALYSIS_INSTRUCTIONS,
                input=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "input_text", "text": context},
                            {"type": "input_image", "image_url": data_url, "detail": "high"},
                        ],
                    }
                ],
                text={
                    "format": {
                        "type": "json_schema",
                        "name": "food_photo_analysis",
                        "schema": schema,
                        "strict": True,
                    }
                },
                max_output_tokens=self._settings.max_output_tokens,
                store=False,
            )
            if _contains_refusal(response):
                raise ProviderRefusalError
            if response.status == "incomplete":
                raise ProviderIncompleteResponseError
            if response.status != "completed" or not response.output_text:
                raise ProviderInvalidResponseError
            return ProviderAnalysis.model_validate(json.loads(response.output_text))
        except (
            ProviderIncompleteResponseError,
            ProviderInvalidResponseError,
            ProviderRefusalError,
        ):
            raise
        except APITimeoutError as exc:
            raise ProviderTimeoutError from exc
        except RateLimitError as exc:
            raise ProviderRateLimitError from exc
        except AuthenticationError as exc:
            raise ProviderAuthenticationError from exc
        except (PermissionDeniedError, NotFoundError) as exc:
            raise ProviderModelAccessError from exc
        except (json.JSONDecodeError, ValidationError, TypeError, ValueError) as exc:
            raise ProviderInvalidResponseError from exc
        except APIError as exc:
            raise ProviderUpstreamError from exc


def _contains_refusal(response: object) -> bool:
    for item in getattr(response, "output", None) or []:
        for content in getattr(item, "content", None) or []:
            if getattr(content, "type", None) == "refusal":
                return True
    return False
