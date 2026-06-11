package com.tourism.assistant.data.mock

import com.tourism.assistant.domain.model.DateRange
import com.tourism.assistant.domain.model.WeatherDay
import com.tourism.assistant.domain.repository.WeatherRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockWeatherRepository @Inject constructor() : WeatherRepository {
    private val conditions = listOf("晴", "多云", "小雨", "阴")
    private val clothing = mapOf(
        "晴" to "建议穿轻薄长袖或短袖，备防晒",
        "多云" to "早晚温差较大，建议洋葱式穿搭",
        "小雨" to "携带折叠伞，穿防滑鞋",
        "阴" to "体感偏凉，可加薄外套"
    )

    override suspend fun getWeatherForecast(destination: String, dateRange: DateRange): List<WeatherDay> {
        return (0 until dateRange.dayCount).map { offset ->
            val date = dateRange.start.plusDays(offset.toLong())
            val condition = conditions[(date.dayOfYear + offset) % conditions.size]
            WeatherDay(
                date = date,
                condition = condition,
                tempHigh = 22 + (offset % 5),
                tempLow = 14 + (offset % 3),
                precipitation = if (condition == "小雨") "60%" else "10%",
                wind = "东南风 2-3级",
                clothingAdvice = clothing[condition] ?: "根据体感增减衣物"
            )
        }
    }
}
