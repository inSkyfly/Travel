"""按目的地自动发现维基 / Wikivoyage 页面 URL（无需预配置城市列表）。"""

from __future__ import annotations

import re
from urllib.parse import quote

import httpx

from app.crawler.http_config import HEADERS

_WIKI_API = "https://zh.wikipedia.org/w/api.php"


def _wiki_page_url(lang: str, title: str) -> str:
    return f"https://{lang}.wikipedia.org/wiki/{quote(title, safe='')}"


def _wikivoyage_page_url(title: str) -> str:
    return f"https://zh.wikivoyage.org/wiki/{quote(title, safe='')}"


def wikipedia_opensearch(query: str, limit: int = 5) -> list[str]:
    """维基百科开放搜索，返回相关页面标题。"""
    params = {
        "action": "opensearch",
        "search": query,
        "limit": limit,
        "namespace": 0,
        "format": "json",
    }
    try:
        with httpx.Client(headers=HEADERS, timeout=15.0) as client:
            resp = client.get(_WIKI_API, params=params)
            resp.raise_for_status()
            data = resp.json()
    except Exception:
        return []
    titles = data[1] if isinstance(data, list) and len(data) > 1 else []
    return [t for t in titles if isinstance(t, str) and t.strip()]


def discover_wikipedia_urls(destination: str, extra_queries: list[str] | None = None) -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()

    def add(title: str) -> None:
        url = _wiki_page_url("zh", title)
        if url not in seen:
            seen.add(url)
            urls.append(url)

    add(destination)
    for q in (extra_queries or []) + [
        f"{destination}旅游",
        f"{destination}景点",
    ]:
        for title in wikipedia_opensearch(q, limit=4):
            add(title)

    return urls[:8]


def discover_wikivoyage_urls(destination: str) -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()

    def add(title: str) -> None:
        url = _wikivoyage_page_url(title)
        if url not in seen:
            seen.add(url)
            urls.append(url)

    add(destination)
    if not destination.endswith("省") and len(destination) <= 4:
        add(f"{destination}省")

    return urls[:4]


def discover_crawl_urls(destination: str) -> dict[str, list[str]]:
    return {
        "wikipedia": discover_wikipedia_urls(destination),
        "wikivoyage": discover_wikivoyage_urls(destination),
    }
