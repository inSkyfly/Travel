package com.tourism.assistant.util

import com.tourism.assistant.domain.model.LocalTransportMode
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object LocalTransportEstimator {

    data class Result(
        val label: String,
        val mode: LocalTransportMode
    )

    fun estimate(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Result {
        if (fromLat == 0.0 && fromLng == 0.0 || toLat == 0.0 && toLng == 0.0) {
            return Result("请用地图查看具体路线", LocalTransportMode.METRO)
        }
        val dist = haversineKm(fromLat, fromLng, toLat, toLng)
        return when {
            dist < 1.2 -> {
                val mins = (dist / 4.5 * 60).toInt().coerceAtLeast(5)
                Result("步行约${mins}分钟（约${dist.formatKm()}公里）", LocalTransportMode.WALK)
            }
            dist < 12 -> {
                val mins = (dist / 22 * 60).toInt().coerceAtLeast(12) + 8
                Result("地铁约${mins}分钟（约${dist.formatKm()}公里）", LocalTransportMode.METRO)
            }
            else -> {
                val mins = (dist / 32 * 60).toInt().coerceAtLeast(20) + 10
                Result("打车约${mins}分钟（约${dist.formatKm()}公里）", LocalTransportMode.TAXI)
            }
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val p1 = lat1 * Math.PI / 180
        val p2 = lat2 * Math.PI / 180
        val dLat = (lat2 - lat1) * Math.PI / 180
        val dLon = (lon2 - lon1) * Math.PI / 180
        val a = sin(dLat / 2).pow(2) + cos(p1) * cos(p2) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }

    private fun Double.formatKm(): String = String.format("%.1f", this)
}
