"""生成行程前按需拉取国内开源旅游数据。"""

from __future__ import annotations

from app.crawler.build_corpus import crawl_destination
from app.rag.store import rag_store
from app.services.plan_enricher import load_structured


def count_destination_hits(destination: str, min_query: str = "攻略") -> int:
    hits = rag_store.search(
        f"{destination} 景点 美食 {min_query}",
        destination=destination,
        top_k=8,
    )
    return len(hits)


def ensure_destination_corpus(
    destination: str,
    min_hits: int = 3,
    min_attractions: int = 4,
) -> dict | None:
    """
    本地语料或开放 POI 不足时尝试自动拉取。
    联网失败不会抛异常，避免阻断行程生成。
    """
    if not destination.strip():
        return None

    hits = count_destination_hits(destination)
    attr_count = len(load_structured("attractions", destination))
    if attr_count >= min_attractions:
        return {
            "skipped": True,
            "reason": "structured_sufficient",
            "hits": hits,
            "attractions": attr_count,
        }
    if hits >= min_hits:
        return {
            "skipped": True,
            "reason": "corpus_sufficient",
            "hits": hits,
            "attractions": attr_count,
        }

    try:
        result = crawl_destination(destination)
        result["hits_before"] = hits
        result["attractions_before"] = attr_count
        return result
    except Exception as exc:
        return {
            "failed": True,
            "error": str(exc),
            "hits": hits,
            "attractions": attr_count,
        }
