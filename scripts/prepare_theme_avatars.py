"""Package approved artwork as small lossless Android assets, without changing originals.

Run with --write once to create assets, or without it to verify the saved resources.
Requires Pillow with WebP support; it is not part of the Android build.
"""

import argparse
import hashlib
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
EDGE = 384  # 75dp up to 5x density, with a little headroom for filtering.
ARTWORK = (
    ("sand", "砂岩", 1),
    ("pine", "松绿", 1),
    ("matisse", "马蒂斯", 1),
    ("dunhuang", "敦煌", 1),
    ("old_port", "旧港来信", 9),
    ("peach_plum", "蜜桃乌梅", 2),
    ("burgundy", "勃艮第", 4),  # Confirmed B: cloth underneath the glass.
    ("morandi", "莫兰迪", 4),
    ("red_bean", "入骨相思", 4),
    ("autumn_citrus", "橙黄橘绿时", 5),
    ("morning_bell", "晨钟暮鼓", 7),
    ("mondrian", "蒙德里安", 1),
)


def prepare(write: bool) -> None:
    target = ROOT / "app/src/main/res/drawable-nodpi"
    if write:
        target.mkdir(parents=True, exist_ok=True)
    total = 0
    for key, name, version in ARTWORK:
        source = ROOT / "docs/colors" / name / f"avatar-generated-v{version}.png"
        output = target / f"theme_avatar_{key}.webp"
        original_hash = hashlib.sha256(source.read_bytes()).digest()
        with Image.open(source) as original:
            assert original.width == original.height, f"Not square: {source}"
            assert original.convert("RGBA").getchannel("A").getextrema() == (255, 255)
            pixels = original.convert("RGB").resize((EDGE, EDGE), Image.Resampling.LANCZOS)
        if write:
            if output.exists():
                raise FileExistsError(f"Refusing to overwrite {output}")
            pixels.save(output, "WEBP", lossless=True, quality=100, method=6, exact=True)
        with Image.open(output) as packaged:
            assert packaged.size == (EDGE, EDGE), output
            assert packaged.convert("RGB").tobytes() == pixels.tobytes(), f"Pixel mismatch: {output}"
        assert hashlib.sha256(source.read_bytes()).digest() == original_hash, source
        total += output.stat().st_size
        print(f"{key}: v{version}, {EDGE}x{EDGE}, {output.stat().st_size:,} bytes, pixels verified")
    print(f"{len(ARTWORK)} approved assets; total {total:,} bytes; all original PNGs unchanged")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true", help="Create missing resources; never overwrite")
    prepare(parser.parse_args().write)
