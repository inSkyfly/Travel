package com.tourism.assistant.data.remote

import com.tourism.assistant.BuildConfig
import com.tourism.assistant.data.remote.dto.RagSearchRequestDto
import com.tourism.assistant.domain.model.AttractionRec
import com.tourism.assistant.domain.model.FoodRec
import com.tourism.assistant.domain.model.HotelRec
import com.tourism.assistant.domain.repository.RagRepository
import com.tourism.assistant.data.mock.MockRagRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAG 检索：优先调用后端向量库；结构化列表仍用本地 JSON 兜底。
 */
@Singleton
class RemoteRagRepository @Inject constructor(
    private val api: TravelApiService,
    private val mockRagRepository: MockRagRepository
) : RagRepository {

    override suspend fun loadHotels(destination: String): List<HotelRec> {
        searchAndLog(destination, "住宿 酒店 评价")
        return mockRagRepository.loadHotels(destination)
    }

    override suspend fun loadFoods(destination: String): List<FoodRec> {
        searchAndLog(destination, "美食 餐厅 本地人推荐")
        return mockRagRepository.loadFoods(destination)
    }

    override suspend fun loadAttractions(destination: String): List<AttractionRec> {
        searchAndLog(destination, "景点 游记 避坑")
        return mockRagRepository.loadAttractions(destination)
    }

    private suspend fun searchAndLog(destination: String, query: String) {
        if (!BuildConfig.USE_REMOTE_AI) return
        try {
            api.ragSearch(RagSearchRequestDto(query = query, destination = destination))
        } catch (_: Exception) {
            // 后端不可用时静默降级
        }
    }
}
