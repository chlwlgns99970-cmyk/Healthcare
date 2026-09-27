from __future__ import annotations

from functools import lru_cache

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )

    openai_api_key: str | None = Field(default=None, alias="OPENAI_API_KEY")
    openai_model: str | None = Field(default=None, alias="OPENAI_MODEL")
    request_timeout_seconds: float = Field(
        default=45.0,
        gt=0,
        le=180,
        alias="FOOD_ANALYSIS_REQUEST_TIMEOUT_SECONDS",
    )
    max_image_bytes: int = Field(
        default=5 * 1024 * 1024,
        ge=64 * 1024,
        le=20 * 1024 * 1024,
        alias="FOOD_ANALYSIS_MAX_IMAGE_BYTES",
    )
    max_image_dimension: int = Field(
        default=1600,
        ge=256,
        le=4096,
        alias="FOOD_ANALYSIS_MAX_IMAGE_DIMENSION",
    )
    max_image_pixels: int = Field(
        default=20_000_000,
        ge=1_000_000,
        le=80_000_000,
        alias="FOOD_ANALYSIS_MAX_IMAGE_PIXELS",
    )
    rate_limit: str = Field(default="20/minute", alias="FOOD_ANALYSIS_RATE_LIMIT")
    daily_request_limit: int = Field(
        default=1000,
        ge=1,
        alias="FOOD_ANALYSIS_DAILY_REQUEST_LIMIT",
    )
    environment: str = Field(default="development", alias="FOOD_ANALYSIS_ENVIRONMENT")
    idempotency_ttl_seconds: int = Field(
        default=600,
        ge=30,
        le=86400,
        alias="FOOD_ANALYSIS_IDEMPOTENCY_TTL_SECONDS",
    )
    provider_max_retries: int = Field(
        default=1,
        ge=0,
        le=2,
        alias="FOOD_ANALYSIS_PROVIDER_MAX_RETRIES",
    )
    max_output_tokens: int = Field(
        default=1800,
        ge=500,
        le=4000,
        alias="FOOD_ANALYSIS_MAX_OUTPUT_TOKENS",
    )
    max_food_items: int = Field(
        default=12,
        ge=1,
        le=30,
        alias="FOOD_ANALYSIS_MAX_ITEMS",
    )

    @field_validator("openai_api_key", "openai_model", mode="before")
    @classmethod
    def empty_string_is_none(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None
        return value

    @property
    def provider_configured(self) -> bool:
        return bool(self.openai_api_key and self.openai_model)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
