"""Verify transparent LOGO VectorDrawable and package fallback launcher icons.

Requires Inkscape on PATH and Pillow. Normal Android builds need neither.
Run --write to regenerate the themed mask and ten launcher WebPs; SVG is read-only.
The primary transparent launcher resources remain native Android vectors.
"""

import argparse
import copy
import re
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/branding/LOGO.svg"
RES = ROOT / "app/src/main/res"
OUT = ROOT / "app/build/launcher-verification"
ANDROID = "{http://schemas.android.com/apk/res/android}"
SVG = "{http://www.w3.org/2000/svg}"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def check_vector(source):
    vector = ET.parse(RES / "drawable/youroom_foreground.xml").getroot()
    source_paths = list(source.iter(SVG + "path"))
    native_paths = list(vector.iter("path"))
    assert len(source_paths) == len(native_paths) == 40
    for original, native in zip(source_paths, native_paths):
        assert original.attrib["d"] == native.attrib[ANDROID + "pathData"]
        style = dict(item.split(":", 1) for item in original.attrib["style"].split(";") if item)
        fill = "#00000000" if style["fill"] == "none" else style["fill"].lower()
        assert fill == native.attrib[ANDROID + "fillColor"].lower()
        assert float(style.get("fill-opacity", 1)) == float(native.attrib.get(ANDROID + "fillAlpha", 1))
    width, height = map(float, source.attrib["viewBox"].split()[2:])
    outer, inner = list(vector.iter("group"))
    scale = float(outer.attrib[ANDROID + "scaleX"])
    assert scale == float(outer.attrib[ANDROID + "scaleY"])
    assert abs(max(width, height) * scale - 60) < .0001
    assert abs(float(outer.attrib[ANDROID + "translateX"]) - (108 - width * scale) / 2) < .0001
    assert abs(float(outer.attrib[ANDROID + "translateY"]) - (108 - height * scale) / 2) < .0001
    transform = source.find(SVG + "g").attrib["transform"]
    tx, ty = map(float, re.fullmatch(r"translate\(([^,]+),([^\)]+)\)", transform).groups())
    assert float(inner.attrib[ANDROID + "translateX"]) == tx
    assert float(inner.attrib[ANDROID + "translateY"]) == ty
    background = ET.parse(RES / "drawable/youroom_background.xml").getroot()
    assert background.find("path").attrib[ANDROID + "fillColor"] == "#00000000"
    for name in ("ic_launcher", "ic_launcher_round"):
        entry = ET.parse(RES / f"mipmap-anydpi/{name}.xml").getroot()
        assert entry.tag == "inset"
        assert entry.attrib[ANDROID + "drawable"] == "@drawable/youroom_launcher"
    launcher = ET.parse(RES / "drawable/youroom_launcher.xml").getroot()
    assert launcher.attrib[ANDROID + "viewportWidth"] == "72"
    assert launcher.attrib[ANDROID + "viewportHeight"] == "72"
    assert [p.attrib for p in launcher.iter("path")] == [p.attrib for p in native_paths]
    launcher_outer, launcher_inner = list(launcher.iter("group"))
    assert launcher_inner.attrib == inner.attrib
    for key in ("scaleX", "scaleY"):
        assert launcher_outer.attrib[ANDROID + key] == outer.attrib[ANDROID + key]
    for key in ("translateX", "translateY"):
        assert abs(float(launcher_outer.attrib[ANDROID + key]) - float(outer.attrib[ANDROID + key]) + 18) < .0001
    return width, height


