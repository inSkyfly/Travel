from __future__ import annotations

from typing import Any

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app.agent.travel_agent import travel_agent
from app.crawler.build_corpus import crawl_destination
from app.rag.store import rag_store
from app.services.plan_service import generate_plan

app = FastAPI(title="Tourism Assistant AI Backend", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatRequest(BaseModel):
    session_id: str | None = None
    message: str


class ChatResetRequest(BaseModel):
    session_id: str | None = None


class CrawlRequest(BaseModel):
    destination: str = Field(..., examples=["成都"])


class RagSearchRequest(BaseModel):
    query: str
    destination: str = ""
    top_k: int = 6


@app.get("/api/v1/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "corpus_chunks": rag_store.collection.count(),
        "use_mock_llm": __import__("app.config", fromlist=["USE_MOCK_LLM"]).USE_MOCK_LLM,
    }


@app.post("/api/v1/chat/reset")
def reset_chat(body: ChatResetRequest) -> dict[str, Any]:
    session_id, greeting = travel_agent.reset(body.session_id)
    return {"session_id": session_id, "message": greeting}


@app.post("/api/v1/chat")
def chat(body: ChatRequest) -> dict[str, Any]:
    session_id = body.session_id
    if not session_id:
        session_id, _ = travel_agent.reset()
    return travel_agent.chat(session_id, body.message)


@app.post("/api/v1/plan/generate")
def plan_generate(request: dict[str, Any]) -> dict[str, Any]:
    return generate_plan(request)


@app.post("/api/v1/rag/search")
def rag_search(body: RagSearchRequest) -> dict[str, Any]:
    hits = rag_store.search(body.query, destination=body.destination, top_k=body.top_k)
    return {"query": body.query, "results": hits}


@app.post("/api/v1/corpus/crawl")
def corpus_crawl(body: CrawlRequest) -> dict[str, Any]:
    return crawl_destination(body.destination)


@app.post("/api/v1/corpus/ingest")
def corpus_ingest() -> dict[str, Any]:
    count = rag_store.ingest_corpus_dir()
    return {"ingested_chunks": count, "total_chunks": rag_store.collection.count()}


@app.on_event("startup")
def startup_ingest() -> None:
    if rag_store.collection.count() == 0:
        rag_store.ingest_corpus_dir()
