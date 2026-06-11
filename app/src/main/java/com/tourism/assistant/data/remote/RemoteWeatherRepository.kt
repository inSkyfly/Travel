package com.tourism.assistant.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tourism.assistant.domain.model.DateRange
import com.tourism.assistant.domain.model.WeatherDay
import com.tourism.assistant.domain.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteWeatherRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) : WeatherRepository {

    override suspend fun getWeatherForecast(
        destination: String,
        dateRange: DateRange
    ): List<WeatherDay> = withContext(Dispatchers.IO) {
        val (latitude, longitude) = geocodeCity(destination)
            ?: defaultCoords(destination)
        fetchForecast(latitude, longitude, dateRange)
    }

    private fun geocodeCity(destination: String): Pair<Double, Double>? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?" +
            "name=${encode(destination)}&count=1&language=zh&format=json"
        val body = httpGet(url) ?: return null
        val results = body.getAsJsonArray("results") ?: return null
        if (results.size() == 0) return null
        val first = results[0].asJsonObject
        return first.get("latitude").asDouble to first.get("longitude").asDouble
    }

    private fun defaultCoords(destination: String): Pair<Double, Double> {
        return when {
            destination.contains("成都") -> 30.5728 to 104.0668
            destination.contains("北京") -> 39.9042 to 116.4074
            destination.contains("上海") -> 31.2304 to 121.4737
            else -> 30.5728 to 104.0668
        }
    }

    private fun fetchForecast(
        latitude: Double,
        longitude: Double,
        dateRange: DateRange
    ): List<WeatherDay> {
        val url = "https://api.open-meteo.com/v1/forecast?" +
            "latitude=$latitude&longitude=$longitude" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_probability_max,wind_speed_10m_max" +
            "&timezone=Asia%2FShanghai" +
            "&start_date=${dateRange.start}" +
            "&end_date=${dateRange.end}"
        val body = httpGet(url) ?: return emptyList()
        val daily = body.getAsJsonObject("daily") ?: return emptyList()
        val dates = daily.getAsJsonArray("time") ?: return emptyList()
        val codes = daily.getAsJsonArray("weather_code")
        val highs = daily.getAsJsonArray("temperature_2m_max")
        val lows = daily.getAsJsonArray("temperature_2m_min")
        val rains = daily.getAsJsonArray("precipitation_probability_max")
        val winds = daily.getAsJsonArray("wind_speed_10m_max")

        return (0 until dates.size()).map { index ->
            val date = LocalDate.parse(dates[index].asString)
            val code = codes?.get(index)?.asInt ?: 0
            val condition = weatherCodeToText(code)
            WeatherDay(
                date = date,
                condition = condition,
                tempHigh = highs?.get(index)?.asDouble?.toInt() ?: 0,
                tempLow = lows?.get(index)?.asDouble?.toInt() ?: 0,
                precipitation = "${rains?.get(index)?.asInt ?: 0}%",
                wind = "风速 ${winds?.get(index)?.asDouble?.toInt() ?: 0} km/h",
                clothingAdvice = clothingAdvice(condition, highs?.get(index)?.asDouble?.toInt() ?: 20)
            )
        }
    }

    private fun httpGet(url: String): JsonObject? {
        return try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string() ?: return null
                gson.fromJson(text, JsonObject::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun weatherCodeToText(code: Int): String = when (code) {
        0 -> "晴"
        1, 2, 3 -> "多云"
        45, 48 -> "雾"
        51, 53, 55, 56, 57 -> "毛毛雨"
        61, 63, 65 -> "小雨"
        66, 67 -> "冻雨"
        71, 73, 75 -> "小雪"
        80, 81, 82 -> "阵雨"
        95, 96, 99 -> "雷阵雨"
        else -> "阴"
    }

    private fun clothingAdvice(condition: String, tempHigh: Int): String = when {
        condition.contains("雨") -> "携带雨具，穿防滑鞋"
        tempHigh >= 28 -> "天气炎热，建议透气短袖并注意防晒"
        tempHigh >= 22 -> "适合轻薄长袖或短袖，注意补水"
        tempHigh >= 15 -> "早晚温差较大，建议洋葱式穿搭"
        else -> "体感偏凉，建议外套保暖"
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
