package com.tourism.assistant.data.remote

import com.tourism.assistant.data.remote.dto.ChatRequestDto
import com.tourism.assistant.data.remote.dto.ChatResetRequestDto
import com.tourism.assistant.data.remote.dto.ChatResponseDto
import com.tourism.assistant.data.remote.dto.CrawlRequestDto
import com.tourism.assistant.data.remote.dto.HealthResponseDto
import com.tourism.assistant.data.remote.dto.RagSearchRequestDto
import com.tourism.assistant.data.remote.dto.RagSearchResponseDto
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.model.TripRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface TravelApiService {

    @GET("api/v1/health")
    suspend fun health(): HealthResponseDto

    @POST("api/v1/chat/reset")
    suspend fun resetChat(@Body body: ChatResetRequestDto): ChatResponseDto

    @POST("api/v1/chat")
    suspend fun chat(@Body body: ChatRequestDto): ChatResponseDto

    @POST("api/v1/plan/generate")
    suspend fun generatePlan(@Body request: TripRequest): TripPlan

    @POST("api/v1/rag/search")
    suspend fun ragSearch(@Body body: RagSearchRequestDto): RagSearchResponseDto

    @POST("api/v1/corpus/crawl")
    suspend fun crawlCorpus(@Body body: CrawlRequestDto): Map<String, Any>
}
