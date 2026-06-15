"""OpenStreetMap 开放数据：景点 / 餐饮 POI（国内目的地，无需 API Key）。"""

from __future__ import annotations

import json
import time
from pathlib import Path
from typing import Any

import httpx

from app.crawler.http_config import HEADERS, REQUEST_DELAY_SEC
from app.config import DATA_DIR

STRUCTURED_DIR = DATA_DIR / "structured"
NOMINATIM = "https://nominatim.openstreetmap.org/search"
OVERPASS = "https://overpass.kumi.systems/api/interpreter"

_ATTRACTION_TOURISM = "attraction|museum|viewpoint|theme_park|zoo|gallery"
_FOOD_AMENITY = "restaurant|cafe|fast_food|food_court|bar|biergarten"


def _nominatim_lookup(destination: str) -> dict[str, Any] | None:
    params = {
        "q": destination,
        "format": "json",
        "limit": 1,
        "countrycodes": "cn",
        "accept-language": "zh",
    }
    headers = {**HEADERS, "User-Agent": HEADERS["User-Agent"]}
    try:
        with httpx.Client(headers=headers, timeout=12.0) as client:
            resp = client.get(NOMINATIM, params=params)
            resp.raise_for_status()
            items = resp.json()
    except Exception:
        return None
    if not items:
        return None
    return items[0]


def _place_radius_km(place: dict[str, Any]) -> int:
    place_type = (place.get("type") or "").lower()
    category = (place.get("category") or "").lower()
    if place_type in ("state", "province") or "省" in place.get("display_name", ""):
        return 120
    if place_type in ("city", "town") or category == "boundary":
        return 35
    return 25


def _overpass_query(lat: float, lon: float, radius_km: int) -> str:
    r = radius_km * 1000
    return f"""
[out:json][timeout:45];
(
  node["tourism"~"{_ATTRACTION_TOURISM}"](around:{r},{lat},{lon});
  way["tourism"~"{_ATTRACTION_TOURISM}"](around:{r},{lat},{lon});
  node["amenity"~"{_FOOD_AMENITY}"](around:{min(r, 20000)},{lat},{lon});
  way["amenity"~"{_FOOD_AMENITY}"](around:{min(r, 20000)},{lat},{lon});
);
out center 60;
"""


def _element_name(tags: dict[str, Any]) -> str | None:
    for key in ("name:zh", "name", "official_name", "alt_name:zh", "alt_name"):
        val = tags.get(key)
        if val and isinstance(val, str) and len(val.strip()) >= 2:
            return val.strip()
    return None


def _element_coords(element: dict[str, Any]) -> tuple[float, float] | None:
    if "lat" in element and "lon" in element:
        return float(element["lat"]), float(element["lon"])
    center = element.get("center")
    if center and "lat" in center and "lon" in center:
        return float(center["lat"]), float(center["lon"])
    return None


