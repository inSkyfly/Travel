"""结合 RAG 资料生成结构化行程（供 Android 展示）"""

from __future__ import annotations

import json
from datetime import date, timedelta
from pathlib import Path
from typing import Any

from app.config import CORPUS_DIR
from app.llm.client import llm_client
from app.rag.store import rag_store


def _load_json_assets(city: str, kind: str) -> list[dict]:
    key = "chengdu" if "成都" in city else city
    path = Path(__file__).resolve().parents[2] / "data" / "structured" / f"{kind}_{key}.json"
    if not path.exists():
        path = Path(__file__).resolve().parents[2] / "data" / "structured" / f"{kind}_chengdu.json"
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def _enrich_from_rag(items: list[dict], rag_hits: list[dict], name_key: str = "name") -> list[dict]:
    if not rag_hits:
        return items
    corpus = "\n".join(h["text"][:300] for h in rag_hits)
    for item in items:
        name = item.get(name_key, "")
        related = [h["text"][:180] for h in rag_hits if name and name[:2] in h["text"]]
        if related and not item.get("reason"):
            item["reason"] = related[0]
    return items


def generate_plan(request: dict[str, Any]) -> dict[str, Any]:
    destination = request.get("destination", "成都")
    origin = request.get("origin", "北京")
    date_range = request.get("dateRange", {})
    start = date.fromisoformat(date_range.get("start", str(date.today())))
    end = date.fromisoformat(date_range.get("end", str(start + timedelta(days=3))))
    day_count = max((end - start).days + 1, 1)
    travelers = int(request.get("travelers", 2))

    rag_query = f"{destination} 景点 美食 住宿 交通 游记 推荐 避坑"
    rag_hits: list[dict] = []
    seen: set[str] = set()
    for doc_type in ("guide", "qa", "web", "attraction"):
        for hit in rag_store.search(
            rag_query, destination=destination, top_k=3, doc_type=doc_type
        ):
            key = hit["text"][:80]
            if key in seen:
                continue
            seen.add(key)
            rag_hits.append(hit)
    if len(rag_hits) < 8:
        for hit in rag_store.search(rag_query, destination=destination, top_k=8):
            key = hit["text"][:80]
            if key in seen:
                continue
            seen.add(key)
            rag_hits.append(hit)
            if len(rag_hits) >= 8:
                break
    rag_context = rag_store.build_context(rag_query, destination=destination, top_k=8)

    hotels = _load_json_assets(destination, "hotels")
    foods = _load_json_assets(destination, "foods")
    attractions = _load_json_assets(destination, "attractions")
    hotels = _enrich_from_rag(hotels, rag_hits)
    foods = _enrich_from_rag(foods, rag_hits)
    attractions = _enrich_from_rag(attractions, rag_hits)

    daily_plans = []
    for i in range(day_count):
        d = start + timedelta(days=i)
        am = attractions[(i * 2) % len(attractions)] if attractions else {"name": "市区漫步", "reason": "轻松游览"}
        pm = attractions[(i * 2 + 1) % len(attractions)] if attractions else am
        daily_plans.append(
            {
                "dayIndex": i + 1,
                "date": d.isoformat(),
                "activities": [
                    {
                        "period": "上午",
                        "title": am.get("name", "景点"),
                        "description": am.get("reason", "推荐游览"),
                        "transportToNext": "地铁/打车约30分钟",
                    },
                    {
                        "period": "下午",
                        "title": pm.get("name", "景点"),
                        "description": pm.get("avoidTips") or pm.get("reason", "按节奏游览"),
                        "transportToNext": "步行或公交",
                    },
                    {
                        "period": "晚上",
                        "title": "品尝本地美食",
                        "description": foods[0]["name"] if foods else "本地特色餐厅",
                        "transportToNext": None,
                    },
                ],
            }
        )

    tips_raw = llm_client.summarize_for_plan(request, rag_context)
    try:
        local_tips = json.loads(tips_raw)
        if not isinstance(local_tips, list):
            local_tips = [str(local_tips)]
    except json.JSONDecodeError:
        local_tips = [ln.strip() for ln in tips_raw.split("\n") if ln.strip()][:5]

    transport_price = 550 * travelers
    return {
        "request": request,
        "transport": {
            "outbound": {
                "type": "TRAIN",
                "number": "G308",
                "departure": origin,
                "arrival": destination,
                "departTime": "08:15",
                "arriveTime": "14:20",
                "duration": "约6小时",
                "price": transport_price,
                "transferInfo": None,
                "bookingUrl": "https://www.12306.cn/index/",
            },
            "inbound": {
                "type": "TRAIN",
                "number": "G309",
                "departure": destination,
                "arrival": origin,
                "departTime": "17:30",
                "arriveTime": "23:45",
                "duration": "约6小时",
                "price": transport_price,
                "transferInfo": None,
                "bookingUrl": "https://www.12306.cn/index/",
            },
        },
        "dailyPlans": daily_plans,
        "accommodations": hotels,
        "foods": foods,
        "attractions": attractions,
        "budgetBreakdown": {
            "total": 6000,
            "categories": [
                {"name": "交通", "allocated": 1800, "spent": transport_price * 2},
                {"name": "住宿", "allocated": 1800, "spent": sum(h.get("pricePerNight", 400) for h in hotels[:2])},
                {"name": "餐饮", "allocated": 1320, "spent": sum(f.get("avgPrice", 60) for f in foods[:3]) * day_count},
                {"name": "门票", "allocated": 720, "spent": 200 * day_count},
                {"name": "其他", "allocated": 360, "spent": 300},
            ],
        },
        "weatherTips": [
            {
                "date": (start + timedelta(days=i)).isoformat(),
                "condition": "多云",
                "tempHigh": 24,
                "tempLow": 16,
                "precipitation": "20%",
                "wind": "东南风2-3级",
                "clothingAdvice": "早晚温差大，建议薄外套",
            }
            for i in range(day_count)
        ],
        "localTips": local_tips,
        "deepLinks": {
            "train_outbound": "https://www.12306.cn/index/",
            "hotel": hotels[0].get("bookingUrl", "https://hotel.meituan.com/") if hotels else "",
        },
        "ragSnippetCount": len(rag_hits),
    }
