"""Checks the store listing fields in store/listing.md against App Store and Google Play limits."""
import re
from pathlib import Path

LIMITS = {"App name": 30, "Subtitle": 30, "Short description": 80, "Promotional text": 170, "Description": 4000, "Keywords": 100, "What's new": 4000}
text = Path(__file__).with_name("listing.md").read_text(encoding="utf-8")
failed = False
for lang_block in re.split(r"\n## ", text):
    lang = lang_block.split("\n", 1)[0]
    for match in re.finditer(r"### (.+?)\n(.*?)(?=\n### |\n---|\Z)", lang_block, re.S):
        title, body = match.group(1), match.group(2).strip()
        limit = next((v for k, v in LIMITS.items() if title.startswith(k)), None)
        if limit is None:
            continue
        ok = len(body) <= limit
        if title.startswith("Keywords") and ", " in body:
            ok = False
        failed |= not ok
        print(f"{'OK  ' if ok else 'OVER'} {lang[:8]:8} {title[:28]:28} {len(body):5}/{limit}")
print("FAILED" if failed else "ALL WITHIN LIMITS")
