from __future__ import annotations

import hashlib
import re
from pathlib import Path
from typing import Any

import chromadb
from chromadb.config import Settings

from app.config import CHROMA_DIR, CORPUS_DIR, resolve_city_key


class RagStore:
    """向量知识库：语料分块入库 + 语义检索"""

    COLLECTION = "travel_corpus"

    def __init__(self) -> None:
        CHROMA_DIR.mkdir(parents=True, exist_ok=True)
        self.client = chromadb.PersistentClient(
            path=str(CHROMA_DIR),
            settings=Settings(anonymized_telemetry=False),
        )
        self.collection = self.client.get_or_create_collection(
            name=self.COLLECTION,
            metadata={"hnsw:space": "cosine"},
        )

    def ingest_text(
        self,
        text: str,
        metadata: dict[str, Any],
        doc_id: str | None = None,
    ) -> int:
        chunks = self._chunk(text)
        if not chunks:
            return 0
        ids, documents, metadatas = [], [], []
        for i, chunk in enumerate(chunks):
            cid = doc_id or metadata.get("source", "doc")
            chunk_id = hashlib.md5(f"{cid}_{i}_{chunk[:32]}".encode()).hexdigest()
            ids.append(chunk_id)
            documents.append(chunk)
            metadatas.append({**metadata, "chunk_index": i})
        self.collection.upsert(ids=ids, documents=documents, metadatas=metadatas)
        return len(chunks)

    def ingest_corpus_dir(self, city_dir: Path | None = None) -> int:
        total = 0
        base = city_dir or CORPUS_DIR
        if not base.exists():
            return 0
        for path in base.rglob("*"):
            if path.suffix.lower() not in (".md", ".txt", ".json"):
                continue
            text = path.read_text(encoding="utf-8")
            meta = {
                "source": str(path.relative_to(base)),
                "city": path.parent.name,
                "type": path.stem.split("_")[0] if "_" in path.stem else "general",
            }
            total += self.ingest_text(text, meta, doc_id=str(path))
        return total

    def search(
        self,
        query: str,
        destination: str = "",
        top_k: int = 6,
        doc_type: str | None = None,
    ) -> list[dict[str, Any]]:
        if self.collection.count() == 0:
            return []

        where: dict[str, Any] | None = None
        if destination:
            city_key = self._city_key(destination)
            where = {"city": city_key}

        try:
            result = self.collection.query(
                query_texts=[query],
                n_results=min(top_k, max(self.collection.count(), 1)),
                where=where,
            )
        except Exception:
            result = self.collection.query(
                query_texts=[query],
                n_results=min(top_k, max(self.collection.count(), 1)),
            )

        items: list[dict[str, Any]] = []
        docs = (result.get("documents") or [[]])[0]
        metas = (result.get("metadatas") or [[]])[0]
        dists = (result.get("distances") or [[]])[0]
        for doc, meta, dist in zip(docs, metas, dists):
            if doc_type and meta.get("type") != doc_type:
                continue
            items.append(
                {
                    "text": doc,
                    "metadata": meta,
                    "score": 1 - dist if dist is not None else 0,
                }
            )
        return items[:top_k]

    def build_context(
        self,
        query: str,
        destination: str = "",
        top_k: int = 6,
        *,
        mixed_types: bool = True,
    ) -> str:
        if mixed_types and destination:
            per_type = max(2, top_k // 3)
            hits: list[dict[str, Any]] = []
            seen: set[str] = set()
            for doc_type in ("guide", "qa", "web", "attraction"):
                for hit in self.search(
                    query, destination=destination, top_k=per_type, doc_type=doc_type
                ):
                    key = hit["text"][:80]
                    if key in seen:
                        continue
                    seen.add(key)
                    hits.append(hit)
                    if len(hits) >= top_k:
                        break
                if len(hits) >= top_k:
                    break
            if len(hits) < top_k:
                for hit in self.search(query, destination=destination, top_k=top_k):
                    key = hit["text"][:80]
                    if key in seen:
                        continue
                    seen.add(key)
                    hits.append(hit)
                    if len(hits) >= top_k:
                        break
        else:
            hits = self.search(query, destination=destination, top_k=top_k)
        if not hits:
            return ""
        parts = []
        for i, hit in enumerate(hits[:top_k], 1):
            meta = hit["metadata"]
            src = meta.get("source", "unknown")
            doc_type = meta.get("type", "general")
            parts.append(f"[{i}] 类型:{doc_type} 来源:{src}\n{hit['text']}")
        return "\n\n".join(parts)

    @staticmethod
    def _chunk(text: str, size: int = 400, overlap: int = 80) -> list[str]:
        text = re.sub(r"\s+", " ", text.strip())
        if not text:
            return []
        chunks = []
        start = 0
        while start < len(text):
            end = min(len(text), start + size)
            chunks.append(text[start:end])
            if end >= len(text):
                break
            start = end - overlap
        return chunks

    @staticmethod
    def _city_key(destination: str) -> str:
        return resolve_city_key(destination)


rag_store = RagStore()
