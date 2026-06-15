"""用结构化资料 + RAG 检索结果丰富行程：坐标、描述、交通估算"""

from __future__ import annotations

import json
import math
import re
from datetime import date, timedelta
from typing import Any

from app.config import DATA_DIR, resolve_city_key
from app.services.geocode import enrich_records_with_coords, geocode_place
from app.services.rag_extract import extract_attractions_from_rag, extract_foods_from_rag

STRUCTURED_DIR = DATA_DIR / "structured"


def load_structured(kind: str, destination: str) -> list[dict[str, Any]]:
    key = resolve_city_key(destination)
    path = STRUCTURED_DIR / f"{kind}_{key}.json"
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def _attractions_from_foods(foods: list[dict[str, Any]], destination: str) -> list[dict[str, Any]]:
    """无景点 JSON 时，用美食商圈/餐厅坐标合成游览点（离线兜底）。"""
    out: list[dict[str, Any]] = []
    seen: set[str] = set()
    for food in foods:
        area = (food.get("area") or destination).strip()
        if not area or area in seen:
            continue
        seen.add(area)
        out.append(
            {
                "name": f"{area}风情街",
                "tags": ["美食探店"],
                "reason": food.get("reason") or f"推荐品尝：{food.get('name', '本地美食')}",
                "avoidTips": food.get("avoidTips") or "",
                "bestTimeSlot": "全天",
                "ticketPrice": 0,
                "latitude": food.get("latitude", 0.0),
                "longitude": food.get("longitude", 0.0),
                "address": food.get("address", ""),
            }
        )
    for food in foods:
        name = (food.get("name") or "").strip()
        short = re.sub(r"[（(].*", "", name).strip()
        if len(short) < 2 or short in seen:
            continue
        seen.add(short)
        out.append(
            {
                "name": short,
                "tags": ["当地推荐"],
                "reason": food.get("reason") or "本地人气餐饮",
                "avoidTips": food.get("avoidTips") or "",
                "bestTimeSlot": "全天",
                "ticketPrice": 0,
                "latitude": food.get("latitude", 0.0),
                "longitude": food.get("longitude", 0.0),
                "address": food.get("address", ""),
            }
        )
        if len(out) >= 6:
            break
    return out


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def estimate_local_transport(
    from_lat: float,
    from_lng: float,
    to_lat: float,
    to_lng: float,
) -> dict[str, str]:
    dist = haversine_km(from_lat, from_lng, to_lat, to_lng)
    if dist < 1.2:
        mins = max(5, int(dist / 4.5 * 60))
        return {"transportToNext": f"步行约{mins}分钟（约{dist:.1f}公里）", "transportMode": "WALK"}
    if dist < 12:
        mins = max(12, int(dist / 22 * 60) + 8)
        return {
            "transportToNext": f"地铁约{mins}分钟（约{dist:.1f}公里）",
            "transportMode": "METRO",
        }
    mins = max(20, int(dist / 32 * 60) + 10)
    return {
        "transportToNext": f"打车约{mins}分钟（约{dist:.1f}公里）",
        "transportMode": "TAXI",
    }


def _match_name(name: str, candidate: str) -> bool:
    if not name or not candidate:
        return False
    a, b = name.strip(), candidate.strip()
    if a == b or a in b or b in a:
        return True
    # 去掉括号后缀再比
    a_core = re.sub(r"[（(].*?[）)]", "", a).strip()
    b_core = re.sub(r"[（(].*?[）)]", "", b).strip()
    if a_core and b_core and (a_core in b_core or b_core in a_core):
        return True
    return len(a_core) >= 2 and a_core[:2] in b_core


def _merge_record(target: dict[str, Any], source: dict[str, Any]) -> None:
    for key in (
        "name",
        "reason",
        "avoidTips",
        "bestTimeSlot",
        "ticketPrice",
        "latitude",
        "longitude",
        "address",
        "area",
        "taste",
        "avgPrice",
        "bookingUrl",
        "platform",
        "rating",
        "recentGoodRate",
        "keywords",
        "pricePerNight",
        "distanceToAttraction",
    ):
        val = source.get(key)
        if val is None or val == "" or val == 0 or val == 0.0:
            continue
        if key not in target or target.get(key) in (None, "", 0, 0.0, [], "说明"):
            target[key] = val


def _rag_snippet_for(name: str, rag_hits: list[dict[str, Any]]) -> str | None:
    if not name:
        return None
    core = re.sub(r"[（(].*?[）)]", "", name).strip()[:4]
    for hit in rag_hits:
        text = hit.get("text", "")
        if core and core in text:
            meta = hit.get("metadata", {})
            src = meta.get("source", "资料")
            snippet = text.strip().replace("\n", " ")[:220]
            return f"资料摘录（{src}）：{snippet}"
    return None


