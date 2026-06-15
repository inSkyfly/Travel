package com.tourism.assistant.data.mock

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tourism.assistant.domain.model.AttractionRec
import com.tourism.assistant.domain.model.FoodRec
import com.tourism.assistant.domain.model.HotelRec
import com.tourism.assistant.domain.repository.RagRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockRagRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : RagRepository {

    override suspend fun loadHotels(destination: String): List<HotelRec> {
        return loadAssetList("hotels_${cityKey(destination)}.json", defaultHotels())
    }

    override suspend fun loadFoods(destination: String): List<FoodRec> {
        return loadAssetList("foods_${cityKey(destination)}.json", defaultFoods())
    }

    override suspend fun loadAttractions(destination: String): List<AttractionRec> {
        return loadAssetList("attractions_${cityKey(destination)}.json", defaultAttractions())
    }

    private inline fun <reified T> loadAssetList(fileName: String, fallback: List<T>): List<T> {
        return try {
            context.assets.open("mock/$fileName").bufferedReader().use { reader ->
                val type = object : TypeToken<List<T>>() {}.type
                gson.fromJson(reader, type)
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun cityKey(destination: String): String {
        val d = destination.trim()
        val rules = listOf(
            "成都" to "chengdu",
            "北京" to "beijing",
            "上海" to "shanghai",
            "杭州" to "hangzhou",
            "西安" to "xian",
            "广州" to "guangzhou",
            "深圳" to "shenzhen",
            "重庆" to "chongqing",
            "南京" to "nanjing",
            "苏州" to "suzhou",
            "武汉" to "wuhan",
            "长沙" to "changsha",
            "厦门" to "xiamen",
            "青岛" to "qingdao",
            "大连" to "dalian",
            "哈尔滨" to "harbin",
            "昆明" to "kunming",
            "桂林" to "guilin",
            "三亚" to "sanya",
            "丽江" to "lijiang",
            "大理" to "dali",
            "拉萨" to "lhasa",
            "新疆" to "xinjiang",
            "伊犁" to "xinjiang",
            "乌鲁木齐" to "xinjiang",
            "喀什" to "xinjiang",
        )
        for ((name, key) in rules) {
            if (d.contains(name)) return key
        }
        return d.replace(Regex("[^a-zA-Z0-9\u4e00-\u9fa5]"), "")
            .takeIf { it.isNotBlank() }
            ?: "general"
    }

    private fun defaultHotels(): List<HotelRec> = listOf(
        HotelRec(
            name = "市中心舒适酒店",
            rating = 4.5,
            recentGoodRate = 85,
            keywords = listOf("干净", "交通方便"),
            pricePerNight = 400,
            distanceToAttraction = "市中心",
            bookingUrl = "https://hotel.meituan.com/",
            platform = "美团"
        )
    )

    private fun defaultFoods(): List<FoodRec> = listOf(
        FoodRec(
            name = "本地特色餐厅",
            area = "市中心",
            taste = "清淡",
            mealType = "正餐",
            avgPrice = 60,
            isLocalFavorite = true,
            isInfluencerHype = false,
            reason = "用户评价：口味正宗",
            bookingUrl = "https://www.meituan.com/"
        )
    )

    private fun defaultAttractions(): List<AttractionRec> = listOf(
        AttractionRec(
            name = "城市地标",
            tags = listOf("历史文化"),
            reason = "热门打卡点",
            avoidTips = "建议错峰出行",
            bestTimeSlot = "09:00-11:00",
            ticketPrice = 50
        )
    )
}
