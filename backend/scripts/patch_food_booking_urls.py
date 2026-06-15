"""修复美食 bookingUrl：移除会 400 的 poi/search，改用 i.meituan.com/s/。"""
from __future__ import annotations

import json
import re
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent.parent
DIRS = [
    ROOT / "data" / "structured",
    ROOT.parent / "app" / "src" / "main" / "assets" / "mock",
]


def patch_food(item: dict) -> dict:
    name = item.get("name", "")
    area = item.get("area", "")
    platform = item.get("platform", "")
    url = item.get("bookingUrl", "")

    if "poi/search" in url or url.endswith("meishi/") or url.endswith("meishi"):
        keyword = f"{name} {area}".strip() if area else name
        if "美团" in platform:
            item["bookingUrl"] = f"https://i.meituan.com/s/{quote(keyword)}"
        elif "点评" in platform and not re.search(r"dianping\.com/shop/\d+", url):
            item["bookingUrl"] = f"https://m.dianping.com/shoplist/0/searchkeyword_{quote(keyword)}"

    return item


def main() -> None:
    for base in DIRS:
        if not base.exists():
            continue
        for path in base.glob("foods_*.json"):
            items = json.loads(path.read_text(encoding="utf-8"))
            for i, item in enumerate(items):
                items[i] = patch_food(item)
            path.write_text(json.dumps(items, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print("fixed", path.name)


if __name__ == "__main__":
    main()
