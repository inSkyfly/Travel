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

LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")
USE_MOCK_LLM = os.getenv("USE_MOCK_LLM", "true" if not LLM_API_KEY else "false").lower() == "true"

# 城市名 → 语料目录 / 检索 metadata 统一 key
CITY_KEYS: dict[str, str] = {
    "成都": "chengdu",
    "chengdu": "chengdu",
    "北京": "beijing",
    "beijing": "beijing",
    "上海": "shanghai",
    "shanghai": "shanghai",
    "杭州": "hangzhou",
    "hangzhou": "hangzhou",
    "西安": "xian",
    "xian": "xian",
}


def resolve_city_key(destination: str) -> str:
    key = destination.strip()
    if key in CITY_KEYS:
        return CITY_KEYS[key]
    for name, slug in CITY_KEYS.items():
        if name in key or key in name:
            return slug
    return re.sub(r"[^\w]", "", key.lower()) or "general"


# 公开资料爬取源（维基百科等开放内容，请遵守 robots.txt）
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
