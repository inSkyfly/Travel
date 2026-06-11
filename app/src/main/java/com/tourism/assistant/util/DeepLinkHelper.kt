package com.tourism.assistant.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tourism.assistant.domain.model.FoodRec
import com.tourism.assistant.domain.model.OpenResult
import com.tourism.assistant.domain.model.TransportSegment
import com.tourism.assistant.domain.model.TransportType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepLinkHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openTransportBooking(segment: TransportSegment): OpenResult {
        val packages = when (segment.type) {
            TransportType.TRAIN -> listOf(
                "com.MobileTicket" to segment.bookingUrl,
                "ctrip.android.view" to "ctrip://wireless/train"
            )
            TransportType.FLIGHT -> listOf(
                "ctrip.android.view" to "ctrip://wireless/flight",
                "com.umetrip.android.msky.app" to segment.bookingUrl
            )
        }
        return tryOpen(packages, segment.bookingUrl, "${segment.number} ${segment.departure}-${segment.arrival}")
    }

    fun openHotelBooking(url: String, hotelName: String): OpenResult {
        return tryOpen(
            listOf(
                "com.sankuai.meituan" to url,
                "com.Qunar" to url,
                "ctrip.android.view" to url
            ),
            url,
            hotelName
        )
    }

    fun openFoodBooking(url: String, shopName: String): OpenResult {
        return openFoodShop(
            FoodRec(
                name = shopName,
                area = "",
                taste = "",
                mealType = "",
                avgPrice = 0,
                isLocalFavorite = false,
                isInfluencerHype = false,
                reason = "",
                bookingUrl = url
            )
        )
    }

    fun openFoodShop(food: FoodRec): OpenResult {
        val appLinks = buildFoodAppLinks(food)
        val shareTitle = "${food.name} · ${food.area} · 人均¥${food.avgPrice}"
        return tryOpen(appLinks, food.bookingUrl, shareTitle)
    }

    private fun buildFoodAppLinks(food: FoodRec): List<Pair<String, String>> {
        val keyword = URLEncoder.encode(food.name, Charsets.UTF_8.name())
        val isMeituan = food.platform.contains("美团")
        val latLngPart = if (food.latitude != 0.0 && food.longitude != 0.0) {
            "&lat=${food.latitude}&lng=${food.longitude}"
        } else {
            ""
        }

        return if (isMeituan) {
            listOf(
                "com.sankuai.meituan" to "imeituan://www.meituan.com/web?url=${
                    URLEncoder.encode(food.bookingUrl, Charsets.UTF_8.name())
                }",
                "com.sankuai.meituan" to "imeituan://www.meituan.com/search?q=$keyword",
                "com.dianping.v1" to food.bookingUrl
            )
        } else {
            listOf(
                "com.dianping.v1" to "dianping://shoplist?keyword=$keyword$latLngPart&cityid=8",
                "com.dianping.v1" to food.bookingUrl,
                "com.sankuai.meituan" to food.bookingUrl
            )
        }
    }

    private fun tryOpen(
        packageUrlPairs: List<Pair<String, String>>,
        fallbackUrl: String,
        shareTitle: String
    ): OpenResult {
        for ((pkg, url) in packageUrlPairs) {
            if (launchApp(pkg, url)) return OpenResult.SUCCESS
        }
        if (launchBrowser(fallbackUrl)) return OpenResult.SUCCESS
        ShareHelper(context).copyAndShare(fallbackUrl, shareTitle)
        return OpenResult.FALLBACK_SHARE
    }

    private fun launchApp(packageName: String, url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    true
                } else {
                    false
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun launchBrowser(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

class ShareHelper(private val context: Context) {
    fun copyAndShare(url: String, title: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("booking_link", url))
        Toast.makeText(context, "链接已复制，请选择应用打开", Toast.LENGTH_SHORT).show()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(shareIntent, "分享行程链接").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
