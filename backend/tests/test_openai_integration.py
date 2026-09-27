from __future__ import annotations

import os
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.provider import OpenAIFoodAnalysisProvider
from tests.test_api import post_image


@pytest.mark.integration
def test_real_openai_food_photo_analysis():
    image_path = os.getenv("FOOD_ANALYSIS_INTEGRATION_IMAGE")
    api_key = os.getenv("OPENAI_API_KEY")
    model = os.getenv("OPENAI_MODEL")
    if not image_path or not api_key or not model:
        pytest.skip("OPENAI_API_KEY, OPENAI_MODEL, FOOD_ANALYSIS_INTEGRATION_IMAGE are required")

    path = Path(image_path)
    if not path.is_file():
        pytest.skip("FOOD_ANALYSIS_INTEGRATION_IMAGE does not point to a file")
    settings = Settings(openai_api_key=api_key, openai_model=model, environment="integration")
    response = post_image(
        TestClient(create_app(settings, OpenAIFoodAnalysisProvider(settings))),
        path.read_bytes(),
        request_id="integration-request",
        mime="image/jpeg",
    )
    assert response.status_code == 200, response.text
    assert response.json()["status"] in {"SUCCESS", "NO_FOOD", "UNCERTAIN"}

