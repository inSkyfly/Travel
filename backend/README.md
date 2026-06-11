# 旅游助手 AI 后端

RAG + 大模型 + Agent 服务，供 Android 客户端调用。

## 架构

```
用户问题 → Agent → RAG 检索语料 → 拼入 Prompt → 大模型 → 回答/行程
                ↑
         爬虫抓取公开网页 + 本地语料
```

## 快速启动

```bash
cd backend
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt
copy .env.example .env        # 可选：填写 LLM_API_KEY

# 构建 RAG 语料（本地样例 + 可选维基爬取）
python scripts/build_rag_corpus.py 成都

# 启动 API
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

健康检查：http://127.0.0.1:8000/api/v1/health

## 大模型配置

在 `.env` 中设置（支持 OpenAI 兼容 API，如 DeepSeek、通义、智谱）：

```
LLM_API_KEY=sk-xxx
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-chat
USE_MOCK_LLM=false
```

未配置 Key 时自动使用 **Mock LLM**（仍走 RAG 检索，便于离线演示）。

## 主要 API

| 接口 | 说明 |
|------|------|
| POST /api/v1/chat | Agent 多轮对话（RAG+LLM） |
| POST /api/v1/chat/reset | 重置会话 |
| POST /api/v1/plan/generate | 生成结构化行程 |
| POST /api/v1/rag/search | RAG 检索 |
| POST /api/v1/corpus/crawl | 爬取目的地公开资料并入库 |

## 语料来源说明

- 默认使用 `data/corpus/` 本地 Markdown 与 `data/structured/` 结构化 JSON
- 爬虫默认抓取 **维基百科** 等公开页面（请遵守 robots.txt，勿爬点评/小红书等受保护站点）
- 生产环境建议替换为合规数据源或官方 API
