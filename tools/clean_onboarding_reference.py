"""Remove the baked-in onboarding copy before super-resolution.

The reference crop contains Korean copy rasterized into the photograph. Keeping
that copy and enlarging the PNG makes the first-run screen look soft even though
the rest of the app uses native Compose text. This tool removes only the dark
letter pixels inside the known copy bounds; the photograph, crop, and geometry
outside those bounds are left untouched. The cleaned crop is then passed to the
same content-preserving EDSR pipeline as the other reference assets.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import cv2
import numpy as np


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--mask-output", type=Path)
    args = parser.parse_args()

    image = cv2.imread(str(args.input), cv2.IMREAD_COLOR)
    if image is None:
        raise SystemExit(f"Unable to read {args.input}")

    height, width = image.shape[:2]
    if (width, height) != (250, 259):
        raise SystemExit(f"Unexpected onboarding source size: {width}x{height}")

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    mask = np.zeros((height, width), dtype=np.uint8)

    # The two rectangles tightly enclose the headline and supporting copy in the
    # approved 250x259 crop. Thresholding avoids masking the warm wall texture.
    for x1, y1, x2, y2, threshold in (
        (17, 27, 165, 129, 225),
        (18, 137, 165, 181, 225),
    ):
        region = gray[y1:y2, x1:x2]
        mask[y1:y2, x1:x2] = np.where(region < threshold, 255, 0).astype(np.uint8)

    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    mask = cv2.dilate(mask, kernel, iterations=4)

    # A wide median estimate recreates the slowly varying plaster wall without
    # the bright halos that generic inpainting can leave around Hangul strokes.
    # Feathering only the masked letter pixels keeps the architecture untouched.
    wall_estimate = cv2.medianBlur(image, 51)
    alpha = cv2.GaussianBlur(mask, (7, 7), 0).astype(np.float32) / 255.0
    alpha = alpha[:, :, None]
    cleaned = np.clip(
        image.astype(np.float32) * (1.0 - alpha)
        + wall_estimate.astype(np.float32) * alpha,
        0,
        255,
    ).astype(np.uint8)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(args.output), cleaned, [cv2.IMWRITE_PNG_COMPRESSION, 9]):
        raise SystemExit(f"Unable to write {args.output}")
    if args.mask_output:
        args.mask_output.parent.mkdir(parents=True, exist_ok=True)
        cv2.imwrite(str(args.mask_output), mask)


if __name__ == "__main__":
    main()
