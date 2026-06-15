package com.tourism.assistant.domain.model

import java.time.LocalDate

data class DateRange(
    val start: LocalDate,
    val end: LocalDate
) {
    val dayCount: Int
        get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(1)
}

enum class BudgetLevel(val label: String) {
    ECONOMY("经济"),
    COMFORT("舒适"),
    LUXURY("豪华")
}

sealed class BudgetInput {
    data class Amount(val total: Int) : BudgetInput()
    data class Level(val level: BudgetLevel) : BudgetInput()
}

enum class Preference(val label: String) {
    NATURE("自然风光"),
    HISTORY("历史文化"),
    FOOD("美食探店"),
    FAMILY("亲子乐园"),
    SPORTS("极限运动"),
    LEISURE("休闲度假")
}

data class TripRequest(
    val origin: String,
    val destination: String,
    val dateRange: DateRange,
    val travelers: Int,
    val budget: BudgetInput,
    val preferences: Set<Preference>,
    val specialNeeds: String
)

enum class TransportType { TRAIN, FLIGHT }

data class TransportSegment(
    val type: TransportType,
    val number: String,
    val departure: String,
    val arrival: String,
    val departTime: String,
    val arriveTime: String,
    val duration: String,
    val price: Int,
    val transferInfo: String? = null,
    val bookingUrl: String
)

data class TransportPlan(
    val outbound: TransportSegment,
    val inbound: TransportSegment
)

enum class LocalTransportMode {
    METRO,
    TAXI,
    WALK,
    BUS,
    NONE
}

data class TimeSlotActivity(
    val period: String,
    val title: String,
    val description: String,
    val transportToNext: String? = null,
    val nextDestinationName: String? = null,
    val nextDestinationLat: Double = 0.0,
    val nextDestinationLng: Double = 0.0,
    val transportMode: LocalTransportMode = LocalTransportMode.NONE
)

data class DailyPlan(
    val dayIndex: Int,
    val date: LocalDate,
    val activities: List<TimeSlotActivity>
)

data class HotelRec(
    val name: String,
    val rating: Double,
    val recentGoodRate: Int,
    val keywords: List<String>,
    val controversyWarning: String? = null,
    val pricePerNight: Int,
    val distanceToAttraction: String,
    val bookingUrl: String,
    val platform: String
)

data class FoodRec(
    val name: String,
    val area: String,
    val taste: String,
    val mealType: String,
    val avgPrice: Int,
    val isLocalFavorite: Boolean,
    val isInfluencerHype: Boolean,
    val reason: String,
    val avoidTips: String? = null,
    val bookingUrl: String,
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val platform: String = "大众点评",
    /** 美团/点评店铺 POI ID，用于直达店铺详情页 */
    val shopId: String? = null
)

data class BudgetCategory(
    val name: String,
    val allocated: Int,
    val spent: Int
)

data class BudgetBreakdown(
    val total: Int,
    val categories: List<BudgetCategory>
) {
    val spentTotal: Int get() = categories.sumOf { it.spent }
    val progress: Float
        get() = if (total > 0) spentTotal.toFloat() / total else 0f
}

data class WeatherDay(
    val date: LocalDate,
    val condition: String,
    val tempHigh: Int,
    val tempLow: Int,
    val precipitation: String,
    val wind: String,
    val clothingAdvice: String
)

data class AttractionRec(
    val name: String,
    val tags: List<String>,
    val reason: String,
    val avoidTips: String?,
    val bestTimeSlot: String,
    val ticketPrice: Int,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = ""
)

data class TripPlan(
    val id: Long = 0,
    val request: TripRequest,
    val transport: TransportPlan,
    val dailyPlans: List<DailyPlan>,
    val accommodations: List<HotelRec>,
    val foods: List<FoodRec>,
    val attractions: List<AttractionRec>,
    val budgetBreakdown: BudgetBreakdown,
    val weatherTips: List<WeatherDay>,
    val localTips: List<String>,
    val deepLinks: Map<String, String>,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: Long,
    val content: String,
    val isFromAgent: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    /** 流式回复进行中 */
    val isStreaming: Boolean = false,
    /** 分析过程文案（完成后默认折叠，可展开） */
    val analysisText: String = "",
    /** 分析区是否展开；流式时为 true，完成后默认 false */
    val isAnalysisExpanded: Boolean = false
)

enum class OpenResult {
    SUCCESS,
    FALLBACK_SHARE
}
