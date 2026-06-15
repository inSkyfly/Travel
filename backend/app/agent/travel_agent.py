from __future__ import annotations

import json
import re
import uuid
from dataclasses import dataclass, field
from collections.abc import Iterator
from datetime import date
from typing import Any

from app.llm.client import llm_client
from app.util.route_parser import parse_route
from app.rag.store import rag_store


@dataclass
class ChatSession:
    session_id: str
    messages: list[dict[str, str]] = field(default_factory=list)
    destination: str = ""
    origin: str = ""
    travelers: int = 0
    start_date: str | None = None
    end_date: str | None = None
    budget_amount: int | None = None
    budget_level: str | None = None
    special_needs: str = ""
    preferences: list[str] = field(default_factory=list)
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
        self._sync_entities_from_history(session)

        query = user_message
        if session.destination:
            query = f"{session.destination} {user_message}"
        rag_context = rag_store.build_context(
            query, destination=session.destination or user_message
        )

        reply = llm_client.chat(session.messages, rag_context=rag_context)
        if "[[COMPLETE]]" in reply:
            session.is_complete = True
            reply = reply.replace("[[COMPLETE]]", "").strip()
            reply += "\n\n需求已收集完成，请点击「生成行程」。"
        session.messages.append({"role": "assistant", "content": reply})
        return self._response(session_id, session, reply)

    def chat_stream(
        self, session_id: str, user_message: str
    ) -> Iterator[dict[str, Any]]:
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
                    "partial_request": None,
                }
                return

        session.messages.append({"role": "user", "content": user_message})
        self._sync_entities_from_history(session)

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
            yield {
                "type": "analysis",
                "delta": "ℹ️ 未找到匹配资料，将基于通用知识回答\n",
            }

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
            "partial_request": meta["partial_request"],
        }

    def _response(
        self, session_id: str, session: ChatSession, reply: str
    ) -> dict[str, Any]:
        return {
            "session_id": session_id,
            "message": reply,
            "is_complete": session.is_complete or self._looks_complete(session),
            "partial_request": self._partial_request(session),
            "rag_used": bool(session.destination or session.origin),
        }

    def _sync_entities_from_history(self, session: ChatSession) -> None:
        for msg in session.messages:
            if msg.get("role") == "user":
                self._extract_entities(session, msg["content"])

    def _extract_entities(self, session: ChatSession, text: str) -> None:
        route = parse_route(text)
        if route:
            session.origin, session.destination = route
        else:
            cities = [
                "成都", "重庆", "北京", "上海", "杭州", "西安", "广州", "深圳",
                "伊犁", "新疆", "乌鲁木齐", "喀什", "拉萨", "三亚", "丽江", "大理", "厦门",
                "青岛", "南京", "苏州", "武汉", "长沙", "哈尔滨", "昆明", "桂林",
            ]
            matched = [c for c in cities if c in text]
            if len(matched) >= 2:
                session.origin = matched[0]
                session.destination = matched[-1]
            elif len(matched) == 1:
                city = matched[0]
                if not session.destination:
                    session.destination = city
                elif city != session.destination and not session.origin:
                    session.origin = city

        m = re.search(r"(\d+)\s*人", text)
        if m:
            session.travelers = int(m.group(1))

        from_match = re.search(r"从\s*([^\s，,。；;！!？?]+)", text)
        if from_match and not session.origin:
            session.origin = from_match.group(1).strip()

        date_range = self._parse_date_range(text)
        if date_range:
            session.start_date, session.end_date = date_range

        amount = re.search(r"(\d{3,6})\s*元?", text)
        if amount and session.budget_amount is None:
            session.budget_amount = int(amount.group(1))
            session.budget_level = None
        elif "经济" in text:
            session.budget_level = "ECONOMY"
            session.budget_amount = None
        elif "豪华" in text:
            session.budget_level = "LUXURY"
            session.budget_amount = None
        elif "舒适" in text:
            session.budget_level = "COMFORT"
            session.budget_amount = None

        if any(k in text for k in ("自然风光", "历史", "美食", "亲子", "运动", "休闲")):
            for pref in ("自然风光", "历史文化", "美食探店", "亲子乐园", "极限运动", "休闲度假"):
                if pref[:2] in text or pref in text:
                    if pref not in session.preferences:
                        session.preferences.append(pref)

    def _parse_date_range(self, text: str) -> tuple[str, str] | None:
        normalized = (
            text.replace(" ", "")
            .replace("号", "日")
            .replace("—", "-")
            .replace("~", "-")
        )
        iso = re.search(
            r"(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})[-到至]+(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})",
            normalized,
        )
        if iso:
            start = date(int(iso.group(1)), int(iso.group(2)), int(iso.group(3)))
            end = date(int(iso.group(4)), int(iso.group(5)), int(iso.group(6)))
            return start.isoformat(), end.isoformat()

        compact = re.search(r"(?<!\d)(\d{4})[-到至]+(\d{4})(?!\d)", normalized)
        if compact:
            year = date.today().year
            s, e = compact.group(1), compact.group(2)
            start = date(year, int(s[:2]), int(s[2:]))
            end = date(year, int(e[:2]), int(e[2:]))
            if end < start:
                end = date(year + 1, int(e[:2]), int(e[2:]))
            return start.isoformat(), end.isoformat()

        chinese = re.search(
            r"(\d{4})年(\d{1,2})月(\d{1,2})日?[-到至]+(\d{4})年(\d{1,2})月(\d{1,2})日?",
            normalized,
        )
        if chinese:
            start = date(
                int(chinese.group(1)), int(chinese.group(2)), int(chinese.group(3))
            )
            end = date(
                int(chinese.group(4)), int(chinese.group(5)), int(chinese.group(6))
            )
            return start.isoformat(), end.isoformat()

        return None

    def _looks_complete(self, session: ChatSession) -> bool:
        return bool(
            session.destination
            and session.origin
            and session.travelers > 0
            and session.start_date
            and session.end_date
        )

    def _partial_request(self, session: ChatSession) -> dict[str, Any] | None:
        if not session.destination:
            return None

        budget: dict[str, Any]
        if session.budget_amount is not None:
            budget = {"type": "amount", "total": session.budget_amount}
        else:
            budget = {
                "type": "level",
                "level": session.budget_level or "COMFORT",
            }

        date_range: dict[str, str] | None = None
        if session.start_date and session.end_date:
            date_range = {"start": session.start_date, "end": session.end_date}

        return {
            "origin": session.origin,
            "destination": session.destination,
            "dateRange": date_range,
            "travelers": session.travelers or 2,
            "budget": budget,
            "preferences": session.preferences,
            "specialNeeds": session.special_needs,
        }


travel_agent = TravelAgent()
