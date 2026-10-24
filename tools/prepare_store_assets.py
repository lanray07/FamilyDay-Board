"""Deterministic Amazon tablet exports: original vector icon and lossless screenshots.

Requirements verified against Amazon's appstore-details documentation on 2026-10-24.
No screenshots are redrawn, cropped, enhanced, or generated.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import hashlib
import json
import math

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "store/submission_assets"
TEAL = "#277C78"
CREAM = "#F8F7F2"
ORANGE = "#E9A66B"


def icon(size):
    # The exact geometry from app/src/main/res/drawable/ic_familyday.xml.
    scale = size * 4 / 108
    image = Image.new("RGBA", (size * 4, size * 4), TEAL)
    draw = ImageDraw.Draw(image)
    def points(values):
        return [(round(x * scale), round(y * scale)) for x, y in values]
    draw.polygon(points([(22, 28), (86, 28), (86, 86), (22, 86)]), fill=CREAM)
    draw.polygon(points([(22, 28), (86, 28), (86, 42), (22, 42)]), fill=ORANGE)
    offset = 2.5 / math.sqrt(2)
    draw.polygon(points([(38-offset, 60+offset), (49, 71+2*offset),
                         (72+offset, 48+offset), (72-offset, 48-offset),
                         (49, 71-2*offset), (38+offset, 60-offset)]), fill=TEAL)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    files = []
    for size in (114, 512):
        destination = OUT / f"familyday-icon-{size}.png"
        icon(size).save(destination, optimize=True, compress_level=9)
        files.append({"file": destination.name, "role": f"tablet_icon_{size}", "source": "app/src/main/res/drawable/ic_familyday.xml"})
    # Optional promotional art: brand title and original icon only, with a 64px safe margin.
    promo = Image.new("RGB", (1024, 500), CREAM)
    draw = ImageDraw.Draw(promo)
    draw.ellipse((760, -90, 1130, 280), fill="#F7E5D5")
    draw.ellipse((770, 365, 1080, 675), fill="#E7EEE3")
    font = ImageFont.truetype("C:/Windows/Fonts/georgia.ttf", 82)
    draw.text((64, 145), "FamilyDay", font=font, fill=TEAL)
    draw.text((64, 245), "Board", font=font, fill=TEAL)
    promo.paste(icon(264).convert("RGB"), (696, 118))
    promo.save(OUT / "familyday-promo-1024x500.png", optimize=True, compress_level=9)
    files.append({"file": "familyday-promo-1024x500.png", "role": "optional_promotion", "source": "deterministic original brand composition; no screenshot or pricing claims"})
    for source in sorted((ROOT / "store/screenshots/en-GB").glob("*.png")):
        image = Image.open(source)
        assert sorted(image.size) == [1200, 1920]
        rgba = image.convert("RGBA")
        assert rgba.getchannel("A").getextrema() == (255, 255)
        output = OUT / source.name
        image.convert("RGB").save(output, optimize=True, compress_level=9)
        assert Image.open(output).convert("RGBA").tobytes() == rgba.tobytes(), "Screenshot pixels changed"
        files.append({"file": output.name, "role": "genuine_screenshot", "source": str(source.relative_to(ROOT)),
                      "source_bytes": source.stat().st_size,
                      "pixel_sha256": hashlib.sha256(rgba.tobytes()).hexdigest()})
    for item in files:
        path = OUT / item["file"]
        im = Image.open(path)
        item.update({"width": im.width, "height": im.height, "mode": im.mode,
                     "bytes": path.stat().st_size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    manifest = {"requirements_source": "https://developer.amazon.com/docs/app-submission/appstore-details.html",
                "verified_date": "2026-10-24", "screenshots_pixel_identical": True, "files": files}
    (OUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps([{"file": f["file"], "size": [f["width"], f["height"]], "bytes": f["bytes"]} for f in files], indent=2))


if __name__ == "__main__":
    main()