class PlaceIndex:
    def __init__(
        self,
        attractions: list[dict[str, Any]],
        foods: list[dict[str, Any]],
    ) -> None:
        self._places: list[dict[str, Any]] = []
        for item in attractions:
            self._places.append({**item, "_kind": "attraction"})
        for item in foods:
            self._places.append({**item, "_kind": "food"})

    def resolve(self, name: str) -> dict[str, Any] | None:
        if not name:
            return None
        for place in self._places:
            if _match_name(name, place.get("name", "")):
                return place
        return None

    def coords(self, name: str) -> tuple[float, float] | None:
        place = self.resolve(name)
        if not place:
            return None
        lat, lng = place.get("latitude"), place.get("longitude")
        if lat and lng and float(lat) != 0 and float(lng) != 0:
            return float(lat), float(lng)
        return None


def _activity_with_transport(
    period: str,
    title: str,
    description: str,
    next_name: str,
    index: PlaceIndex,
) -> dict[str, Any]:
    act: dict[str, Any] = {
        "period": period,
        "title": title,
        "description": description,
        "nextDestinationName": next_name,
        "nextDestinationLat": 0.0,
        "nextDestinationLng": 0.0,
        "transportMode": "NONE",
    }
    next_coords = index.coords(next_name)
    from_coords = index.coords(title)
    if next_coords:
        act["nextDestinationLat"] = next_coords[0]
        act["nextDestinationLng"] = next_coords[1]
    if from_coords and next_coords:
        transport = estimate_local_transport(
            from_coords[0], from_coords[1], next_coords[0], next_coords[1]
        )
        act["transportToNext"] = transport["transportToNext"]
        act["transportMode"] = transport["transportMode"]
    elif next_name:
        act["transportToNext"] = f"前往 {next_name}（请用地图导航）"
        act["transportMode"] = "METRO"
    return act


def _build_daily_plans_from_materials(
    attractions: list[dict[str, Any]],
    foods: list[dict[str, Any]],
    start: date,
    day_count: int,
    rag_hits: list[dict[str, Any]],
) -> list[dict[str, Any]]:
    """用结构化资料（含坐标）按 App 模板重建每日行程，避免 LLM 乱写景点名导致无法匹配。"""
    if not attractions:
        return []

    index = PlaceIndex(attractions, foods)
    daily: list[dict[str, Any]] = []

    for i in range(day_count):
        d = start + timedelta(days=i)
        am = attractions[(i * 2) % len(attractions)]
        pm = attractions[(i * 2 + 1) % len(attractions)]
        food = foods[i % len(foods)] if foods else None

        am_title = am.get("name", "景点")
        pm_title = pm.get("name", "景点")
        food_name = food.get("name", "本地特色餐厅") if food else "本地特色餐厅"

        am_desc = am.get("reason") or am.get("avoidTips") or ""
        if not am_desc:
            am_desc = _rag_snippet_for(am_title, rag_hits) or ""
        pm_desc = pm.get("avoidTips") or pm.get("reason") or ""
        if not pm_desc:
            pm_desc = _rag_snippet_for(pm_title, rag_hits) or ""
        evening_desc = (food.get("reason") if food else None) or food_name

        daily.append(
            {
                "dayIndex": i + 1,
                "date": d.isoformat(),
                "activities": [
                    _activity_with_transport("上午", am_title, am_desc, pm_title, index),
                    _activity_with_transport("下午", pm_title, pm_desc, food_name, index),
                    {
                        "period": "晚上",
                        "title": "品尝本地美食",
                        "description": evening_desc,
                        "transportToNext": None,
                        "transportMode": "NONE",
                    },
                ],
            }
        )
    return daily


