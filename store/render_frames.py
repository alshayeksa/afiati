"""App Store images: a caption above each real iPhone screenshot, at the 6.9-inch size (1320x2868).

Run: python store/render_frames.py <folder with the raw screenshots from ios-screenshots.yml>
Output: store/screenshots/ar/*.png and store/screenshots/en/*.png, in upload order.
Rendered with Microsoft Edge (headless), so the Arabic text is shaped correctly.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FONTS = (ROOT / "worker/public/assets/fonts").as_uri()
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
W, H = 1320, 2868

SCENES = [
    ("home", ("دواؤك في وقته", "تذكير واضح بكل جرعة"), ("Your medicine, on time", "A clear reminder for every dose")),
    ("mom-day", ("أبناؤك يطمئنون عليك", "بموافقتك وبالقدر الذي تختاره"), ("Family knows you're okay", "With your consent, as much as you choose")),
    ("alert", ("طلب المساعدة يصلهم فورًا", "مع موقعك على الخريطة"), ("Help reaches them fast", "With your location on the map")),
    ("help", ("«أنا قادم» تطمئنك", "تعرف أن أحدهم في الطريق"), ("“I'm coming”", "Know that someone is on the way")),
    ("meds", ("أدويتك في مكان واحد", "الجرعات والمواعيد والمخزون"), ("Your medicines in one place", "Doses, times and stock")),
    ("family", ("أنت تختار من يتابعك", "وما يراه كل واحد منهم"), ("You choose who follows you", "And what each of them sees")),
]

PAGE = """<!doctype html><html lang="{lang}" dir="{dir}"><meta charset="utf-8"><style>
@font-face{{font-family:T;font-weight:500;src:url({fonts}/Tajawal-Medium.ttf)}}
@font-face{{font-family:T;font-weight:700;src:url({fonts}/Tajawal-Bold.ttf)}}
html,body{{margin:0;width:{w}px;height:{h}px;overflow:hidden}}
body{{background:radial-gradient(120% 70% at 50% 0,#0E2C44 0,#04101A 55%,#020507 100%);font-family:T,sans-serif;color:#fff;text-align:center}}
h1{{margin:150px 70px 0;font-size:96px;line-height:1.25;font-weight:700}}
p{{margin:26px 70px 0;font-size:56px;line-height:1.35;font-weight:500;color:#4FB9F0}}
.phone{{position:absolute;left:50%;top:600px;width:1080px;transform:translateX(-50%);border-radius:104px;border:14px solid #15232D;
  box-shadow:0 0 0 2px rgba(79,185,240,.25),0 40px 120px rgba(0,0,0,.6),0 0 160px rgba(69,171,226,.18);overflow:hidden;background:#000}}
.phone img{{display:block;width:100%}}
</style><body><h1>{title}</h1><p>{sub}</p><div class="phone"><img src="{shot}"></div></body></html>"""


def main(source: Path):
    with tempfile.TemporaryDirectory() as tmp:
        for lang in ("ar", "en"):
            out = ROOT / "store" / "screenshots" / lang
            out.mkdir(parents=True, exist_ok=True)
            for index, (name, ar, en) in enumerate(SCENES, start=1):
                shot = source / f"{name}-{lang}.png"
                if not shot.exists():
                    print("missing", shot)
                    continue
                title, sub = ar if lang == "ar" else en
                page = Path(tmp) / f"{name}-{lang}.html"
                page.write_text(PAGE.format(lang=lang, dir="rtl" if lang == "ar" else "ltr", fonts=FONTS, w=W, h=H,
                                            title=title, sub=sub, shot=shot.resolve().as_uri()), encoding="utf-8")
                target = out / f"{index:02d}-{name}.png"
                subprocess.run([EDGE, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                                "--force-device-scale-factor=1", f"--window-size={W},{H}", f"--screenshot={target}", page.as_uri()],
                               check=True, capture_output=True)
                print("written", target.relative_to(ROOT))


if __name__ == "__main__":
    main(Path(sys.argv[1]))
