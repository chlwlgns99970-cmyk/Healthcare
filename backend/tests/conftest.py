from __future__ import annotations

from io import BytesIO

import pytest
from PIL import Image

from app.config import Settings


@pytest.fixture
def settings() -> Settings:
    return Settings(
        openai_api_key="test-secret-key",
        openai_model="test-vision-model",
        request_timeout_seconds=1,
        max_image_bytes=256_000,
        max_image_dimension=512,
        max_image_pixels=2_000_000,
        rate_limit="100/minute",
        daily_request_limit=1000,
        environment="test",
        idempotency_ttl_seconds=600,
        provider_max_retries=0,
        max_output_tokens=1000,
        max_food_items=12,
    )


@pytest.fixture
def png_bytes() -> bytes:
    output = BytesIO()
    Image.new("RGB", (32, 24), (180, 70, 30)).save(output, format="PNG")
    return output.getvalue()

