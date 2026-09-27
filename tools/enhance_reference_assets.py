"""Create content-preserving reference asset candidates with OpenCV EDSR.

This is a build-time audit tool only. It never runs in the Android app and writes
to an explicit output directory so the checked-in source assets are not replaced
until the results have been visually reviewed.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import cv2
import numpy as np


def upscale(sr: cv2.dnn_superres.DnnSuperResImpl, source: Path, output: Path) -> tuple[int, int, float]:
    image = cv2.imread(str(source), cv2.IMREAD_UNCHANGED)
    if image is None:
        raise ValueError(f"Unable to read {source}")

    if image.ndim == 3 and image.shape[2] == 4:
        color = image[:, :, :3]
        alpha = image[:, :, 3]
    else:
        color = image
        alpha = None

    enhanced_4x = sr.upsample(color)
    target_size = (image.shape[1] * 6, image.shape[0] * 6)
    enhanced = cv2.resize(enhanced_4x, target_size, interpolation=cv2.INTER_LANCZOS4)

    if alpha is not None:
        alpha_6x = cv2.resize(alpha, target_size, interpolation=cv2.INTER_LANCZOS4)
        enhanced = np.dstack((enhanced, alpha_6x))

    output.parent.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(output), enhanced, [cv2.IMWRITE_PNG_COMPRESSION, 9]):
        raise ValueError(f"Unable to write {output}")

    downsampled = cv2.resize(enhanced[:, :, :3], (image.shape[1], image.shape[0]), interpolation=cv2.INTER_AREA)
    original_color = color[:, :, :3]
    mean_error = float(np.abs(downsampled.astype(np.float32) - original_color.astype(np.float32)).mean())
    return enhanced.shape[1], enhanced.shape[0], mean_error


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, type=Path)
    parser.add_argument("--input-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()

    sr = cv2.dnn_superres.DnnSuperResImpl_create()
    sr.readModel(str(args.model))
    sr.setModel("edsr", 4)

    sources = sorted(
        path for path in args.input_dir.glob("ref_*.png")
        if not path.stem.endswith("_hq")
    )
    if not sources:
        raise SystemExit("No ref_*.png source assets found")

    for source in sources:
        output = args.output_dir / f"{source.stem}_sr.png"
        width, height, mean_error = upscale(sr, source, output)
        print(f"{source.name}\t{width}x{height}\tmean_roundtrip_error={mean_error:.3f}")


if __name__ == "__main__":
    main()
