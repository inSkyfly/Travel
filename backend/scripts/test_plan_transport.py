"""Quick check: generate_plan transport fields for Chengdu."""
from app.services.plan_service import generate_plan

req = {
    "origin": "北京",
    "destination": "成都",
    "dateRange": {"start": "2026-06-29", "end": "2026-07-01"},
    "travelers": 2,
    "budget": {"type": "level", "level": "COMFORT"},
    "preferences": [],
    "specialNeeds": "",
}
p = generate_plan(req)
print("planSource:", p.get("planSource"))
for day in p["dailyPlans"]:
    print("day", day["dayIndex"], day["date"])
    for a in day["activities"]:
        print(
            " ",
            a["period"],
            a["title"],
            "|",
            a.get("transportToNext"),
            "|",
            a.get("nextDestinationLat"),
        )
