from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
from io import BytesIO
import warnings

from fastapi import UploadFile
from PIL import Image, ImageOps, UnidentifiedImageError

from .config import Settings
from .errors import FoodAnalysisError


ALLOWED_DECLARED_TYPES = {"image/jpeg", "image/png"}
ALLOWED_IMAGE_FORMATS = {"JPEG", "PNG"}


@dataclass(frozen=True)
class ProcessedImage:
    jpeg_bytes: bytes
    original_sha256: str
    mime_type: str = "image/jpeg"


async def read_and_process_image(upload: UploadFile, settings: Settings, request_id: str) -> ProcessedImage:
    declared_type = (upload.content_type or "").lower()
    if declared_type not in ALLOWED_DECLARED_TYPES:
        raise FoodAnalysisError(
            "UNSUPPORTED_IMAGE_TYPE",
            "JPEG 또는 PNG 사진만 업로드할 수 있습니다.",
            status_code=415,
            request_id=request_id,
        )

    raw = bytearray()
    while chunk := await upload.read(64 * 1024):
        raw.extend(chunk)
        if len(raw) > settings.max_image_bytes:
            raise FoodAnalysisError(
                "IMAGE_TOO_LARGE",
                "업로드한 이미지가 허용 크기를 초과했습니다.",
                status_code=413,
                request_id=request_id,
            )

    if not raw:
        raise FoodAnalysisError(
            "INVALID_IMAGE",
            "비어 있거나 손상된 이미지입니다.",
            status_code=400,
            request_id=request_id,
        )

    raw_bytes = bytes(raw)
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(raw_bytes)) as source:
                if source.format not in ALLOWED_IMAGE_FORMATS:
                    raise FoodAnalysisError(
                        "UNSUPPORTED_IMAGE_TYPE",
                        "JPEG 또는 PNG 사진만 업로드할 수 있습니다.",
                        status_code=415,
                        request_id=request_id,
                    )
                width, height = source.size
                if width <= 0 or height <= 0 or width * height > settings.max_image_pixels:
                    raise FoodAnalysisError(
                        "INVALID_IMAGE",
                        "이미지 해상도가 허용 범위를 벗어났습니다.",
                        status_code=400,
                        request_id=request_id,
                    )
                source.load()
                oriented = ImageOps.exif_transpose(source)
                normalized = oriented.convert("RGB")
                normalized.thumbnail(
                    (settings.max_image_dimension, settings.max_image_dimension),
                    Image.Resampling.LANCZOS,
                )
                output = BytesIO()
                normalized.save(output, format="JPEG", quality=88, optimize=True)
                normalized.close()
                if oriented is not source:
                    oriented.close()
    except FoodAnalysisError:
        raise
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError) as exc:
        raise FoodAnalysisError(
            "INVALID_IMAGE",
            "이미지를 해석할 수 없습니다.",
            status_code=400,
            request_id=request_id,
        ) from exc

    jpeg_bytes = output.getvalue()
    if len(jpeg_bytes) > settings.max_image_bytes:
        raise FoodAnalysisError(
            "IMAGE_TOO_LARGE",
            "처리한 이미지가 허용 크기를 초과했습니다.",
            status_code=413,
            request_id=request_id,
        )

    return ProcessedImage(
        jpeg_bytes=jpeg_bytes,
        original_sha256=sha256(raw_bytes).hexdigest(),
    )
