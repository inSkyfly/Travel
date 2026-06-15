"""从公开网页抓取旅游资料并写入语料库"""

from __future__ import annotations

import html
import re
import time
from pathlib import Path
from typing import Any
import httpx
from bs4 import BeautifulSoup

from app.config import (
    CORPUS_DIR,
    CRAWL_SOURCES,
    STACKEXCHANGE_QUERIES,
    WIKIVOYAGE_SOURCES,
    resolve_city_key,
)
from app.crawler.discover import discover_crawl_urls
from app.crawler.http_config import HEADERS, REQUEST_DELAY_SEC
from app.crawler.osm_open_data import crawl_osm_to_corpus, merge_open_attractions
from app.crawler.wikidata_open import crawl_wikidata_to_corpus, fetch_wikidata_places
from app.rag.store import rag_store

STACKEXCHANGE_API = "https://api.stackexchange.com/2.2"


def _safe_filename(name: str, max_len: int = 50) -> str:
    return re.sub(r"[^\w\u4e00-\u9fff-]", "_", name)[:max_len]


def _title_from_url(url: str) -> str:
    return url.rstrip("/").split("/")[-1]


def fetch_page_text(url: str, timeout: float = 12.0) -> str:
    with httpx.Client(headers=HEADERS, follow_redirects=True, timeout=timeout) as client:
        resp = client.get(url)
        resp.raise_for_status()
    soup = BeautifulSoup(resp.text, "lxml")
    for tag in soup(["script", "style", "nav", "footer", "sup"]):
        tag.decompose()
    content = soup.find("div", {"id": "mw-content-text"}) or soup.find("main") or soup.body
    if not content:
        return ""
    paragraphs = [
        p.get_text(" ", strip=True)
        for p in content.find_all(["p", "li", "h2", "h3", "h4"])
        if len(p.get_text(strip=True)) > 15
    ]
    return "\n".join(paragraphs)


def _save_markdown(
    out_dir: Path,
    *,
    prefix: str,
    title: str,
    source_url: str,
    body: str,
    extra_header: str = "",
) -> Path | None:
    if len(body.strip()) < 80:
        return None
    safe = _safe_filename(title)
    path = out_dir / f"{prefix}_{safe}.md"
    header = f"# {title}\n来源: {source_url}\n"
    if extra_header:
        header += f"{extra_header}\n"
    path.write_text(f"{header}\n{body}", encoding="utf-8")
    return path


def _sources_for(destination: str, mapping: dict[str, list]) -> list:
    city_key = resolve_city_key(destination)
    return mapping.get(destination) or mapping.get(city_key, [])


def crawl_wikipedia(destination: str, out_dir: Path) -> tuple[list[str], list[str]]:
    saved: list[str] = []
    errors: list[str] = []
    urls = _sources_for(destination, CRAWL_SOURCES)
    if not urls:
        urls = discover_crawl_urls(destination)["wikipedia"]
    for url in urls:
        try:
            text = fetch_page_text(url)
            title = _title_from_url(url)
            path = _save_markdown(
                out_dir,
                prefix="web",
                title=title,
                source_url=url,
                body=text,
            )
            if path is None:
                errors.append(f"{url}: content too short")
            else:
                saved.append(str(path))
        except Exception as exc:
            errors.append(f"{url}: {exc}")
        time.sleep(REQUEST_DELAY_SEC)
    return saved, errors


def crawl_wikivoyage(destination: str, out_dir: Path) -> tuple[list[str], list[str]]:
    saved: list[str] = []
    errors: list[str] = []
    urls = _sources_for(destination, WIKIVOYAGE_SOURCES)
    if not urls:
        urls = discover_crawl_urls(destination)["wikivoyage"]
    for url in urls:
        try:
            text = fetch_page_text(url)
            title = _title_from_url(url)
            path = _save_markdown(
                out_dir,
                prefix="guide",
                title=title,
                source_url=url,
                body=text,
                extra_header="类型: 维基导游（交通/住宿/景点实用指南）",
            )
            if path is None:
                errors.append(f"{url}: content too short")
            else:
                saved.append(str(path))
        except Exception as exc:
            errors.append(f"{url}: {exc}")
        time.sleep(REQUEST_DELAY_SEC)
    return saved, errors


def _strip_html(text: str) -> str:
    if not text:
        return ""
    return html.unescape(BeautifulSoup(text, "lxml").get_text("\n", strip=True))


def _fetch_stackexchange_questions(
    client: httpx.Client,
    *,
    query: str,
    tagged: str,
    pagesize: int = 12,
) -> list[dict[str, Any]]:
    params = {
        "order": "desc",
        "sort": "votes",
        "site": "travel",
        "q": query,
        "pagesize": pagesize,
        "filter": "withbody",
    }
    if tagged:
        params["tagged"] = tagged
    resp = client.get(f"{STACKEXCHANGE_API}/search/advanced", params=params)
    resp.raise_for_status()
    return resp.json().get("items", [])


