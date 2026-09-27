from __future__ import annotations

from io import BytesIO

import pytest
from fastapi import UploadFile
from PIL import Image

from app.image_processing import read_and_process_image


@pytest.mark.asyncio
async def test_large_image_is_resized_and_metadata_is_removed(settings):
    original = BytesIO()
    exif = Image.Exif()
    exif[0x010E] = "private metadata"
    Image.new("RGB", (1200, 600), (10, 100, 30)).save(
        original,
        format="JPEG",
        quality=90,
        exif=exif,
    )
    upload = UploadFile(filename="meal.jpg", file=BytesIO(original.getvalue()), headers={"content-type": "image/jpeg"})

    result = await read_and_process_image(upload, settings, "image-test")
    with Image.open(BytesIO(result.jpeg_bytes)) as image:
        assert max(image.size) <= settings.max_image_dimension
        assert not image.getexif()


@pytest.mark.asyncio
async def test_actual_image_format_is_checked(settings, png_bytes):
    output = BytesIO()
    Image.open(BytesIO(png_bytes)).save(output, format="WEBP")
    upload = UploadFile(filename="fake.jpg", file=BytesIO(output.getvalue()), headers={"content-type": "image/jpeg"})

    with pytest.raises(Exception) as raised:
        await read_and_process_image(upload, settings, "format-test")
    assert getattr(raised.value, "code", None) == "UNSUPPORTED_IMAGE_TYPE"
