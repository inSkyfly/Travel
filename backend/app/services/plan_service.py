"""RAG 检索 → 摘要 → LLM 生成结构化行程（供 Android 展示）"""

from __future__ import annotations

import copy
from datetime import date, timedelta
from typing import Any

from app.llm.client import llm_client
from app.rag.store import rag_store


def _collect_rag_context(destination: str) -> tuple[str, int]:
    rag_query = f"{destination} 景点 美食 住宿 交通 游记 推荐 避坑 攻略"
    rag_hits: list[dict] = []
    seen: set[str] = set()
    for doc_type in ("guide", "qa", "web", "attraction"):
        for hit in rag_store.search(
            rag_query, destination=destination, top_k=4, doc_type=doc_type
        ):
            key = hit["text"][:80]
            if key in seen:
                continue
            seen.add(key)
            rag_hits.append(hit)
    if len(rag_hits) < 12:
        for hit in rag_store.search(rag_query, destination=destination, top_k=12):
            key = hit["text"][:80]
            if key in seen:
                continue
            seen.add(key)
            rag_hits.append(hit)
            if len(rag_hits) >= 12:
                break

    # 无城市过滤的兜底检索（语料 metadata 未标城市时）
    if not rag_hits:
        for hit in rag_store.search(rag_query, destination="", top_k=12):
            key = hit["text"][:80]
            if key in seen:
                continue
            if destination[:2] not in hit["text"]:
                continue
            seen.add(key)
            rag_hits.append(hit)

    rag_context = rag_store.build_context(rag_query, destination=destination, top_k=12)
    if not rag_context.strip() and rag_hits:
        parts = []
        for i, hit in enumerate(rag_hits[:12], 1):
            meta = hit.get("metadata", {})
            parts.append(
                f"[{i}] 来源:{meta.get('source', 'unknown')}\n{hit['text'][:400]}"
            )
        rag_context = "\n\n".join(parts)
    return rag_context, len(rag_hits)


def _normalize_plan_body(
    body: dict[str, Any],
    request: dict[str, Any],
    start: date,
    day_count: int,
) -> dict[str, Any]:
    """补齐缺省字段，并强制使用客户端提交的 request。"""
    plan = copy.deepcopy(body)
    origin = request.get("origin", "")
    destination = request.get("destination", "")

    transport = plan.setdefault("transport", {})
    outbound = transport.setdefault("outbound", {})
    inbound = transport.setdefault("inbound", {})
    outbound.setdefault("type", "TRAIN")
    inbound.setdefault("type", "TRAIN")
    outbound["departure"] = origin or outbound.get("departure", "")
    outbound["arrival"] = destination or outbound.get("arrival", "")
    inbound["departure"] = destination or inbound.get("departure", "")
    inbound["arrival"] = origin or inbound.get("arrival", "")
    outbound.setdefault("bookingUrl", "https://www.12306.cn/index/")
    inbound.setdefault("bookingUrl", "https://www.12306.cn/index/")
    outbound.setdefault("price", 500)
    inbound.setdefault("price", 500)

    daily = plan.get("dailyPlans") or []
    if len(daily) < day_count:
        daily = list(daily)
        while len(daily) < day_count:
            i = len(daily)
            d = start + timedelta(days=i)
            daily.append(
                {
                    "dayIndex": i + 1,
                    "date": d.isoformat(),
                    "activities": [
                        {
                            "period": "上午",
                            "title": f"{destination}市区游览",
                            "description": "自由安排",
                            "transportToNext": None,
                            "transportMode": "NONE",
                        }
                    ],
                }
            )
    for i, day in enumerate(daily[:day_count]):
        day["dayIndex"] = i + 1
        day["date"] = (start + timedelta(days=i)).isoformat()
        for act in day.get("activities") or []:
            act.setdefault("transportMode", "NONE")
            act.setdefault("nextDestinationLat", 0.0)
            act.setdefault("nextDestinationLng", 0.0)

    plan["dailyPlans"] = daily[:day_count]

    for hotel in plan.get("accommodations") or []:
        hotel.setdefault("rating", 4.5)
        hotel.setdefault("recentGoodRate", 85)
        hotel.setdefault("keywords", [])
        hotel.setdefault("pricePerNight", 400)
        hotel.setdefault("bookingUrl", "https://hotel.meituan.com/")
        hotel.setdefault("platform", "美团")

    for food in plan.get("foods") or []:
        food.setdefault("avgPrice", 60)
        food.setdefault("isLocalFavorite", True)
        food.setdefault("isInfluencerHype", False)
        food.setdefault("bookingUrl", "https://www.meituan.com/")
        food.setdefault("platform", "大众点评")

    for attr in plan.get("attractions") or []:
        attr.setdefault("tags", [])
        attr.setdefault("ticketPrice", 0)
        attr.setdefault("bestTimeSlot", "全天")

    weather = plan.get("weatherTips") or []
    if len(weather) < day_count:
        weather = list(weather)
        while len(weather) < day_count:
            i = len(weather)
            weather.append(
                {
                    "date": (start + timedelta(days=i)).isoformat(),
                    "condition": "多云",
                    "tempHigh": 24,
                    "tempLow": 16,
                    "precipitation": "20%",
                    "wind": "微风",
                    "clothingAdvice": "根据气温增减衣物",
                }
            )
    for i, w in enumerate(weather[:day_count]):
        w["date"] = (start + timedelta(days=i)).isoformat()

    plan["weatherTips"] = weather[:day_count]
    plan.setdefault("localTips", [f"出行前请关注{destination}天气与景区开放信息"])
    plan.setdefault("deepLinks", {})
    plan["deepLinks"].setdefault("train_outbound", "https://www.12306.cn/index/")
    plan["deepLinks"].setdefault(
        "hotel",
        (plan.get("accommodations") or [{}])[0].get("bookingUrl", "https://hotel.meituan.com/"),
    )

    if not plan.get("budgetBreakdown"):
        plan["budgetBreakdown"] = {
            "total": 6000,
            "categories": [
                {"name": "交通", "allocated": 1800, "spent": 1200},
                {"name": "住宿", "allocated": 1800, "spent": 1500},
                {"name": "餐饮", "allocated": 1200, "spent": 1000},
                {"name": "门票", "allocated": 800, "spent": 600},
                {"name": "其他", "allocated": 400, "spent": 300},
            ],
        }

    plan["request"] = request
    return plan


def generate_plan(request: dict[str, Any]) -> dict[str, Any]:
    destination = request.get("destination", "成都")
    date_range = request.get("dateRange", {})
    start = date.fromisoformat(date_range.get("start", str(date.today())))
    end = date.fromisoformat(date_range.get("end", str(start + timedelta(days=3))))
    day_count = max((end - start).days + 1, 1)

    rag_context, rag_snippet_count = _collect_rag_context(destination)
    rag_summary = llm_client.summarize_rag_for_plan(destination, rag_context)

    plan_body = llm_client.generate_trip_plan(
        request=request,
        rag_summary=rag_summary,
        day_count=day_count,
        start=start,
        end=end,
    )

    result = _normalize_plan_body(plan_body, request, start, day_count)
    result["ragSnippetCount"] = rag_snippet_count
    result["ragSummaryUsed"] = True
    return result
