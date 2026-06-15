package com.tourism.assistant.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.tourism.assistant.domain.model.FoodRec
import com.tourism.assistant.domain.model.HotelRec
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
    private val meituanPackage = "com.sankuai.meituan"
    private val dianpingPackage = "com.dianping.v1"

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

    fun openHotelBooking(hotel: HotelRec): OpenResult {
        val appLinks = buildHotelAppLinks(hotel)
        return tryOpen(appLinks, hotel.bookingUrl, hotel.name)
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
        val fallbackUrl = buildFoodFallbackUrl(food)
        return tryOpen(appLinks, fallbackUrl, shareTitle)
    }

    private fun foodSearchText(food: FoodRec): String {
        return if (food.area.isNotBlank()) "${food.name} ${food.area}" else food.name
    }

    private fun meituanWebDeepLink(h5Url: String): String {
        val encoded = URLEncoder.encode(h5Url, Charsets.UTF_8.name())
        return "imeituan://www.meituan.com/web?url=$encoded"
    }

    private fun meituanPoiDetailH5(poiId: String): String =
        "https://i.meituan.com/poi/$poiId"

    /** 移动端搜索：/s/关键词（勿用 poi/search，App 内会 400） */
    private fun meituanMobileSearchH5(keyword: String): String {
        val encoded = URLEncoder.encode(keyword, Charsets.UTF_8.name())
        return "https://i.meituan.com/s/$encoded"
    }

    private fun isBrokenMeituanSearchUrl(url: String): Boolean {
        return url.contains("poi/search", ignoreCase = true) ||
            url.endsWith("meishi/") ||
            url.endsWith("meishi")
    }

    private fun resolveShopPoiId(food: FoodRec): String? {
        food.shopId?.takeIf { it.isNotBlank() }?.let { return it }
        extractDianpingShopId(food.bookingUrl)?.let { return it }
        return extractMeituanPoiId(food.bookingUrl)
    }

    private fun buildFoodFallbackUrl(food: FoodRec): String {
        val poiId = resolveShopPoiId(food)
        if (poiId != null) {
            return meituanPoiDetailH5(poiId)
        }
        if (food.bookingUrl.isNotBlank() &&
            !isBrokenMeituanSearchUrl(food.bookingUrl) &&
            food.bookingUrl.contains("meituan.com", ignoreCase = true)
        ) {
            return food.bookingUrl
        }
        val keyword = foodSearchText(food)
        return when {
            food.platform.contains("美团") -> meituanMobileSearchH5(keyword)
            food.platform.contains("点评") -> {
                val encoded = URLEncoder.encode(keyword, Charsets.UTF_8.name())
                "https://m.dianping.com/shoplist/0/searchkeyword_$encoded"
            }
            else -> meituanMobileSearchH5(keyword)
        }
    }

    private fun extractDianpingShopId(url: String): String? {
        return Regex("dianping\\.com/shop/(\\d+)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun extractMeituanPoiId(url: String): String? {
        val patterns = listOf(
            Regex("meituan\\.com/poi/(\\d+)", RegexOption.IGNORE_CASE),
            Regex("poiId=(\\d+)", RegexOption.IGNORE_CASE),
            Regex("poi_id=(\\d+)", RegexOption.IGNORE_CASE),
            Regex("id=(\\d{6,})", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val id = pattern.find(url)?.groupValues?.getOrNull(1)
            if (id != null) return id
        }
        return null
    }

    private fun buildMeituanFoodLinks(food: FoodRec): List<Pair<String, String>> {
        val poiId = resolveShopPoiId(food)
        val searchText = foodSearchText(food)
        val nameKeyword = URLEncoder.encode(food.name, Charsets.UTF_8.name())
        val searchKeyword = URLEncoder.encode(searchText, Charsets.UTF_8.name())
        val links = mutableListOf<Pair<String, String>>()

        // 优先原生 Scheme（不经过 Web 容器，避免 400）
        links += meituanPackage to "imeituan://www.meituan.com/search?q=$searchKeyword"
        links += meituanPackage to "imeituan://www.meituan.com/search?q=$nameKeyword"

        if (poiId != null) {
            links += meituanPackage to "imeituan://www.meituan.com/food/poi/detail?id=$poiId"
            links += meituanPackage to "imeituan://www.meituan.com/food/poi/detail?poiId=$poiId"
            links += meituanPackage to meituanWebDeepLink(meituanPoiDetailH5(poiId))
        }

        val mobileSearchH5 = meituanMobileSearchH5(searchText)
        links += meituanPackage to meituanWebDeepLink(mobileSearchH5)

        if (food.bookingUrl.isNotBlank() &&
            food.bookingUrl.startsWith("http", ignoreCase = true) &&
            !isBrokenMeituanSearchUrl(food.bookingUrl)
        ) {
            links += meituanPackage to meituanWebDeepLink(food.bookingUrl)
        }

        return links
    }

    private fun buildDianpingFoodLinks(food: FoodRec): List<Pair<String, String>> {
        val poiId = resolveShopPoiId(food)
        val searchText = foodSearchText(food)
        val searchKeyword = URLEncoder.encode(searchText, Charsets.UTF_8.name())
        val nameKeyword = URLEncoder.encode(food.name, Charsets.UTF_8.name())
        val links = mutableListOf<Pair<String, String>>()

        if (poiId != null) {
            links += dianpingPackage to "dianping://shopinfo?shopid=$poiId"
            links += dianpingPackage to "dianping://foodpoidetail?shopid=$poiId"
            links += dianpingPackage to "dianping://shopinfo?id=$poiId"
        }

        links += dianpingPackage to "dianping://searchshoplist?keyword=$searchKeyword"
        links += dianpingPackage to "dianping://shoplist?keyword=$nameKeyword"

        if (food.bookingUrl.startsWith("http", ignoreCase = true)) {
            links += dianpingPackage to food.bookingUrl
        }

        return links
    }

    private fun buildFoodAppLinks(food: FoodRec): List<Pair<String, String>> {
        val isMeituan =
            food.platform.contains("美团") || food.bookingUrl.contains("meituan", ignoreCase = true)
        val isDianping =
            food.platform.contains("点评") || food.bookingUrl.contains("dianping", ignoreCase = true)

        val links = mutableListOf<Pair<String, String>>()

        // 点评店铺优先走点评 App（陈麻婆等）
        if (isDianping) {
            links += buildDianpingFoodLinks(food)
        }
        if (isMeituan || !isDianping) {
            links += buildMeituanFoodLinks(food)
        }

        if (links.isEmpty()) {
            links += buildMeituanFoodLinks(food)
            links += buildDianpingFoodLinks(food)
        }

        return links.distinctBy { "${it.first}|${it.second}" }
    }

    private fun buildHotelAppLinks(hotel: HotelRec): List<Pair<String, String>> {
        val keyword = URLEncoder.encode(hotel.name, Charsets.UTF_8.name())
        val encodedWebUrl = URLEncoder.encode(hotel.bookingUrl, Charsets.UTF_8.name())
        val platform = hotel.platform

        return when {
            platform.contains("美团") -> listOf(
                meituanPackage to "imeituan://www.meituan.com/hotel/search?q=$keyword",
                meituanPackage to "imeituan://www.meituan.com/web?url=$encodedWebUrl",
                meituanPackage to "imeituan://www.meituan.com/search?q=$keyword"
            )
            platform.contains("携程") -> listOf(
                "ctrip.android.view" to "ctrip://wireless/hotel_inland_list?keyword=$keyword",
                "ctrip.android.view" to "ctrip://wireless/hotel?keyword=$keyword",
                "ctrip.android.view" to hotel.bookingUrl
            )
            platform.contains("去哪儿") -> listOf(
                "com.Qunar" to "qunaraphone://hotel/search?keyword=$keyword",
                "com.Qunar" to "qunaraphone://search?keyword=$keyword",
                "com.Qunar" to hotel.bookingUrl
            )
            else -> listOf(
                meituanPackage to "imeituan://www.meituan.com/hotel/search?q=$keyword",
                "ctrip.android.view" to "ctrip://wireless/hotel_inland_list?keyword=$keyword",
                "com.Qunar" to "qunaraphone://hotel/search?keyword=$keyword"
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
            if (intent.resolveActivity(context.packageManager) == null) {
                return false
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun launchBrowser(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) == null) {
                return false
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
