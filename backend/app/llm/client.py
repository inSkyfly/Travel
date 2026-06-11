from __future__ import annotations

import json
import re
from typing import Any

import httpx
from openai import OpenAI

from app.config import LLM_API_KEY, LLM_BASE_URL, LLM_MODEL, USE_MOCK_LLM


SYSTEM_PROMPT = """你是专业旅游助手 Agent。根据用户问题和「参考资料」回答，要求：
1. 优先引用参考资料中的真实体验信息，不要编造评分/价格
2. 若资料不足，明确说明并给出通用建议
3. 多轮对话中逐步收集：目的地、出发地、日期、人数、预算、偏好、特殊需求
4. 信息收集完整时，在回复末尾单独一行输出：[[COMPLETE]]
5. 语气友好简洁，使用中文"""


class LlmClient:
    def __init__(self) -> None:
        self.use_mock = USE_MOCK_LLM
        self._client = None
        if not self.use_mock and LLM_API_KEY:
            self._client = OpenAI(api_key=LLM_API_KEY, base_url=LLM_BASE_URL)

    def chat(
        self,
        messages: list[dict[str, str]],
        rag_context: str = "",
        temperature: float = 0.7,
    ) -> str:
        if self._client is None:
            return self._mock_chat(messages, rag_context)

        system = SYSTEM_PROMPT
        if rag_context.strip():
            system += f"\n\n## 参考资料（RAG 检索）\n{rag_context}"

        payload = [{"role": "system", "content": system}] + messages
        response = self._client.chat.completions.create(
            model=LLM_MODEL,
            messages=payload,
            temperature=temperature,
        )
        return response.choices[0].message.content or ""

    def summarize_for_plan(self, user_request: dict[str, Any], rag_context: str) -> str:
        prompt = f"""根据用户需求和参考资料，生成3-5条「当地注意事项」JSON数组（仅输出JSON）：
用户需求：{json.dumps(user_request, ensure_ascii=False)}
参考资料：
{rag_context}"""
        if self._client is None:
            return json.dumps(
                [
                    "热门景点建议错峰或提前预约",
                    "尊重当地文化习俗",
                    "保管随身物品，使用正规打车平台",
                ],
                ensure_ascii=False,
            )
        text = self.chat([{"role": "user", "content": prompt}], temperature=0.3)
        match = re.search(r"\[.*\]", text, re.DOTALL)
        return match.group(0) if match else text

    def _mock_chat(self, messages: list[dict[str, str]], rag_context: str) -> str:
        last = messages[-1]["content"] if messages else ""
        ctx_hint = ""
        if rag_context.strip():
            lines = [ln.strip() for ln in rag_context.splitlines() if ln.strip()][:3]
            ctx_hint = "\n\n（基于资料摘要）" + "；".join(lines)[:200]

        lower = last.lower()
        if any(k in last for k in ("成都", "重庆", "北京", "上海", "杭州")):
            return f"好的，{last}是个不错的选择！请问您从哪个城市出发？{ctx_hint}"
        if len(last) <= 6 and not any(c.isdigit() for c in last):
            return f"收到，从{last}出发。请告诉我出行日期范围（如 0610-0614 或 6月10到14号）。"
        if re.search(r"\d", last):
            return f"已记录您的出行信息。请问一共几位出行？{ctx_hint}"
        if re.search(r"[123456789]", last):
            return "您的预算大概是？可以说金额（如5000）或：经济/舒适/豪华"
        return f"我会结合检索到的真实游记与点评资料为您规划。请继续补充需求，或回复「生成行程」。{ctx_hint}"


llm_client = LlmClient()
