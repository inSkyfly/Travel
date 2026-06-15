"""Wikidata 开放知识库：中文景点实体（SPARQL，无需 API Key）。"""

from __future__ import annotations

import time
from pathlib import Path
from typing import Any

import httpx

from app.crawler.http_config import HEADERS, REQUEST_DELAY_SEC

WIKIDATA_API = "https://query.wikidata.org/sparql"


def _sparql_for_place(destination: str) -> str:
    # 旅游景点、遗产、公园等，关联到指定中文地名
    return f"""
SELECT DISTINCT ?item ?itemLabel ?itemDescription WHERE {{
  VALUES ?type {{
    wd:Q570116
    wd:Q33506
    wd:Q839954
    wd:Q22698
  }}
  ?item wdt:P31/wdt:P279* ?type .
  ?item wdt:P131* ?location .
  ?location rdfs:label "{destination}"@zh .
  SERVICE wikibase:label {{ bd:serviceParam wikibase:language "zh,en" . }}
}}
LIMIT 40
"""


def fetch_wikidata_places(destination: str) -> list[dict[str, Any]]:
    params = {
        "query": _sparql_for_place(destination),
        "format": "json",
    }
    headers = {**HEADERS, "Accept": "application/sparql-results+json"}
    try:
        with httpx.Client(headers=headers, timeout=30.0) as client:
            resp = client.get(WIKIDATA_API, params=params)
            resp.raise_for_status()
            data = resp.json()
    except Exception:
        return []

    results: list[dict[str, Any]] = []
    seen: set[str] = set()
    for row in data.get("results", {}).get("bindings", []):
        label = row.get("itemLabel", {}).get("value", "")
        if not label or label.startswith("Q") or label in seen:
            continue
        desc = row.get("itemDescription", {}).get("value", "")
        seen.add(label)
        results.append(
            {
                "name": label,
                "tags": ["Wikidata"],
                "reason": desc or f"Wikidata 开放知识库收录的{destination}相关地点",
                "avoidTips": "",
                "bestTimeSlot": "全天",
                "ticketPrice": 0,
                "latitude": 0.0,
                "longitude": 0.0,
                "address": "",
            }
        )
    return results


def crawl_wikidata_to_corpus(destination: str, out_dir: Path) -> tuple[list[str], list[str]]:
    from app.crawler.build_corpus import _save_markdown

    saved: list[str] = []
    errors: list[str] = []
    time.sleep(REQUEST_DELAY_SEC)

    places = fetch_wikidata_places(destination)
    if not places:
        return saved, errors

    lines = [f"## {destination} 地点（Wikidata 开放知识库）"]
    for p in places:
        lines.append(f"- {p['name']}：{p.get('reason', '')}")

    path = _save_markdown(
        out_dir,
        prefix="wikidata",
        title=f"{destination}_wikidata",
        source_url="https://www.wikidata.org/",
        body="\n".join(lines),
        extra_header="类型: Wikidata 开放结构化数据",
    )
    if path:
        saved.append(str(path))
    return saved, errors