def _parse_osm_elements(
    elements: list[dict[str, Any]],
    destination: str,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    attractions: list[dict[str, Any]] = []
    foods: list[dict[str, Any]] = []
    seen_attr: set[str] = set()
    seen_food: set[str] = set()

    for el in elements:
        tags = el.get("tags") or {}
        name = _element_name(tags)
        if not name:
            continue
        coords = _element_coords(el)
        if not coords:
            continue
        lat, lon = coords
        tourism = tags.get("tourism", "")
        amenity = tags.get("amenity", "")
        address = tags.get("addr:full") or tags.get("addr:street") or ""

        if tourism and name not in seen_attr:
            seen_attr.add(name)
            attractions.append(
                {
                    "name": name,
                    "tags": [tourism],
                    "reason": f"OpenStreetMap 开放数据收录的{destination}旅游点位",
                    "avoidTips": tags.get("opening_hours") or "",
                    "bestTimeSlot": "全天",
                    "ticketPrice": 0,
                    "latitude": lat,
                    "longitude": lon,
                    "address": address,
                }
            )
        elif amenity and name not in seen_food:
            seen_food.add(name)
            foods.append(
                {
                    "name": name,
                    "area": destination,
                    "taste": tags.get("cuisine", "当地风味") or "当地风味",
                    "mealType": "正餐",
                    "avgPrice": 60,
                    "isLocalFavorite": True,
                    "isInfluencerHype": False,
                    "reason": f"OpenStreetMap 开放数据收录的餐饮点位",
                    "bookingUrl": "https://www.meituan.com/meishi/",
                    "address": address,
                    "latitude": lat,
                    "longitude": lon,
                    "platform": "美团",
                }
            )

    return attractions[:30], foods[:20]


def _merge_records(existing: list[dict], new: list[dict]) -> list[dict]:
    by_name: dict[str, dict] = {}
    for item in existing:
        name = item.get("name", "")
        if name:
            by_name[name] = item
    for item in new:
        name = item.get("name", "")
        if not name:
            continue
        if name in by_name:
            old = by_name[name]
            for k, v in item.items():
                if v and (not old.get(k) or old.get(k) in (0, 0.0, "", [])):
                    old[k] = v
        else:
            by_name[name] = item
    return list(by_name.values())


def _save_structured(city_key: str, attractions: list[dict], foods: list[dict]) -> None:
    STRUCTURED_DIR.mkdir(parents=True, exist_ok=True)
    attr_path = STRUCTURED_DIR / f"attractions_{city_key}.json"
    food_path = STRUCTURED_DIR / f"foods_{city_key}.json"

    if attractions:
        existing = json.loads(attr_path.read_text(encoding="utf-8")) if attr_path.exists() else []
        merged = _merge_records(existing, attractions)
        attr_path.write_text(json.dumps(merged, ensure_ascii=False, indent=2), encoding="utf-8")

    if foods:
        existing = json.loads(food_path.read_text(encoding="utf-8")) if food_path.exists() else []
        merged = _merge_records(existing, foods)
        food_path.write_text(json.dumps(merged, ensure_ascii=False, indent=2), encoding="utf-8")


def merge_open_attractions(city_key: str, attractions: list[dict]) -> None:
    if attractions:
        _save_structured(city_key, attractions, [])


def fetch_osm_pois(destination: str, city_key: str) -> tuple[list[dict], list[dict], list[str]]:
    errors: list[str] = []
    place = _nominatim_lookup(destination)
    if not place:
        return [], [], [f"nominatim: 未找到「{destination}」"]

    time.sleep(REQUEST_DELAY_SEC)
    lat, lon = float(place["lat"]), float(place["lon"])
    radius = _place_radius_km(place)
    query = _overpass_query(lat, lon, radius)

    try:
        with httpx.Client(headers=HEADERS, timeout=60.0) as client:
            resp = client.post(OVERPASS, data={"data": query})
            resp.raise_for_status()
            data = resp.json()
    except Exception as exc:
        return [], [], [f"overpass: {exc}"]

    elements = data.get("elements") or []
    attractions, foods = _parse_osm_elements(elements, destination)
    if attractions or foods:
        _save_structured(city_key, attractions, foods)

    return attractions, foods, errors


def crawl_osm_to_corpus(
    destination: str,
    city_key: str,
    out_dir: Path,
) -> tuple[list[str], list[str]]:
    """写入语料 Markdown 并更新 structured JSON。"""
    from app.crawler.build_corpus import _save_markdown

    saved: list[str] = []
    errors: list[str] = []
    attractions, foods, fetch_errors = fetch_osm_pois(destination, city_key)
    errors.extend(fetch_errors)

    if attractions:
        lines = [f"## {destination} 景点（OpenStreetMap 开放数据）"]
        for a in attractions[:25]:
            lines.append(
                f"- {a['name']}：{a.get('address', '')} "
                f"（坐标 {a['latitude']:.4f},{a['longitude']:.4f}）"
            )
        path = _save_markdown(
            out_dir,
            prefix="osm",
            title=f"{destination}_景点",
            source_url="https://www.openstreetmap.org/",
            body="\n".join(lines),
            extra_header="类型: OpenStreetMap 开放旅游 POI",
        )
        if path:
            saved.append(str(path))

    if foods:
        lines = [f"## {destination} 美食餐饮（OpenStreetMap 开放数据）"]
        for f in foods[:20]:
            lines.append(f"- {f['name']}：{f.get('taste', '')} {f.get('address', '')}")
        path = _save_markdown(
            out_dir,
            prefix="osm",
            title=f"{destination}_美食",
            source_url="https://www.openstreetmap.org/",
            body="\n".join(lines),
            extra_header="类型: OpenStreetMap 开放餐饮 POI",
        )
        if path:
            saved.append(str(path))

    return saved, errors