def foreground(source, width, height, monochrome=False):
    scale = 60 / max(width, height)
    root = ET.Element(SVG + "svg", {"width": "108", "height": "108", "viewBox": "0 0 108 108"})
    group = ET.SubElement(root, SVG + "g", {"transform": f"translate({(108-width*scale)/2},{(108-height*scale)/2}) scale({scale})"})
    artwork = copy.deepcopy(source.find(SVG + "g"))
    if monochrome:
        # LOGO has one white highlight, not a cutout. Preserve all painted shapes,
        # their opacity, and the unpainted path in the optional silhouette.
        for path in artwork.iter(SVG + "path"):
            style = dict(item.split(":", 1) for item in path.attrib["style"].split(";") if item)
            fill = "none" if style["fill"] == "none" else "#000000"
            path.set("style", f"fill:{fill};fill-opacity:{style.get('fill-opacity', '1')}")
    group.append(artwork)
    inkscape = shutil.which("inkscape") or shutil.which("inkscape.com")
    if not inkscape:
        raise RuntimeError("Install Inkscape or add its executable to PATH.")
    png = OUT / ("monochrome-source.png" if monochrome else "foreground-source.png")
    subprocess.run([inkscape, "--pipe", "--export-type=png", "--export-width=864", f"--export-filename={png}"],
                   input=ET.tostring(root), check=True, capture_output=True)
    image = Image.open(png).convert("RGBA")
    if monochrome:
        alpha = ImageChops.multiply(image.getchannel("A"), ImageChops.invert(image.convert("L")))
        mask = Image.new("RGBA",image.size,(0,0,0,0))
        mask.putalpha(alpha)
        return mask
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    source = ET.parse(SOURCE).getroot()
    width, height = check_vector(source)
    image = foreground(source, width, height)
    mono = foreground(source, width, height, monochrome=True).resize((432,432),Image.Resampling.LANCZOS)
    mono_path = RES / "drawable-nodpi/youroom_monochrome.png"
    if args.write:
        mono.save(mono_path)
    with Image.open(mono_path) as actual:
        assert actual.convert("RGBA").tobytes() == mono.tobytes(), str(mono_path)
    # Ensure all nontransparent artwork fits the adaptive 66dp-diameter safe circle.
    alpha = image.getchannel("A")
    for y in range(image.height):
        for x in range(image.width):
            if alpha.getpixel((x, y)) > 8:
                assert ((x + .5) / 8 - 54) ** 2 + ((y + .5) / 8 - 54) ** 2 < 33 ** 2
    # AdaptiveIconDrawable expands its layers: the visible mask corresponds to the
    # central 72dp portion of the 108dp layers, not a 108dp shrink-to-fit bitmap.
    visible = image.crop((144, 144, 720, 720))
    full = visible.copy()
    assert full.getpixel((0, 0))[3] == 0
    assert full.getpixel((288, 192))[3] == 0, "Top-face negative space must stay transparent"
    # The tiny original white highlight is artwork, not an opaque background.
    assert sum(full.getpixel((x,y)) == (255,255,255,255)
               for y in range(full.height) for x in range(full.width)) < 100
    round_mask = Image.new("L", full.size)
    ImageDraw.Draw(round_mask).ellipse((0, 0, full.width - 1, full.height - 1), fill=255)
    circle = full.copy()
    circle.putalpha(ImageChops.multiply(full.getchannel("A"), round_mask))
    for density, size in DENSITIES.items():
        for name, art in (("ic_launcher", full), ("ic_launcher_round", circle)):
            target = RES / f"mipmap-{density}/{name}.webp"
            expected = art.resize((size, size), Image.Resampling.LANCZOS)
            if args.write:
                expected.save(target, "WEBP", lossless=True, exact=True)
            with Image.open(target) as actual:
                assert actual.size == (size, size)
                assert actual.convert("RGBA").tobytes() == expected.tobytes(), str(target)
    # Code-native packaging preview, not a generated/repainted source image.
    sheet = Image.new("RGB", (820, 340), "#E9E9E6")
    draw = ImageDraw.Draw(sheet)
    for column, (label, art) in enumerate((("Square", full), ("Round", circle), ("Rounded", full))):
        tile = art.resize((192, 192), Image.Resampling.LANCZOS)
        if column == 2:
            mask = Image.new("L", (192, 192))
            ImageDraw.Draw(mask).rounded_rectangle((0, 0, 191, 191), radius=43, fill=255)
            tile.putalpha(ImageChops.multiply(tile.getchannel("A"), mask))
        left = 45 + column * 268
        for y in range(0, 192, 16):
            for x in range(0, 192, 16):
                color = "#FAFAFA" if (x // 16 + y // 16) % 2 == 0 else "#DDDDDA"
                draw.rectangle((left+x, 52+y, left+x+15, 52+y+15), fill=color)
        sheet.paste(tile, (left, 52), tile)
        draw.text((45 + column * 268, 265), label, fill="#333333")
    sheet.save(OUT / "launcher-masks.png")
    print("PASS: 40 SVG paths/fills/opacity match; original transform preserved; 66dp safe zone; "
          "transparent background and negative space; themed mask and 10 transparent WebPs match SVG.")


if __name__ == "__main__":
    main()
