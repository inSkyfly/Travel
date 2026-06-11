"""行程规划 LLM Prompt 与 JSON 结构说明（与 Android TripPlan 对齐）"""

from __future__ import annotations

PLAN_SYSTEM_PROMPT = """你是专业旅游行程规划师。根据「用户需求」和「参考资料摘要」生成完整行程。
要求：
1. 行程必须匹配用户指定的目的地城市，景点、美食、住宿均应在目的地当地
2. 优先依据参考资料；资料不足时可结合常识，但不要编造具体评分、精确票价，价格用合理估算并标注「约」
3. 只输出一个合法 JSON 对象，不要 markdown 代码块，不要额外解释
4. 往返交通的 departure/arrival 必须与用户出发地、目的地一致
5. dailyPlans 天数必须与用户日期范围一致，每天含上午、下午、晚上 3 个时段
6. transport.type 只能是 TRAIN 或 FLIGHT；transportMode 只能是 METRO/TAXI/WALK/BUS/NONE"""

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
          "description": "说明",
          "transportToNext": "地铁约20分钟",
          "nextDestinationName": "下一景点名",
          "nextDestinationLat": 0.0,
          "nextDestinationLng": 0.0,
          "transportMode": "METRO"
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
