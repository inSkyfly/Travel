from __future__ import annotations

import json
import re
from collections.abc import Iterator
from typing import Any

import httpx
from openai import OpenAI

from datetime import date, timedelta

from app.config import LLM_API_KEY, LLM_BASE_URL, LLM_MODEL, USE_MOCK_LLM
from app.llm.plan_prompt import PLAN_JSON_SCHEMA, PLAN_SYSTEM_PROMPT, PLAN_USER_TEMPLATE


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

    def chat_stream(
        self,
        messages: list[dict[str, str]],
        rag_context: str = "",
        temperature: float = 0.7,
    ) -> Iterator[str]:
        if self._client is None:
            for ch in self._mock_chat(messages, rag_context):
                yield ch
            return

        system = SYSTEM_PROMPT
        if rag_context.strip():
            system += f"\n\n## 参考资料（RAG 检索）\n{rag_context}"

        payload = [{"role": "system", "content": system}] + messages
        stream = self._client.chat.completions.create(
            model=LLM_MODEL,
            messages=payload,
            temperature=temperature,
            stream=True,
        )
        for chunk in stream:
            delta = chunk.choices[0].delta.content
            if delta:
                yield delta

    def summarize_rag_for_plan(self, destination: str, rag_context: str) -> str:
        """将 RAG 原始检索结果压缩为供行程规划使用的摘要。"""
        if not rag_context.strip():
            return f"暂无关于「{destination}」的本地参考资料。规划时请结合该城市常识，并在 localTips 中说明信息有限。"

        prompt = (
            f"请用中文总结以下关于「{destination}」的旅游资料，提炼："
            "必去景点、特色美食、住宿区域、交通方式、避坑经验、适合人群。"
            "分条列出，控制在 600 字内，不要编造资料中未出现的数据。\n\n"
            f"{rag_context}"
        )
        if self._client is None:
            lines = [ln.strip() for ln in rag_context.splitlines() if ln.strip()][:12]
            return "\n".join(lines) if lines else f"关于{destination}的公开旅游资料有限。"

        return self.chat([{"role": "user", "content": prompt}], temperature=0.2)

    def generate_trip_plan(
        self,
        request: dict[str, Any],
        rag_summary: str,
        day_count: int,
        start: date,
        end: date,
    ) -> dict[str, Any]:
        """根据用户需求 + RAG 摘要，由 LLM 生成完整行程 JSON。"""
        if self._client is None:
            return self._mock_generate_plan(request, rag_summary, day_count, start, end)

        user_prompt = PLAN_USER_TEMPLATE.format(
            request_json=json.dumps(request, ensure_ascii=False),
            day_count=day_count,
            start_date=start.isoformat(),
            end_date=end.isoformat(),
            rag_summary=rag_summary,
            schema=PLAN_JSON_SCHEMA,
        )
        response = self._client.chat.completions.create(
            model=LLM_MODEL,
            messages=[
                {"role": "system", "content": PLAN_SYSTEM_PROMPT},
                {"role": "user", "content": user_prompt},
            ],
            temperature=0.4,
        )
        text = response.choices[0].message.content or ""
        return self._extract_json_object(text)

    def _extract_json_object(self, text: str) -> dict[str, Any]:
        cleaned = text.strip()
        if cleaned.startswith("```"):
            cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
            cleaned = re.sub(r"\s*```$", "", cleaned)
        match = re.search(r"\{.*\}", cleaned, re.DOTALL)
        if not match:
            raise ValueError("LLM 未返回合法 JSON 行程")
        return json.loads(match.group(0))

    def _mock_generate_plan(
        self,
        request: dict[str, Any],
        rag_summary: str,
        day_count: int,
        start: date,
        end: date,
    ) -> dict[str, Any]:
        destination = request.get("destination", "目的地")
        origin = request.get("origin", "出发地")
        travelers = int(request.get("travelers", 2))

        names = self._extract_place_names(rag_summary, destination)
        if len(names) < 2:
            names = self._default_places_for(destination)

        foods = self._extract_food_hints(rag_summary, destination)
        daily_plans: list[dict[str, Any]] = []
        for i in range(day_count):
            d = start + timedelta(days=i)
            am = names[(i * 2) % len(names)]
            pm = names[(i * 2 + 1) % len(names)]
            evening = foods[i % len(foods)] if foods else f"{destination}本地特色餐厅"
            daily_plans.append(
                {
                    "dayIndex": i + 1,
                    "date": d.isoformat(),
                    "activities": [
                        {
                            "period": "上午",
                            "title": am,
                            "description": f"游览{am}，建议错峰出行（基于资料摘要）",
                            "transportToNext": "地铁/打车约30分钟",
                            "nextDestinationName": pm,
                            "nextDestinationLat": 0.0,
                            "nextDestinationLng": 0.0,
                            "transportMode": "METRO",
                        },
                        {
                            "period": "下午",
                            "title": pm,
                            "description": f"深度游览{pm}，留意人流高峰",
                            "transportToNext": "步行或公交",
                            "nextDestinationName": evening,
                            "nextDestinationLat": 0.0,
                            "nextDestinationLng": 0.0,
                            "transportMode": "WALK",
                        },
                        {
                            "period": "晚上",
                            "title": "品尝本地美食",
                            "description": evening,
                            "transportToNext": None,
                            "transportMode": "NONE",
                        },
                    ],
                }
            )

        attractions = [
            {
                "name": name,
                "tags": ["当地推荐"],
                "reason": "来自 RAG 资料摘要",
                "avoidTips": "建议提前预约或错峰",
                "bestTimeSlot": "09:00-11:00",
                "ticketPrice": 50,
                "latitude": 0.0,
                "longitude": 0.0,
                "address": "",
            }
            for name in names[:6]
        ]

        transport_price = 550 * travelers
        return {
            "transport": {
                "outbound": {
                    "type": "TRAIN",
                    "number": "参考车次",
                    "departure": origin,
                    "arrival": destination,
                    "departTime": "08:15",
                    "arriveTime": "14:20",
                    "duration": "约6小时（Mock 估算）",
                    "price": transport_price,
                    "transferInfo": None,
                    "bookingUrl": "https://www.12306.cn/index/",
                },
                "inbound": {
                    "type": "TRAIN",
                    "number": "参考车次",
                    "departure": destination,
                    "arrival": origin,
                    "departTime": "17:30",
                    "arriveTime": "23:45",
                    "duration": "约6小时（Mock 估算）",
                    "price": transport_price,
                    "transferInfo": None,
                    "bookingUrl": "https://www.12306.cn/index/",
                },
            },
            "dailyPlans": daily_plans,
            "accommodations": [
                {
                    "name": f"{destination}市中心舒适酒店",
                    "rating": 4.5,
                    "recentGoodRate": 88,
                    "keywords": ["交通方便", "干净"],
                    "controversyWarning": None,
                    "pricePerNight": 420,
                    "distanceToAttraction": "市中心",
                    "bookingUrl": "https://hotel.meituan.com/",
                    "platform": "美团",
                }
            ],
            "foods": [
                {
                    "name": foods[0] if foods else f"{destination}特色餐厅",
                    "area": "市中心",
                    "taste": "当地风味",
                    "mealType": "正餐",
                    "avgPrice": 60,
                    "isLocalFavorite": True,
                    "isInfluencerHype": False,
                    "reason": "资料摘要推荐",
                    "avoidTips": None,
                    "bookingUrl": "https://www.meituan.com/",
                    "address": "",
                    "latitude": 0.0,
                    "longitude": 0.0,
                    "platform": "大众点评",
                }
            ],
            "attractions": attractions,
            "budgetBreakdown": {
                "total": 6000,
                "categories": [
                    {"name": "交通", "allocated": 1800, "spent": transport_price * 2},
                    {"name": "住宿", "allocated": 1800, "spent": 420 * max(day_count - 1, 1)},
                    {"name": "餐饮", "allocated": 1200, "spent": 80 * day_count * travelers},
                    {"name": "门票", "allocated": 800, "spent": 50 * len(attractions)},
                    {"name": "其他", "allocated": 400, "spent": 300},
                ],
            },
            "weatherTips": [
                {
                    "date": (start + timedelta(days=i)).isoformat(),
                    "condition": "多云",
                    "tempHigh": 24,
                    "tempLow": 16,
                    "precipitation": "20%",
                    "wind": "微风",
                    "clothingAdvice": "早晚温差大，建议薄外套",
                }
                for i in range(day_count)
            ],
            "localTips": [
                f"{destination} 热门景点建议错峰或提前预约",
                "资料有限时请以现场信息为准（Mock 模式）",
                "使用正规平台预订交通与住宿",
            ],
            "deepLinks": {
                "train_outbound": "https://www.12306.cn/index/",
                "hotel": "https://hotel.meituan.com/",
            },
        }

    @staticmethod
    def _extract_place_names(text: str, destination: str) -> list[str]:
        candidates: list[str] = []
        for line in text.splitlines():
            line = line.strip().lstrip("-•*0123456789. ")
            if not line or destination not in line:
                continue
            for part in re.split(r"[，,、；;]", line):
                part = part.strip()
                if 2 <= len(part) <= 20 and (
                    destination in part
                    or any(
                        k in part
                        for k in ("公园", "博物馆", "寺", "山", "湖", "故宫", "长城", "广场")
                    )
                ):
                    candidates.append(part[:20])
        seen: set[str] = set()
        unique: list[str] = []
        for c in candidates:
            if c not in seen:
                seen.add(c)
                unique.append(c)
        return unique

    @staticmethod
    def _extract_food_hints(text: str, destination: str) -> list[str]:
        foods: list[str] = []
        for line in text.splitlines():
            if any(k in line for k in ("美食", "小吃", "餐厅", "火锅", "面", "烤鸭", "菜")):
                foods.append(line.strip()[:40])
        if not foods:
            foods.append(f"{destination}本地特色餐饮")
        return foods[:5]

    @staticmethod
    def _default_places_for(destination: str) -> list[str]:
        presets: dict[str, list[str]] = {
            "北京": ["故宫博物院", "颐和园", "天坛公园", "南锣鼓巷", "什刹海"],
            "成都": ["都江堰", "武侯祠", "青城山", "大熊猫繁育研究基地", "宽窄巷子"],
            "上海": ["外滩", "豫园", "南京路步行街", "迪士尼乐园", "田子坊"],
            "杭州": ["西湖", "灵隐寺", "西溪湿地", "河坊街", "千岛湖"],
        }
        for key, places in presets.items():
            if key in destination:
                return places
        return [f"{destination}市区漫步", f"{destination}地标景点", f"{destination}文化街区"]

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
