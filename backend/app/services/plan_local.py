"""仅用本地 structured JSON 生成行程（不调用 LLM / 外网）。"""

from __future__ import annotations

from datetime import date
from typing import Any

from app.services.plan_enricher import enrich_plan_body, load_structured
from app.services.plan_service import _normalize_plan_body


def _has_local_materials(destination: str) -> bool:
    return bool(
        load_structured("attractions", destination)
        or load_structured("foods", destination)
        or load_structured("hotels", destination)
    )


def has_local_materials(destination: str) -> bool:
    return _has_local_materials(destination)


def generate_plan_from_local_data(
    request: dict[str, Any],
    start: date,
    day_count: int,
) -> dict[str, Any]:
    destination = request.get("destination", "成都")
    origin = request.get("origin", "")

    shell: dict[str, Any] = {
        "transport": {
            "outbound": {
                "type": "TRAIN",
                "number": "参考车次",
                "departure": origin,
                "arrival": destination,
                "departTime": "08:00",
                "arriveTime": "14:00",
                "duration": "以实际购票为准",
                "price": 500,
                "transferInfo": None,
                "bookingUrl": "https://www.12306.cn/index/",
            },
            "inbound": {
                "type": "TRAIN",
                "number": "参考车次",
                "departure": destination,
                "arrival": origin,
                "departTime": "17:00",
                "arriveTime": "23:00",
                "duration": "以实际购票为准",
                "price": 500,
                "transferInfo": None,
                "bookingUrl": "https://www.12306.cn/index/",
            },
        },
        "dailyPlans": [],
        "attractions": [],
        "foods": [],
        "accommodations": [],
        "weatherTips": [],
        "localTips": [],
        "deepLinks": {},
    }

    result = _normalize_plan_body(shell, request, start, day_count)
    result = enrich_plan_body(
        result,
        destination,
        [],
        start=start,
        day_count=day_count,
        offline_local=True,
    )
    if result.get("planSource") in ("rag_structured", "rag_web"):
        result["planSource"] = "local_structured"
    else:
        result["planSource"] = "local_partial"
    result["offlineMode"] = True
    result["ragSummaryUsed"] = False
    tips = list(result.get("localTips") or [])
    result["localTips"] = [
        "本行程基于本地资料库生成（联网查询未使用或已失败，数据为模拟参考）",
        *tips,
    ]
    return result
