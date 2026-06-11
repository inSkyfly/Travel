from __future__ import annotations

import json
import uuid
from dataclasses import dataclass, field
from collections.abc import Iterator
from typing import Any

from app.llm.client import llm_client
from app.rag.store import rag_store


@dataclass
class ChatSession:
    session_id: str
    messages: list[dict[str, str]] = field(default_factory=list)
    destination: str = ""
    origin: str = ""
    travelers: int = 0
    is_complete: bool = False


class TravelAgent:
    def __init__(self) -> None:
        self.sessions: dict[str, ChatSession] = {}

    def reset(self, session_id: str | None = None) -> tuple[str, str]:
        sid = session_id or str(uuid.uuid4())
        self.sessions[sid] = ChatSession(session_id=sid)
        greeting = "您好！我是您的 AI 旅游助手（RAG+大模型）。请告诉我您想去哪里旅行？"
        self.sessions[sid].messages.append({"role": "assistant", "content": greeting})
        return sid, greeting

    def chat(self, session_id: str, user_message: str) -> dict[str, Any]:
        session = self.sessions.get(session_id)
        if session is None:
            session_id, greeting = self.reset(session_id)
            session = self.sessions[session_id]
            if not user_message.strip():
                return self._response(session_id, session, greeting)

        session.messages.append({"role": "user", "content": user_message})
        self._extract_entities(session, user_message)

        query = user_message
        if session.destination:
            query = f"{session.destination} {user_message}"
        rag_context = rag_store.build_context(query, destination=session.destination or user_message)

        reply = llm_client.chat(session.messages, rag_context=rag_context)
        if "[[COMPLETE]]" in reply:
            session.is_complete = True
            reply = reply.replace("[[COMPLETE]]", "").strip()
            reply += "\n\n需求已收集完成，请点击「生成行程」。"
        session.messages.append({"role": "assistant", "content": reply})
        return self._response(session_id, session, reply)

    def chat_stream(self, session_id: str, user_message: str) -> Iterator[dict[str, Any]]:
        session = self.sessions.get(session_id)
        if session is None:
            session_id, greeting = self.reset(session_id)
            session = self.sessions[session_id]
            if not user_message.strip():
                yield {"type": "analysis", "delta": "🔍 准备会话\n"}
                yield {"type": "content", "delta": greeting}
                yield {
                    "type": "done",
                    "content": greeting,
                    "session_id": session_id,
                    "is_complete": False,
                }
                return

        session.messages.append({"role": "user", "content": user_message})
        self._extract_entities(session, user_message)

        yield {"type": "analysis", "delta": "🔍 正在理解您的问题\n"}

        query = user_message
        if session.destination:
            query = f"{session.destination} {user_message}"

        yield {"type": "analysis", "delta": "📚 正在检索相关资料…\n"}
        rag_context = rag_store.build_context(
            query, destination=session.destination or user_message
        )
        if rag_context.strip():
            snippet_count = rag_context.count("[")
            yield {
                "type": "analysis",
                "delta": f"✅ 已检索到参考资料（约 {snippet_count} 条片段）\n",
            }
        else:
            yield {"type": "analysis", "delta": "ℹ️ 未找到匹配资料，将基于通用知识回答\n"}

        yield {"type": "analysis", "delta": "📝 正在生成回答…\n"}

        raw_reply = ""
        for delta in llm_client.chat_stream(session.messages, rag_context=rag_context):
            raw_reply += delta
            yield {"type": "content", "delta": delta}

        reply = raw_reply
        if "[[COMPLETE]]" in reply:
            session.is_complete = True
            reply = reply.replace("[[COMPLETE]]", "").strip()
            reply += "\n\n需求已收集完成，请点击「生成行程」。"

        session.messages.append({"role": "assistant", "content": reply})
        meta = self._response(session_id, session, reply)
        yield {
            "type": "done",
            "content": reply,
            "session_id": session_id,
            "is_complete": meta["is_complete"],
        }

    def _response(self, session_id: str, session: ChatSession, reply: str) -> dict[str, Any]:
        return {
            "session_id": session_id,
            "message": reply,
            "is_complete": session.is_complete or self._looks_complete(session),
            "partial_request": self._partial_request(session),
            "rag_used": bool(session.destination or session.origin),
        }

    def _extract_entities(self, session: ChatSession, text: str) -> None:
        cities = ["成都", "重庆", "北京", "上海", "杭州", "西安", "广州", "深圳"]
        for city in cities:
            if city in text and not session.destination:
                session.destination = city
            elif city in text and session.destination and city != session.destination and not session.origin:
                session.origin = city
        import re

        m = re.search(r"(\d+)\s*人?", text)
        if m:
            session.travelers = int(m.group(1))

    def _looks_complete(self, session: ChatSession) -> bool:
        return bool(session.destination and session.origin and session.travelers > 0)

    def _partial_request(self, session: ChatSession) -> dict[str, Any] | None:
        if not session.destination:
            return None
        return {
            "origin": session.origin,
            "destination": session.destination,
            "travelers": session.travelers or 2,
            "specialNeeds": "",
            "preferences": [],
        }


travel_agent = TravelAgent()
