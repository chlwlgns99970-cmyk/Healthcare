from __future__ import annotations

import math
from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class AnalysisStatus(StrEnum):
    SUCCESS = "SUCCESS"
    NO_FOOD = "NO_FOOD"
    UNCERTAIN = "UNCERTAIN"


class ConfidenceLevel(StrEnum):
    HIGH = "HIGH"
    MEDIUM = "MEDIUM"
    LOW = "LOW"


class AmountUnit(StrEnum):
    SERVING = "인분"
    GRAM = "g"
    MILLILITER = "ml"
    PIECE = "개"
    SLICE = "조각"
    BOWL = "그릇"
    CUP = "컵"
    PLATE = "접시"
    PACK = "봉지"


class ProviderFoodItem(BaseModel):
    model_config = ConfigDict(extra="forbid", allow_inf_nan=False)

    foodName: str = Field(min_length=1, max_length=120)
    alternativeNames: list[str] = Field(max_length=5)
    estimatedAmount: float = Field(gt=0, le=10000)
    amountUnit: AmountUnit
    estimatedKcal: int = Field(gt=0, le=20000)
    minimumKcal: int | None = Field(ge=1, le=20000)
    maximumKcal: int | None = Field(ge=1, le=20000)
    confidenceLevel: ConfidenceLevel
    description: str = Field(min_length=1, max_length=300)
    assumptions: list[str] = Field(max_length=8)

    @field_validator("foodName", "description")
    @classmethod
    def text_must_not_be_blank(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("text must not be blank")
        return value

    @model_validator(mode="after")
    def validate_calorie_range(self) -> "ProviderFoodItem":
        if not math.isfinite(self.estimatedAmount):
            raise ValueError("estimatedAmount must be finite")
        if self.minimumKcal is not None and self.minimumKcal > self.estimatedKcal:
            raise ValueError("minimumKcal cannot exceed estimatedKcal")
        if self.maximumKcal is not None and self.maximumKcal < self.estimatedKcal:
            raise ValueError("maximumKcal cannot be below estimatedKcal")
        if (self.minimumKcal is None) != (self.maximumKcal is None):
            raise ValueError("calorie range must provide both bounds or neither")
        return self


class ProviderAnalysis(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: AnalysisStatus
    items: list[ProviderFoodItem]
    warnings: list[str] = Field(max_length=8)

    @model_validator(mode="after")
    def validate_status_and_items(self) -> "ProviderAnalysis":
        if self.status == AnalysisStatus.NO_FOOD and self.items:
            raise ValueError("NO_FOOD cannot include items")
        if self.status == AnalysisStatus.SUCCESS and not self.items:
            raise ValueError("SUCCESS must include at least one item")
        return self


class AnalysisItem(BaseModel):
    itemId: str
    foodName: str
    alternativeNames: list[str]
    estimatedAmount: float
    amountUnit: str
    estimatedKcal: int
    minimumKcal: int | None
    maximumKcal: int | None
    confidence: float | None = None
    confidenceLevel: ConfidenceLevel
    description: str
    assumptions: list[str]
    selected: bool = True


class FoodAnalysisResponse(BaseModel):
    analysisId: str
    requestId: str
    status: AnalysisStatus
    items: list[AnalysisItem]
    totalEstimatedKcal: int | None
    totalMinimumKcal: int | None
    totalMaximumKcal: int | None
    warnings: list[str]
    modelVersion: str
    analyzedAt: datetime


class ErrorDetail(BaseModel):
    code: str
    message: str
    retryable: bool
    requestId: str | None


class ErrorResponse(BaseModel):
    error: ErrorDetail
