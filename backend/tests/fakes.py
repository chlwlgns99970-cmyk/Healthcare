from __future__ import annotations

from app.models import (
    AmountUnit,
    AnalysisStatus,
    ConfidenceLevel,
    ProviderAnalysis,
    ProviderFoodItem,
)
from app.provider import FoodAnalysisProvider


def food_item(
    name: str = "비빔밥",
    kcal: int = 610,
    minimum: int | None = 500,
    maximum: int | None = 750,
) -> ProviderFoodItem:
    return ProviderFoodItem(
        foodName=name,
        alternativeNames=[],
        estimatedAmount=1,
        amountUnit=AmountUnit.SERVING,
        estimatedKcal=kcal,
        minimumKcal=minimum,
        maximumKcal=maximum,
        confidenceLevel=ConfidenceLevel.MEDIUM,
        description="일반적인 1인분 기준 추정",
        assumptions=["일반적인 조리법으로 추정"],
    )


class FakeProvider(FoodAnalysisProvider):
    def __init__(
        self,
        result: ProviderAnalysis | None = None,
        *,
        configured: bool = True,
        error: Exception | None = None,
    ) -> None:
        self.result = result or ProviderAnalysis(
            status=AnalysisStatus.SUCCESS,
            items=[food_item()],
            warnings=["사진만으로 정확한 섭취량을 확인하기 어렵습니다."],
        )
        self.configured = configured
        self.error = error
        self.calls = 0

    @property
    def is_configured(self) -> bool:
        return self.configured

    async def analyze(
        self,
        image_bytes: bytes,
        *,
        locale: str,
        timezone: str,
        captured_at: str,
    ) -> ProviderAnalysis:
        self.calls += 1
        if not self.configured:
            from app.errors import ProviderNotConfiguredError

            raise ProviderNotConfiguredError
        if self.error is not None:
            raise self.error
        return self.result

