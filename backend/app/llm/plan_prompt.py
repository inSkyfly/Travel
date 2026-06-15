"""行程规划 LLM Prompt 与 JSON 结构说明（与 Android TripPlan 对齐）"""

from __future__ import annotations

PLAN_SYSTEM_PROMPT = """你是专业旅游行程规划师。根据「用户需求」和「参考资料摘要」生成完整行程。
要求：
1. 每日游玩、美食、住宿必须全部在「目的地」城市/地区（如用户成都→新疆，景点美食只能是新疆的）；出发地仅用于往返交通 departure/arrival
2. 景点、美食、住宿名称必须来自参考资料摘要中的真实地点，不要编造，更不要使用出发地的景点
3. 每日 dailyPlans 固定 3 段：上午(景点)、下午(景点)、晚上(美食/夜游)；title 用具体景点/餐厅名
4. description 写推荐理由或避坑提示，优先引用资料原文风格，不要写「说明」等占位词
5. nextDestinationName 填本时段结束后要去的下一个具体地点名；transportToNext 可留空字符串，坐标可填 0（服务端会根据资料库计算）
6. 只输出一个合法 JSON 对象，不要 markdown，不要额外解释
7. 往返交通 departure/arrival 必须与用户出发地、目的地一致
8. transport.type 只能是 TRAIN 或 FLIGHT；transportMode 只能是 METRO/TAXI/WALK/BUS/NONE"""

PLAN_USER_TEMPLATE = """## 用户需求
{request_json}

## 出行天数
{day_count} 天（{start_date} 至 {end_date}）

## 参考资料摘要（来自 RAG 检索，请优先采信）
{rag_summary}

## 输出 JSON 结构（字段名必须一致）
{schema}
"""


PLAN_JSON_SCHEMA = """
{
  "transport": {
    "outbound": {
      "type": "TRAIN",
      "number": "车次或航班号",
      "departure": "出发城市",
      "arrival": "目的地城市",
      "departTime": "08:00",
      "arriveTime": "14:00",
      "duration": "约6小时",
      "price": 500,
      "transferInfo": null,
      "bookingUrl": "https://www.12306.cn/index/"
    },
    "inbound": { "同上结构，返程" }
  },
  "dailyPlans": [
    {
      "dayIndex": 1,
      "date": "YYYY-MM-DD",
      "activities": [
        {
          "period": "上午",
          "title": "景点或活动名",
          "description": "来自资料的推荐理由或避坑提示",
          "transportToNext": "",
          "nextDestinationName": "下一具体景点或餐厅名",
          "nextDestinationLat": 0.0,
          "nextDestinationLng": 0.0,
          "transportMode": "NONE"
        },
        { "period": "下午", ... },
        { "period": "晚上", "title": "晚餐或夜游", "description": "...", "transportToNext": null, "transportMode": "NONE" }
      ]
    }
  ],
  "accommodations": [
    {
      "name": "酒店名",
      "rating": 4.5,
      "recentGoodRate": 90,
      "keywords": ["干净", "交通方便"],
      "controversyWarning": null,
      "pricePerNight": 400,
      "distanceToAttraction": "距景点约10分钟",
      "bookingUrl": "https://hotel.meituan.com/",
      "platform": "美团"
    }
  ],
  "foods": [
    {
      "name": "餐厅或小吃",
      "area": "商圈",
      "taste": "口味",
      "mealType": "正餐",
      "avgPrice": 60,
      "isLocalFavorite": true,
      "isInfluencerHype": false,
      "reason": "推荐理由",
      "avoidTips": null,
      "bookingUrl": "https://www.meituan.com/",
      "address": "",
      "latitude": 0.0,
      "longitude": 0.0,
      "platform": "大众点评"
    }
  ],
  "attractions": [
    {
      "name": "景点名",
      "tags": ["历史文化"],
      "reason": "推荐理由",
      "avoidTips": "避坑提示",
      "bestTimeSlot": "09:00-11:00",
      "ticketPrice": 50,
      "latitude": 0.0,
      "longitude": 0.0,
      "address": ""
    }
  ],
  "budgetBreakdown": {
    "total": 6000,
    "categories": [
      { "name": "交通", "allocated": 1800, "spent": 1500 },
      { "name": "住宿", "allocated": 1800, "spent": 1600 },
      { "name": "餐饮", "allocated": 1200, "spent": 1000 },
      { "name": "门票", "allocated": 800, "spent": 600 },
      { "name": "其他", "allocated": 400, "spent": 300 }
    ]
  },
  "weatherTips": [
    {
      "date": "YYYY-MM-DD",
      "condition": "多云",
      "tempHigh": 24,
      "tempLow": 16,
      "precipitation": "20%",
      "wind": "东南风2-3级",
      "clothingAdvice": "穿衣建议"
    }
  ],
  "localTips": ["当地注意事项1", "注意事项2"],
  "deepLinks": {
    "train_outbound": "https://www.12306.cn/index/",
    "hotel": "https://hotel.meituan.com/"
  }
}
"""
