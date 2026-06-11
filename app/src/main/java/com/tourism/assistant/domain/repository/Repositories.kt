package com.tourism.assistant.domain.repository

import com.tourism.assistant.domain.model.ChatMessage
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.model.TripRequest
import com.tourism.assistant.domain.model.WeatherDay
import kotlinx.coroutines.flow.Flow

interface AgentRepository {
    suspend fun generatePlan(request: TripRequest): TripPlan
    fun getChatMessages(): Flow<List<ChatMessage>>
    suspend fun sendUserMessage(message: String): ChatMessage
    suspend fun resetChat()
    fun getPartialRequest(): Flow<TripRequest?>
    suspend fun isRequestComplete(): Boolean
    suspend fun buildRequestFromChat(): TripRequest?
}

interface RagRepository {
    suspend fun loadHotels(destination: String): List<com.tourism.assistant.domain.model.HotelRec>
    suspend fun loadFoods(destination: String): List<com.tourism.assistant.domain.model.FoodRec>
    suspend fun loadAttractions(destination: String): List<com.tourism.assistant.domain.model.AttractionRec>
}

interface WeatherRepository {
    suspend fun getWeatherForecast(destination: String, dateRange: com.tourism.assistant.domain.model.DateRange): List<WeatherDay>
}

interface TripPlanRepository {
    fun getAllPlans(): Flow<List<TripPlan>>
    suspend fun getPlanById(id: Long): TripPlan?
    suspend fun savePlan(plan: TripPlan): Long
    suspend fun deletePlan(id: Long)
}
