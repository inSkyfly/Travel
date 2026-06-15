package com.tourism.assistant.data.mock

import com.tourism.assistant.agent.ChatStateMachine
import com.tourism.assistant.data.local.ChatSessionLocalStore
import com.tourism.assistant.data.local.ChatSessionState
import com.tourism.assistant.data.local.TripRequestBuilderSnapshot
import com.tourism.assistant.domain.model.AttractionRec
import com.tourism.assistant.domain.model.BudgetInput
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.FoodRec
import com.tourism.assistant.domain.model.LocalTransportMode
import com.tourism.assistant.domain.model.ChatMessage
import com.tourism.assistant.domain.model.DailyPlan
import com.tourism.assistant.domain.model.Preference
import com.tourism.assistant.domain.model.TimeSlotActivity
import com.tourism.assistant.domain.model.TransportPlan
import com.tourism.assistant.domain.model.TransportSegment
import com.tourism.assistant.domain.model.TransportType
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.model.TripRequest
import com.tourism.assistant.domain.repository.AgentRepository
import com.tourism.assistant.domain.repository.RagRepository
import com.tourism.assistant.domain.repository.WeatherRepository
import com.tourism.assistant.util.BudgetCalculator
import com.tourism.assistant.util.ChatStreamRenderer
import com.tourism.assistant.util.LocalTransportEstimator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MockAgentRepository @Inject constructor(
    private val ragRepository: RagRepository,
    private val weatherRepository: WeatherRepository,
    private val chatSessionStore: ChatSessionLocalStore
) : AgentRepository {

    private val chatStateMachine = ChatStateMachine()
    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private var messageId = 0L
    private var memoryInitialized = false

    override suspend fun generatePlan(request: TripRequest): TripPlan {
        val attractions = filterAttractions(
            ragRepository.loadAttractions(request.destination),
            request.preferences
        )
        val hotels = ragRepository.loadHotels(request.destination)
        val foods = ragRepository.loadFoods(request.destination)
        val weather = weatherRepository.getWeatherForecast(request.destination, request.dateRange)

        val outbound = buildTransport(request, isOutbound = true)
        val inbound = buildTransport(request, isOutbound = false)
        val transportCost = outbound.price + inbound.price

        val hotelCost = hotels.take(2).sumOf { it.pricePerNight } * (request.dateRange.dayCount - 1).coerceAtLeast(1)
        val ticketCost = attractions.take(request.dateRange.dayCount * 2).sumOf { it.ticketPrice } * request.travelers
        val foodCost = foods.take(5).sumOf { it.avgPrice } * request.dateRange.dayCount * request.travelers / 2

        val dailyPlans = buildDailyPlans(request, attractions, foods)
        val budgetBreakdown = BudgetCalculator.buildBreakdown(
            budget = request.budget,
            dateRange = request.dateRange,
            travelers = request.travelers,
            transportCost = transportCost,
            hotelCost = hotelCost,
            foodCost = foodCost,
            ticketCost = ticketCost
        )

        val deepLinks = mapOf(
            "train_outbound" to outbound.bookingUrl,
            "train_inbound" to inbound.bookingUrl,
            "hotel" to (hotels.firstOrNull()?.bookingUrl ?: "https://hotel.meituan.com/"),
            "food" to (foods.firstOrNull()?.bookingUrl ?: "https://www.meituan.com/")
        )

        return TripPlan(
            request = request,
            transport = TransportPlan(outbound, inbound),
            dailyPlans = dailyPlans,
            accommodations = hotels,
            foods = foods,
            attractions = attractions,
            budgetBreakdown = budgetBreakdown,
            weatherTips = weather,
            localTips = buildLocalTips(request.destination),
            deepLinks = deepLinks
        )
    }

    override fun getChatMessages(): Flow<List<ChatMessage>> = messages.asStateFlow()

    override suspend fun ensureChatInitialized() {
        if (memoryInitialized) return
        memoryInitialized = true

        val saved = chatSessionStore.load()
        if (saved != null && saved.messages.isNotEmpty()) {
            restoreFromState(saved)
            return
        }
        startNewConversationInternal(persist = true)
    }

    override suspend fun startNewConversation() {
        chatSessionStore.clear()
        startNewConversationInternal(persist = true)
    }

    /** 云端仓库重置会话时同步本地 Mock 状态，不写入持久化 */
    internal fun resetForFallback() {
        chatStateMachine.reset()
        messageId = 0L
    }

    override suspend fun sendUserMessage(message: String): ChatMessage {
        if (!memoryInitialized) ensureChatInitialized()

        val userMsg = ChatMessage(++messageId, message, isFromAgent = false)
        val agentId = ++messageId
        val (reply, _) = chatStateMachine.processInput(message)

        messages.value = messages.value + userMsg + ChatMessage(
            id = agentId,
            content = "",
            isFromAgent = true,
            isStreaming = true,
            isAnalysisExpanded = true
        )
        persistState()

        ChatStreamRenderer.streamReply(
            analysisLines = listOf(
                "🔍 正在理解您的需求",
                "📚 检索本地资料库",
                "📝 正在组织回答"
            ),
            reply = reply,
            charDelayMs = 10L,
            onUpdate = { analysis, content, streaming ->
                updateMessage(agentId) { msg ->
                    msg.copy(
                        analysisText = analysis,
                        content = content,
                        isStreaming = streaming,
                        isAnalysisExpanded = true
                    )
                }
            }
        )

        val lastMsg = messages.value.last { it.id == agentId }
        val finalMsg = lastMsg.copy(
            content = reply,
            analysisText = lastMsg.analysisText,
            isStreaming = false,
            isAnalysisExpanded = false
        )
        messages.value = messages.value.map { if (it.id == agentId) finalMsg else it }
        persistState()
        return finalMsg
    }

    private fun updateMessage(id: Long, transform: (ChatMessage) -> ChatMessage) {
        messages.value = messages.value.map { if (it.id == id) transform(it) else it }
    }

    private suspend fun startNewConversationInternal(persist: Boolean) {
        chatStateMachine.reset()
        messageId = 0L
        val greeting = chatStateMachine.startConversation()
        messages.value = listOf(
            ChatMessage(++messageId, greeting, isFromAgent = true)
        )
        if (persist) persistState()
    }

    private fun restoreFromState(state: ChatSessionState) {
        messageId = state.nextMessageId
        messages.value = state.messages.map { msg ->
            if (msg.isStreaming) {
                msg.copy(isStreaming = false, isAnalysisExpanded = false)
            } else {
                msg
            }
        }
        chatStateMachine.restore(state.chatStep, state.preferenceBuffer, state.builder)
    }

    private suspend fun persistState() {
        chatSessionStore.save(
            ChatSessionState(
                sessionId = null,
                messages = messages.value,
                nextMessageId = messageId,
                chatStep = chatStateMachine.currentStep().name,
                preferenceBuffer = chatStateMachine.exportPreferenceBuffer().toList(),
                builder = TripRequestBuilderSnapshot.from(chatStateMachine.getBuilder()),
                remoteDialogComplete = chatStateMachine.isDialogComplete()
            )
        )
    }

    override fun getPartialRequest(): Flow<TripRequest?> {
        return messages.map { chatStateMachine.getBuilder().build() }
    }

    override suspend fun isRequestComplete(): Boolean {
        return chatStateMachine.isDialogComplete() || chatStateMachine.getBuilder().isComplete()
    }

    override suspend fun buildRequestFromChat(): TripRequest? {
        val builder = chatStateMachine.getBuilder()
        return if (chatStateMachine.isDialogComplete()) {
            builder.buildForPlan()
        } else {
            builder.build()
        }
    }

    private fun filterAttractions(
        attractions: List<AttractionRec>,
        preferences: Set<Preference>
    ): List<AttractionRec> {
        if (preferences.isEmpty()) return attractions
        val prefLabels = preferences.map { it.label }
        val filtered = attractions.filter { attraction ->
            attraction.tags.any { tag -> prefLabels.any { tag.contains(it) || it.contains(tag) } }
        }
        return filtered.ifEmpty { attractions }
    }

    private fun buildTransport(request: TripRequest, isOutbound: Boolean): TransportSegment {
        val useFlight = request.origin.length > 4 || request.destination.length > 4
        val type = if (useFlight) TransportType.FLIGHT else TransportType.TRAIN
        val priceBase = when (request.budget) {
            is BudgetInput.Amount -> request.budget.total / 10
            is BudgetInput.Level -> when (request.budget.level) {
                com.tourism.assistant.domain.model.BudgetLevel.ECONOMY -> 350
                com.tourism.assistant.domain.model.BudgetLevel.COMFORT -> 550
                com.tourism.assistant.domain.model.BudgetLevel.LUXURY -> 900
            }
        }
        return if (type == TransportType.TRAIN) {
            TransportSegment(
                type = TransportType.TRAIN,
                number = if (isOutbound) "G308" else "G309",
                departure = if (isOutbound) request.origin else request.destination,
                arrival = if (isOutbound) request.destination else request.origin,
                departTime = if (isOutbound) "08:15" else "17:30",
                arriveTime = if (isOutbound) "14:20" else "23:45",
                duration = "约6小时",
                price = priceBase * request.travelers,
                transferInfo = null,
                bookingUrl = "https://www.12306.cn/index/"
            )
        } else {
            TransportSegment(
                type = TransportType.FLIGHT,
                number = if (isOutbound) "CA4102" else "CA4103",
                departure = if (isOutbound) request.origin else request.destination,
                arrival = if (isOutbound) request.destination else request.origin,
                departTime = if (isOutbound) "09:40" else "18:10",
                arriveTime = if (isOutbound) "12:05" else "20:35",
                duration = "约2.5小时",
                price = priceBase * request.travelers,
                transferInfo = "直飞",
                bookingUrl = "https://m.ctrip.com/html5/flight/swift/index"
            )
        }
    }

    private fun buildDailyPlans(
        request: TripRequest,
        attractions: List<AttractionRec>,
        foods: List<FoodRec>
    ): List<DailyPlan> {
        val list = attractions.ifEmpty {
            listOf(
                AttractionRec("市区漫步", listOf("休闲度假"), "轻松了解城市", null, "全天", 0)
            )
        }
        return (0 until request.dateRange.dayCount).map { dayIndex ->
            val date = request.dateRange.start.plusDays(dayIndex.toLong())
            val morning = list.getOrNull(dayIndex * 2) ?: list.first()
            val afternoon = list.getOrNull(dayIndex * 2 + 1) ?: list.getOrNull(1) ?: list.first()
            val eveningFood = foods.getOrNull(dayIndex % foods.size.coerceAtLeast(1))
            val amToPm = LocalTransportEstimator.estimate(
                morning.latitude, morning.longitude,
                afternoon.latitude, afternoon.longitude
            )
            val pmToEvening = LocalTransportEstimator.estimate(
                afternoon.latitude, afternoon.longitude,
                eveningFood?.latitude ?: 0.0,
                eveningFood?.longitude ?: 0.0
            )
            DailyPlan(
                dayIndex = dayIndex + 1,
                date = date,
                activities = listOf(
                    TimeSlotActivity(
                        period = "上午",
                        title = morning.name,
                        description = morning.reason,
                        transportToNext = amToPm.label,
                        nextDestinationName = afternoon.name,
                        nextDestinationLat = afternoon.latitude,
                        nextDestinationLng = afternoon.longitude,
                        transportMode = amToPm.mode
                    ),
                    TimeSlotActivity(
                        period = "下午",
                        title = afternoon.name,
                        description = afternoon.avoidTips ?: morning.reason,
                        transportToNext = pmToEvening.label,
                        nextDestinationName = eveningFood?.name ?: "晚餐餐厅",
                        nextDestinationLat = eveningFood?.latitude ?: 0.0,
                        nextDestinationLng = eveningFood?.longitude ?: 0.0,
                        transportMode = pmToEvening.mode
                    ),
                    TimeSlotActivity(
                        period = "晚上",
                        title = "品尝本地美食",
                        description = eveningFood?.reason ?: "推荐附近本地人常去餐厅，避免网红排队",
                        transportToNext = null,
                        transportMode = LocalTransportMode.NONE
                    )
                )
            )
        }
    }

    private fun buildLocalTips(destination: String): List<String> {
        return listOf(
            "$destination 出行请注意保管随身物品，景区人流密集时谨防扒窃",
            "尊重当地文化习俗，进入宗教场所着装得体",
            "小额消费可备现金，部分老店不支持移动支付",
            "热门景点建议提前预约，避免现场限流",
            "如需打车，请通过正规平台叫车并核对车牌"
        )
    }
}