def _fetch_stackexchange_answers(
    client: httpx.Client,
    question_ids: list[int],
    *,
    pagesize: int = 3,
) -> dict[int, list[dict[str, Any]]]:
    if not question_ids:
        return {}
    params = {
        "order": "desc",
        "sort": "votes",
        "site": "travel",
        "pagesize": pagesize,
        "filter": "withbody",
    }
    resp = client.get(
        f"{STACKEXCHANGE_API}/questions/{';'.join(str(i) for i in question_ids)}/answers",
        params=params,
    )
    resp.raise_for_status()
    grouped: dict[int, list[dict[str, Any]]] = {qid: [] for qid in question_ids}
    for answer in resp.json().get("items", []):
        qid = answer.get("question_id")
        if qid in grouped:
            grouped[qid].append(answer)
    return grouped


def crawl_stackexchange(destination: str, out_dir: Path) -> tuple[list[str], list[str]]:
    city_key = resolve_city_key(destination)
    config = STACKEXCHANGE_QUERIES.get(destination) or STACKEXCHANGE_QUERIES.get(city_key)
    if not config:
        config = {"q": destination, "tagged": ""}

    saved: list[str] = []
    errors: list[str] = []
    with httpx.Client(headers=HEADERS, timeout=30.0) as client:
        try:
            questions = _fetch_stackexchange_questions(
                client,
                query=config["q"],
                tagged=config.get("tagged", "china"),
            )
            if not questions:
                return saved, errors

            qids = [int(q["question_id"]) for q in questions if q.get("question_id")]
            answers_by_q = _fetch_stackexchange_answers(client, qids)
            time.sleep(REQUEST_DELAY_SEC)

            for question in questions:
                qid = question.get("question_id")
                if not qid:
                    continue
                title = _strip_html(question.get("title", "")) or f"question_{qid}"
                link = question.get("link", "")
                tags = ", ".join(question.get("tags", []))
                body_parts = [
                    f"## 问题\n{_strip_html(question.get('body', ''))}",
                ]
                for ans in answers_by_q.get(int(qid), []):
                    score = ans.get("score", 0)
                    body_parts.append(
                        f"## 高赞回答 (score: {score})\n{_strip_html(ans.get('body', ''))}"
                    )
                md_body = "\n\n".join(part for part in body_parts if part.strip())
                path = _save_markdown(
                    out_dir,
                    prefix=f"qa_{qid}",
                    title=title,
                    source_url=link,
                    body=md_body,
                    extra_header=f"类型: Stack Exchange 游客问答 | 标签: {tags}",
                )
                if path is not None:
                    saved.append(str(path))
        except Exception as exc:
            errors.append(f"stackexchange:{config['q']}: {exc}")

    return saved, errors


def _safe_crawl(step: str, fn, *args) -> tuple[list[str], list[str]]:
    try:
        return fn(*args)
    except Exception as exc:
        return [], [f"{step}: {exc}"]


def crawl_destination(destination: str) -> dict:
    city_key = resolve_city_key(destination)
    out_dir = CORPUS_DIR / city_key
    out_dir.mkdir(parents=True, exist_ok=True)

    osm_saved, osm_errors = _safe_crawl(
        "osm", crawl_osm_to_corpus, destination, city_key, out_dir
    )
    wikidata_saved, wikidata_errors = _safe_crawl(
        "wikidata", crawl_wikidata_to_corpus, destination, out_dir
    )
    try:
        wikidata_places = fetch_wikidata_places(destination)
        if wikidata_places:
            merge_open_attractions(city_key, wikidata_places)
    except Exception as exc:
        wikidata_errors = list(wikidata_errors) + [f"wikidata_merge: {exc}"]

    wiki_saved, wiki_errors = _safe_crawl(
        "wikipedia", crawl_wikipedia, destination, out_dir
    )
    guide_saved, guide_errors = _safe_crawl(
        "wikivoyage", crawl_wikivoyage, destination, out_dir
    )
    qa_saved, qa_errors = _safe_crawl(
        "stackexchange", crawl_stackexchange, destination, out_dir
    )

    saved = osm_saved + wikidata_saved + wiki_saved + guide_saved + qa_saved
    errors = osm_errors + wikidata_errors + wiki_errors + guide_errors + qa_errors

    try:
        chunks = rag_store.ingest_corpus_dir(out_dir)
    except Exception as exc:
        chunks = 0
        errors.append(f"ingest: {exc}")

    return {
        "destination": destination,
        "city_key": city_key,
        "saved_files": saved,
        "counts": {
            "osm": len(osm_saved),
            "wikidata": len(wikidata_saved),
            "wikipedia": len(wiki_saved),
            "wikivoyage": len(guide_saved),
            "stackexchange": len(qa_saved),
        },
        "ingested_chunks": chunks,
        "errors": errors,
    }
