# 旅游助手 (Tourism Assistant)

Android 旅游规划应用：**AI Agent + RAG + 大模型** 生成行程，支持预算进度、天气提示与第三方 App 一键跳转。

## 技术架构

```
Android App  ──HTTP──▶  Python 后端 (FastAPI)
                           ├── Agent（多轮对话编排）
                           ├── RAG（Chroma 向量检索）
                           ├── LLM（OpenAI 兼容 API / Mock）
                           └── 爬虫（公开资料 → 语料库）
```

| 能力 | 之前（MVP） | 现在 |
|------|------------|------|
| AI Agent | 规则状态机 Mock | 后端 Agent + 本地字段解析 |
| RAG | 静态 JSON | Chroma 向量库 + 语料检索 |
| 大模型 | 无 | DeepSeek/通义等 OpenAI 兼容 API（可 Mock） |

## 功能概览

1. **需求采集**：多轮对话（RAG+LLM）或表单填写
2. **行程生成**：RAG 检索资料 + 结构化行程 + LLM 总结注意事项
3. **语料构建**：本地 Markdown/JSON + 可选维基公开页爬取
4. **第三方跳转**：12306 / 携程 / 美团等 Deep Link
5. **历史行程**：Room 本地保存

## 快速启动（完整 AI 链路）

### 1. 启动后端

```powershell
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
# 编辑 .env 填入 LLM_API_KEY（可选，无 Key 则 Mock LLM + 真 RAG）

python scripts\build_rag_corpus.py 成都
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 2. 启动 Android

`app/build.gradle.kts` 默认：

```kotlin
buildConfigField("boolean", "USE_REMOTE_AI", "true")
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")
```

- **模拟器**：`10.0.2.2` 即本机
- **真机**：改为电脑局域网 IP，如 `http://192.168.1.100:8000/`

用 Android Studio Run `app`。后端不可达时自动 **降级为 Mock**。

### 3. 关闭远程 AI（纯离线 Mock）

```kotlin
buildConfigField("boolean", "USE_REMOTE_AI", "false")
```

## 构建

```powershell
cd TourismAssistant
.\gradlew.bat assembleDebug
```

## 目录说明

| 路径 | 说明 |
|------|------|
| `backend/` | RAG + LLM + Agent API 服务 |
| `backend/data/corpus/` | RAG 语料（Markdown） |
| `backend/data/structured/` | 结构化酒店/美食/景点 JSON |
| `backend/scripts/build_rag_corpus.py` | 爬取 + 入库脚本 |
| `app/.../data/remote/` | Android 远程 API 客户端 |

## 大模型配置

见 [`backend/README.md`](backend/README.md) 与 `backend/.env.example`。

## 语料与合规

- 默认语料为本地样例 + 维基百科等**公开页面**
- 请勿爬取大众点评/小红书等受 ToS 保护站点；生产请换合规数据源

## 第三方跳转说明

各 OTA App 的 Scheme 可能变更，失败时会复制链接并唤起系统分享。
