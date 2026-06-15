"""从 RAG 检索片段中抽取景点 / 美食名称（无需手工 structured JSON）。"""

from __future__ import annotations

import re
from typing import Any

_PLACE_HINT = re.compile(
    r"(景区|风景区|国家公园|古城|博物馆|公园|寺|庙|湖|山|草原|大峡谷|湾|塔|陵|故里|大巴扎|天池|瀑布|峡谷|湿地|故居)"
)
_FOOD_HINT = re.compile(r"(美食|小吃|餐厅|烤肉|火锅|拌面|馕|抓饭|米线|烤鸭|豆腐|烧烤|夜市|面馆|奶茶)")
_SKIP_PREFIX = re.compile(r"^(来源|类型|标签|问题|高赞)")


def _clean_line(line: str) -> str:
    line = line.strip().lstrip("-•*#0123456789. ")
    line = re.sub(r"\[.*?\]", "", line)
    line = re.sub(r"[（(].*?[）)]", "", line).strip()
    return line


def extract_attractions_from_rag(
    rag_hits: list[dict[str, Any]],
    destination: str,
    limit: int = 10,
) -> list[dict[str, Any]]:
    seen: set[str] = set()
    results: list[dict[str, Any]] = []

    for hit in rag_hits:
        text = hit.get("text", "")
        for raw in text.splitlines():
            if _SKIP_PREFIX.search(raw.strip()):
                continue
            line = _clean_line(raw)
            if len(line) < 3 or len(line) > 28:
                continue
            if destination not in line and not _PLACE_HINT.search(line):
                continue
            name = line.split("：")[0].split(":")[0].strip()
            if len(name) < 3 or name in seen:
                continue
            seen.add(name)
            snippet = line.replace("\n", " ")[:220]
            results.append(
                {
                    "name": name,
                    "tags": ["当地推荐"],
                    "reason": snippet,
                    "avoidTips": "",
                    "bestTimeSlot": "全天",
                    "ticketPrice": 0,
                    "latitude": 0.0,
                    "longitude": 0.0,
                    "address": "",
                }
            )
            if len(results) >= limit:
                return results

    return results


def extract_foods_from_rag(
    rag_hits: list[dict[str, Any]],
    destination: str,
    limit: int = 6,
) -> list[dict[str, Any]]:
    seen: set[str] = set()
    results: list[dict[str, Any]] = []

    for hit in rag_hits:
        for raw in hit.get("text", "").splitlines():
            if _SKIP_PREFIX.search(raw.strip()):
                continue
            line = _clean_line(raw)
            if len(line) < 2 or len(line) > 30:
                continue
            if not _FOOD_HINT.search(line):
                continue
            name = line.split("：")[0].split(":")[0].strip()
            if len(name) < 2 or name in seen:
                continue
            seen.add(name)
            results.append(
                {
                    "name": name[:24],
                    "area": destination,
                    "taste": "当地风味",
                    "mealType": "正餐",
                    "avgPrice": 60,
                    "isLocalFavorite": True,
                    "isInfluencerHype": False,
                    "reason": line[:180],
                    "bookingUrl": "https://www.meituan.com/meishi/",
                    "address": "",
                    "latitude": 0.0,
                    "longitude": 0.0,
                    "platform": "美团",
                }
            )
            if len(results) >= limit:
                return results

    return results
