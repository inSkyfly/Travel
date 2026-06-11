package com.tourism.assistant.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import com.tourism.assistant.domain.model.LocalTransportMode
import com.tourism.assistant.domain.model.OpenResult
import com.tourism.assistant.domain.model.TimeSlotActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapNavigationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openBaiduMapRoute(activity: TimeSlotActivity, preferMode: LocalTransportMode): OpenResult {
        val destinationName = activity.nextDestinationName?.takeIf { it.isNotBlank() }
            ?: activity.title
        val lat = activity.nextDestinationLat
        val lng = activity.nextDestinationLng

        if (lat == 0.0 && lng == 0.0) {
            Toast.makeText(context, "暂无目的地坐标，无法打开地图", Toast.LENGTH_SHORT).show()
            return OpenResult.FALLBACK_SHARE
        }

        val mode = when (preferMode) {
            LocalTransportMode.METRO -> "transit"
            LocalTransportMode.TAXI -> "driving"
            LocalTransportMode.WALK -> "walking"
            else -> "transit"
        }

        val origin = buildOrigin()
        val destination = "latlng:$lat,$lng|name:${encode(destinationName)}"
        val uri = Uri.parse(
            "baidumap://map/direction?" +
                "origin=$origin&destination=$destination&mode=$mode&coord_type=gcj02"
        )

        if (launchBaiduMap(uri)) return OpenResult.SUCCESS

        val webUri = Uri.parse(
            "https://api.map.baidu.com/direction?" +
                "origin=$origin&destination=$destination&mode=$mode&coord_type=gcj02&output=html"
        )
        if (launchBrowser(webUri)) return OpenResult.SUCCESS

        Toast.makeText(context, "未安装百度地图，请先安装后重试", Toast.LENGTH_SHORT).show()
        return OpenResult.FALLBACK_SHARE
    }

    @SuppressLint("MissingPermission")
    private fun buildOrigin(): String {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        return if (location != null) {
            "latlng:${location.latitude},${location.longitude}|name:${encode("我的位置")}"
        } else {
            encode("我的位置")
        }
    }

    private fun launchBaiduMap(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(BAIDU_MAP_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun launchBrowser(uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        const val BAIDU_MAP_PACKAGE = "com.baidu.BaiduMap"
    }
}
