"""Prepares the website images and fonts in worker/public/assets.

Run: python site/prepare_assets.py   (then: node site/build.mjs)

- Screenshots come from site/shots. They are real app screens captured on an Android emulator with
  demo data, connected to a local test server. They contain no real user data.
- The icon comes from the iOS app icon, and the fonts from the Android app (Tajawal).
"""
from pathlib import Path
import shutil

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "worker" / "public" / "assets"
(OUT / "shots").mkdir(parents=True, exist_ok=True)
(OUT / "fonts").mkdir(parents=True, exist_ok=True)

# Icon: one large for the page, one for Apple devices, one favicon
icon = Image.open(ROOT / "native/ios/Afiati/Assets.xcassets/AppIcon.appiconset/afiati-icon-1024.png").convert("RGB")
icon.resize((256, 256), Image.LANCZOS).save(OUT / "icon.png", optimize=True)
icon.resize((180, 180), Image.LANCZOS).save(OUT / "apple-touch-icon.png", optimize=True)
icon.resize((48, 48), Image.LANCZOS).save(OUT / "favicon.png", optimize=True)
# Preview image for links shared in messaging apps (1200x630): icon centred on the app's dark blue
card = Image.new("RGB", (1200, 630), (4, 12, 18))
big = icon.resize((360, 360), Image.LANCZOS)
card.paste(big, ((1200 - 360) // 2, (630 - 360) // 2))
card.save(OUT / "share.png", optimize=True)

# Screenshots: 1080x2340 -> 540x1170 WebP (sharp on phones, small to download)
for source in sorted((ROOT / "site" / "shots").glob("*.png")):
    image = Image.open(source).convert("RGB").resize((540, 1170), Image.LANCZOS)
    image.save(OUT / "shots" / (source.stem + ".webp"), "WEBP", quality=86, method=6)

for weight in ("regular", "medium", "bold"):
    shutil.copyfile(ROOT / f"native/android/app/src/main/res/font/tajawal_{weight}.ttf", OUT / "fonts" / f"Tajawal-{weight.capitalize()}.ttf")

print("assets ready:", sorted(p.name for p in OUT.rglob("*") if p.is_file()))
