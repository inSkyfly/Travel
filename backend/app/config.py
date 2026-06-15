"""旅游助手 AI 后端：RAG + LLM + Agent"""

from pathlib import Path
import os
import re

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = BASE_DIR / "data"
CORPUS_DIR = DATA_DIR / "corpus"
CHROMA_DIR = DATA_DIR / "chroma"

LLM_API_KEY = os.getenv("LLM_API_KEY", "sk-48a534a3b7ff452fbda71f4c5200805d")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "qwen3.7-plus")
USE_MOCK_LLM = os.getenv("USE_MOCK_LLM", "false" if not LLM_API_KEY else "false").lower() == "true"

from app.config_city_keys import CITY_KEYS


def resolve_city_key(destination: str) -> str:
    key = destination.strip()
    if key in CITY_KEYS:
        return CITY_KEYS[key]
    for name, slug in CITY_KEYS.items():
        if name in key or key in name:
            return slug
    return re.sub(r"[^\w]", "", key.lower()) or "general"


# 国内可用的开放数据源（生成行程时按需自动拉取，无需手工维护 JSON）
# - OpenStreetMap：景点 / 餐饮 POI + 坐标（ODbL 开放许可）
# - Wikidata：结构化地点知识（CC0）
# - 中文维基百科 / 维基导游：攻略文本（CC BY-SA）
OPEN_DATA_SOURCES = ("osm", "wikidata", "wikipedia", "wikivoyage")
CRAWL_SOURCES: dict[str, list[str]] = {
    "成都": [
        "https://zh.wikipedia.org/wiki/成都市",
        "https://zh.wikipedia.org/wiki/都江堰",
        "https://zh.wikipedia.org/wiki/武侯祠",
        "https://zh.wikipedia.org/wiki/青城山",
    ],
    "北京": [
        "https://zh.wikipedia.org/wiki/北京市",
        "https://zh.wikipedia.org/wiki/颐和园",
        "https://zh.wikipedia.org/wiki/故宫博物院",
    ],
    "chengdu": [
        "https://zh.wikipedia.org/wiki/成都市",
        "https://zh.wikipedia.org/wiki/都江堰",
    ],
    "beijing": [
        "https://zh.wikipedia.org/wiki/北京市",
        "https://zh.wikipedia.org/wiki/颐和园",
    ],
}

# 维基导游（CC BY-SA）：交通、住宿、景点实用指南
WIKIVOYAGE_SOURCES: dict[str, list[str]] = {
    "成都": [
        "https://zh.wikivoyage.org/wiki/成都",
        "https://zh.wikivoyage.org/wiki/都江堰",
    ],
    "北京": [
        "https://zh.wikivoyage.org/wiki/北京",
        "https://zh.wikivoyage.org/wiki/颐和园",
    ],
    "chengdu": [
        "https://zh.wikivoyage.org/wiki/成都",
        "https://zh.wikivoyage.org/wiki/都江堰",
    ],
    "beijing": [
        "https://zh.wikivoyage.org/wiki/北京",
        "https://zh.wikivoyage.org/wiki/颐和园",
    ],
}

# Stack Exchange Travel 问答（CC BY-SA）：真实游客经验
STACKEXCHANGE_QUERIES: dict[str, dict[str, str]] = {
    "成都": {"q": "chengdu", "tagged": "china"},
    "北京": {"q": "beijing", "tagged": "china"},
    "上海": {"q": "shanghai", "tagged": "china"},
    "chengdu": {"q": "chengdu", "tagged": "china"},
    "beijing": {"q": "beijing", "tagged": "china"},
    "shanghai": {"q": "shanghai", "tagged": "china"},
}
