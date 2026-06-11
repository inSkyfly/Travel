#!/usr/bin/env python3
"""一键：爬取公开资料 -> 写入语料 -> 入库 RAG"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.crawler.build_corpus import crawl_destination  # noqa: E402
from app.rag.store import rag_store  # noqa: E402


def main() -> None:
    destination = sys.argv[1] if len(sys.argv) > 1 else "成都"
    print(f"正在爬取并入库：{destination}")
    print("数据源：维基百科(web) + 维基导游(guide) + Stack Exchange(qa)")
    result = crawl_destination(destination)
    print("爬取结果:", result)
    extra = rag_store.ingest_corpus_dir()
    print(f"语料库共 {rag_store.collection.count()} 条向量块（全库补充入库 {extra} 块）")


if __name__ == "__main__":
    main()
