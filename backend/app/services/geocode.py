"""OpenStreetMap Nominatim 地名坐标（免费，请控制频率）。"""

from __future__ import annotations

import time
from typing import Any

import httpx

from app.crawler.http_config import HEADERS

_NOMINATIM = "https://nominatim.openstreetmap.org/search"
_CACHE: dict[str, tuple[float, float] | None] = {}
_LAST_CALL = 0.0


def geocode_place(name: str, destination: str, country: str = "中国") -> tuple[float, float] | None:
    key = f"{name}|{destination}|{country}"
    if key in _CACHE:
        return _CACHE[key]

    global _LAST_CALL
    elapsed = time.monotonic() - _LAST_CALL
    if elapsed < 1.1:
        time.sleep(1.1 - elapsed)

    query = f"{name}, {destination}, {country}"
    params: dict[str, Any] = {
        "q": query,
        "format": "json",
        "limit": 1,
        "accept-language": "zh",
    }
    headers = {**HEADERS, "User-Agent": HEADERS["User-Agent"]}

    try:
        with httpx.Client(headers=headers, timeout=12.0) as client:
            resp = client.get(_NOMINATIM, params=params)
            resp.raise_for_status()
            items = resp.json()
        _LAST_CALL = time.monotonic()
        if not items:
            _CACHE[key] = None
            return None
        lat, lon = float(items[0]["lat"]), float(items[0]["lon"])
        coords = (lat, lon)
        _CACHE[key] = coords
        return coords
    except Exception:
        _LAST_CALL = time.monotonic()
        _CACHE[key] = None
        return None


def enrich_records_with_coords(
    records: list[dict[str, Any]],
    destination: str,
    max_calls: int = 8,
) -> None:
    calls = 0
    for rec in records:
        lat, lng = rec.get("latitude"), rec.get("longitude")
        if lat and lng and float(lat) != 0 and float(lng) != 0:
            continue
        if calls >= max_calls:
            break
        name = rec.get("name", "")
        if not name:
            continue
        coords = geocode_place(name, destination)
        calls += 1
        if coords:
            rec["latitude"] = coords[0]
            rec["longitude"] = coords[1]
