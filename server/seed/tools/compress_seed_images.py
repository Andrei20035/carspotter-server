#!/usr/bin/env python3
"""
Offline seed-image compressor.

Reproduces the Android client's `ImageCompressor.kt` so that seed images look
exactly like real uploads (the server never compresses — it stores bytes as-is).

Pipeline per image (matching the app):
  1. Apply EXIF orientation, then strip EXIF.
  2. Center-crop to the target aspect ratio (profile 1:1, post 4:5).
  3. Resize down so it fits within max W x H (never upscales).
  4. Encode JPEG at the app's quality.

Parity caveats (see seed/README.md):
  - The Android JPEG encoder (Skia) differs from libjpeg/Pillow, so byte-for-byte
    output is impossible off-device. Dimensions, aspect-crop and format match;
    file sizes are in the same ballpark, not identical.

Non-destructive: reads originals from seed/posts and seed/profile_pictures,
writes compressed copies (same filenames, forced .jpg) into seed/compressed/.
"""

from __future__ import annotations

import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageOps

# ---- Parameters copied verbatim from ImageCompressor.companion -------------
ASPECT_RATIO_EPSILON = 0.01


@dataclass(frozen=True)
class Params:
    max_w: int
    max_h: int
    quality: int

    @property
    def aspect(self) -> float:
        return self.max_w / self.max_h


PROFILE_PARAMS = Params(max_w=512, max_h=512, quality=80)   # ImageCompressor.ProfileParams
CAR_PARAMS = Params(max_w=1080, max_h=1350, quality=82)     # ImageCompressor.CarParams

SEED_DIR = Path(__file__).resolve().parent.parent  # seed/
JOBS = [
    ("posts", "posts", CAR_PARAMS),
    ("profile_pictures", "profile_pictures", PROFILE_PARAMS),
]


def center_crop_to_aspect(img: Image.Image, target_aspect: float) -> Image.Image:
    """Mirror of ImageCompressor.centerCropToAspectRatio."""
    w, h = img.size
    current_aspect = w / h
    if abs(current_aspect - target_aspect) < ASPECT_RATIO_EPSILON:
        return img
    if current_aspect > target_aspect:
        crop_w = min(max(round(h * target_aspect), 1), w)
        crop_x = (w - crop_w) // 2
        return img.crop((crop_x, 0, crop_x + crop_w, h))
    crop_h = min(max(round(w / target_aspect), 1), h)
    crop_y = (h - crop_h) // 2
    return img.crop((0, crop_y, w, crop_y + crop_h))


def resize_if_needed(img: Image.Image, max_w: int, max_h: int) -> Image.Image:
    """Mirror of ImageCompressor.resizeIfNeeded (never upscales)."""
    w, h = img.size
    if w <= max_w and h <= max_h:
        return img
    scale = min(max_w / w, max_h / h)
    target_w = max(int(w * scale), 1)
    target_h = max(int(h * scale), 1)
    return img.resize((target_w, target_h), Image.LANCZOS)


def compress_one(src: Path, dst: Path, p: Params) -> tuple[int, int, tuple[int, int]]:
    with Image.open(src) as img:
        img = ImageOps.exif_transpose(img)        # step 1: bake orientation, drop EXIF
        img = img.convert("RGB")                  # JPEG has no alpha
        img = center_crop_to_aspect(img, p.aspect)  # step 2
        img = resize_if_needed(img, p.max_w, p.max_h)  # step 3
        dst.parent.mkdir(parents=True, exist_ok=True)
        img.save(                                  # step 4
            dst,
            format="JPEG",
            quality=p.quality,
            subsampling="4:2:0",  # typical mobile chroma subsampling
            optimize=False,
            exif=b"",
        )
        return src.stat().st_size, dst.stat().st_size, img.size


def main() -> int:
    total_in = total_out = count = 0
    for src_sub, dst_sub, params in JOBS:
        src_dir = SEED_DIR / src_sub
        dst_dir = SEED_DIR / "compressed" / dst_sub
        files = sorted(
            f for f in src_dir.iterdir()
            if f.is_file() and f.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
        )
        print(f"\n== {src_sub} -> compressed/{dst_sub}  ({len(files)} files, "
              f"target {params.max_w}x{params.max_h} q{params.quality}) ==")
        for f in files:
            dst = dst_dir / (f.stem + ".jpg")
            in_b, out_b, dims = compress_one(f, dst, params)
            total_in += in_b
            total_out += out_b
            count += 1
            print(f"  {f.name:18} {in_b/1024:8.1f} KB -> {out_b/1024:7.1f} KB  "
                  f"{dims[0]}x{dims[1]}")
    print(f"\nDONE: {count} images, {total_in/1024/1024:.2f} MB -> "
          f"{total_out/1024/1024:.2f} MB "
          f"({100*(1-total_out/total_in):.1f}% smaller)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