def enrich_plan_body(
    plan: dict[str, Any],
    destination: str,
    rag_hits: list[dict[str, Any]],
    start: date | None = None,
    day_count: int | None = None,
    offline_local: bool = False,
) -> dict[str, Any]:
    attractions_db = load_structured("attractions", destination)
    foods_db = load_structured("foods", destination)
    hotels_db = load_structured("hotels", destination)

    if len(attractions_db) < 2 and foods_db:
        attractions_db = _attractions_from_foods(foods_db, destination)

    if not plan.get("attractions"):
        plan["attractions"] = [dict(a) for a in attractions_db]
    else:
        for attr in plan["attractions"]:
            for src in attractions_db:
                if _match_name(attr.get("name", ""), src.get("name", "")):
                    _merge_record(attr, src)
                    break
            snippet = _rag_snippet_for(attr.get("name", ""), rag_hits)
            if snippet and (
                not attr.get("reason")
                or attr.get("reason") in ("推荐理由", "来自 RAG 资料摘要", "说明")
            ):
                attr["reason"] = snippet

    if not plan.get("foods"):
        plan["foods"] = [dict(f) for f in foods_db]
    else:
        for food in plan["foods"]:
            for src in foods_db:
                if _match_name(food.get("name", ""), src.get("name", "")):
                    _merge_record(food, src)
                    break

    if not plan.get("accommodations") and hotels_db:
        plan["accommodations"] = [dict(h) for h in hotels_db]
    else:
        for hotel in plan.get("accommodations") or []:
            for src in hotels_db:
                if _match_name(hotel.get("name", ""), src.get("name", "")):
                    _merge_record(hotel, src)
                    break

    attractions_final = plan.get("attractions") or attractions_db
    foods_final = plan.get("foods") or foods_db

    rag_attractions = extract_attractions_from_rag(rag_hits, destination) if not attractions_db else []
    rag_foods = extract_foods_from_rag(rag_hits, destination) if not foods_db else []

    if attractions_db and start and day_count:
        plan["dailyPlans"] = _build_daily_plans_from_materials(
            attractions_db,
            foods_final or foods_db,
            start,
            day_count,
            rag_hits,
        )
        plan["planSource"] = "rag_structured"
    elif start and day_count:
        web_attrs = rag_attractions if len(rag_attractions) >= 2 else []
        if len(web_attrs) < 2 and attractions_final:
            web_attrs = [
                a for a in attractions_final
                if isinstance(a, dict) and a.get("name")
            ]
        web_foods = rag_foods if rag_foods else (foods_final or [])
        if len(web_attrs) >= 2:
            if not offline_local:
                enrich_records_with_coords(web_attrs, destination)
                if web_foods:
                    enrich_records_with_coords(web_foods, destination, max_calls=4)
            plan["attractions"] = web_attrs
            if web_foods:
                plan["foods"] = web_foods
            plan["dailyPlans"] = _build_daily_plans_from_materials(
                web_attrs,
                web_foods,
                start,
                day_count,
                rag_hits,
            )
            plan["planSource"] = "rag_web"
        else:
            plan["planSource"] = "llm_only"
            if not offline_local:
                enrich_records_with_coords(attractions_final, destination, max_calls=6)
            index = PlaceIndex(attractions_final, foods_final)
            _enrich_daily_plans_llm(index, plan, rag_hits, destination, offline_local)
    else:
        plan["planSource"] = "llm_only"

    return plan


def _enrich_daily_plans_llm(
    index: PlaceIndex,
    plan: dict[str, Any],
    rag_hits: list[dict[str, Any]],
    destination: str,
    offline_local: bool = False,
) -> None:
    for day in plan.get("dailyPlans") or []:
        activities = day.get("activities") or []
        for i, act in enumerate(activities):
            title = act.get("title", "")
            place = index.resolve(title)
            if place:
                if place.get("reason") and (
                    not act.get("description")
                    or act.get("description") in ("说明", "推荐游览", "按节奏游览")
                    or act.get("description", "").startswith("游览")
                    or act.get("description", "").startswith("深度游览")
                ):
                    desc = place.get("reason") or place.get("avoidTips") or ""
                    if desc:
                        act["description"] = desc
                snippet = _rag_snippet_for(title, rag_hits)
                if snippet and len(act.get("description", "")) < 30:
                    act["description"] = snippet

            next_name = act.get("nextDestinationName")
            if not next_name and i + 1 < len(activities):
                next_name = activities[i + 1].get("title", "")
            if not next_name and act.get("period") == "下午":
                next_name = act.get("description", "")[:20]

            if act.get("period") == "晚上" and not next_name:
                desc = act.get("description", "")
                food = index.resolve(desc)
                if food:
                    next_name = food.get("name", "")

            if not next_name:
                act["transportToNext"] = None
                act["transportMode"] = "NONE"
                continue

            act["nextDestinationName"] = next_name
            next_coords = index.coords(next_name)
            if not next_coords and not offline_local:
                next_coords = geocode_place(next_name, destination)
            if next_coords:
                act["nextDestinationLat"] = next_coords[0]
                act["nextDestinationLng"] = next_coords[1]

            from_coords = index.coords(title)
            if not from_coords and not offline_local:
                from_coords = geocode_place(title, destination)
            if from_coords and next_coords:
                transport = estimate_local_transport(
                    from_coords[0], from_coords[1], next_coords[0], next_coords[1]
                )
                act["transportToNext"] = transport["transportToNext"]
                act["transportMode"] = transport["transportMode"]
            elif next_coords:
                act["transportToNext"] = f"前往 {next_name}（请用地图导航）"
                act.setdefault("transportMode", "METRO")
