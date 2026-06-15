package com.tourism.assistant.data.remote

import com.tourism.assistant.agent.ChatStateMachine
import com.tourism.assistant.data.local.ChatSessionLocalStore
import com.tourism.assistant.data.local.ChatSessionState
import com.tourism.assistant.data.local.TripRequestBuilderSnapshot
import com.tourism.assistant.data.mock.MockAgentRepository
import com.tourism.assistant.data.remote.dto.ChatResetRequestDto
import com.tourism.assistant.domain.model.ChatMessage
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.model.TripRequest
import com.tourism.assistant.domain.repository.AgentRepository
import com.tourism.assistant.util.ChatStreamRenderer
import com.tourism.assistant.util.FlexibleDateParser
import com.tourism.assistant.util.TripRouteParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteAgentRepository @Inject constructor(
    private val streamClient: ChatStreamClient,
    private val api: TravelApiService,
    private val mockFallback: MockAgentRepository,
    private val chatSessionStore: ChatSessionLocalStore
) : AgentRepository {

    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val localState = ChatStateMachine()
    private var messageId = 0L
    private var sessionId: String? = null
    private var preferRemote = true
    private var lastRemoteSuccess = false
    private var remoteDialogComplete = false
    private var lastConnectionError: String? = null
    private var memoryInitialized = false

    fun isUsingRemoteBackend(): Boolean = preferRemote && lastRemoteSuccess
    fun getLastConnectionError(): String? = lastConnectionError
    fun hasPersistedSession(): Boolean = sessionId != null || messages.value.isNotEmpty()

    private var lastPlanError: String? = null

    fun getLastPlanError(): String? = lastPlanError

    override suspend fun generatePlan(request: TripRequest): TripPlan {
        if (!preferRemote) {
            return mockFallback.generatePlan(request)
        }
        try {
            lastPlanError = null
            return api.generatePlan(request)
        } catch (e: Exception) {
            lastPlanError = "云端不可用，已改用本地资料生成行程"
            return mockFallback.generatePlan(request)
        }
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

    override suspend fun sendUserMessage(message: String): ChatMessage {
        if (!memoryInitialized) ensureChatInitialized()

        val (reply, dialogComplete) = localState.processInput(message)
        TripRouteParser.parse(message)?.let { (origin, destination) ->
            localState.getBuilder().origin = origin
            localState.getBuilder().destination = destination
        }
        FlexibleDateParser.parseRange(message)?.let { (start, end) ->
            localState.getBuilder().startDate = start
            localState.getBuilder().endDate = end
        }

        val userMsg = ChatMessage(++messageId, message, isFromAgent = false)
        val agentId = ++messageId
        messages.value = messages.value + userMsg + ChatMessage(
            id = agentId,
            content = "",
            isFromAgent = true,
            isStreaming = true,
            isAnalysisExpanded = true
        )
        persistState()

        if (preferRemote) {
            try {
                return streamFromRemote(agentId, message, dialogComplete)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastConnectionError = "云端暂不可用（${e.message ?: "连接失败"}），已用本地流式回复"
                lastRemoteSuccess = false
            }
        }

        return streamLocalReply(agentId, reply, dialogComplete)
    }

    private suspend fun streamFromRemote(
        agentId: Long,
        message: String,
        dialogComplete: Boolean
    ): ChatMessage {
        var analysisBuffer = ""
        var contentBuffer = ""
        var finalContent = ""
        streamClient.streamChat(sessionId, message) { event ->
            when (event.type) {
                "analysis" -> {
                    analysisBuffer += event.delta
                    pushStreamingUpdate(agentId, analysisBuffer, contentBuffer)
                }
                "content" -> {
                    contentBuffer += event.delta
                    finalContent = contentBuffer
                    pushStreamingUpdate(agentId, analysisBuffer, contentBuffer)
                }
                "done" -> {
                    if (event.session_id.isNotBlank()) sessionId = event.session_id
                    if (event.is_complete || dialogComplete) remoteDialogComplete = true
                    event.partial_request?.let { applyPartialRequest(it) }
                    finalContent = event.content.ifBlank { finalContent }
                    if (finalContent.isNotBlank() && contentBuffer != finalContent) {
                        contentBuffer = finalContent
                        pushStreamingUpdate(agentId, analysisBuffer, contentBuffer)
                    }
                    finalizeAgentMessage(agentId, finalContent, analysisBuffer)
                }
            }
        }
        lastRemoteSuccess = true
        lastConnectionError = null
        persistState()
        return messages.value.last { it.id == agentId }
    }

    private suspend fun pushStreamingUpdate(
        agentId: Long,
        analysisText: String,
        content: String
    ) {
        updateAgentMessage(agentId) { msg ->
            msg.copy(
                analysisText = analysisText,
                content = content,
                isAnalysisExpanded = true,
                isStreaming = true
            )
        }
        delay(10)
    }

    private suspend fun streamLocalReply(
        agentId: Long,
        reply: String,
        dialogComplete: Boolean
    ): ChatMessage {
        val existingAnalysis = messages.value.find { it.id == agentId }?.analysisText.orEmpty()
        val onUpdate: (String, String, Boolean) -> Unit = { analysis, content, streaming ->
            updateAgentMessage(agentId) { msg ->
                msg.copy(
                    analysisText = analysis,
                    content = content,
                    isStreaming = streaming,
                    isAnalysisExpanded = true
                )
            }
        }
        if (existingAnalysis.isNotBlank()) {
            ChatStreamRenderer.streamContentOnly(
                preservedAnalysis = existingAnalysis,
                reply = reply,
                charDelayMs = 10L,
                onUpdate = onUpdate
            )
        } else {
            ChatStreamRenderer.streamReply(
                analysisLines = listOf(
                    "🔍 正在理解您的问题",
                    "📚 检索本地资料库",
                    "📝 正在组织回答"
                ),
                reply = reply,
                charDelayMs = 10L,
                onUpdate = onUpdate
            )
        }
        val analysisText = messages.value.find { it.id == agentId }?.analysisText.orEmpty()
        finalizeAgentMessage(agentId, reply, analysisText)
        if (dialogComplete) remoteDialogComplete = true
        persistState()
        return messages.value.last { it.id == agentId }
    }

    private fun finalizeAgentMessage(
        agentId: Long,
        content: String,
        analysisText: String? = null
    ) {
        updateAgentMessage(agentId) { msg ->
            val preservedAnalysis = when {
                !analysisText.isNullOrBlank() -> analysisText
                msg.analysisText.isNotBlank() -> msg.analysisText
                else -> ""
            }
            msg.copy(
                content = content,
                analysisText = preservedAnalysis,
                isStreaming = false,
                isAnalysisExpanded = false
            )
        }
    }

    private suspend fun startNewConversationInternal(persist: Boolean) {
        sessionId = null
        localState.reset()
        messageId = 0L
        remoteDialogComplete = false
        mockFallback.resetForFallback()

        if (preferRemote) {
            try {
                val response = api.resetChat(ChatResetRequestDto(session_id = sessionId))
                sessionId = response.session_id
                localState.startConversation()
                lastConnectionError = null
                lastRemoteSuccess = true
                messages.value = listOf(
                    ChatMessage(++messageId, response.message, isFromAgent = true)
                )
                if (persist) persistState()
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastConnectionError = "云端连接失败：${e.message ?: "请检查 IP/端口"}"
                lastRemoteSuccess = false
            }
        }

        val greeting = localState.startConversation()
        messages.value = listOf(
            ChatMessage(++messageId, greeting, isFromAgent = true)
        )
        if (persist) persistState()
    }

    private fun restoreFromState(state: ChatSessionState) {
        messageId = state.nextMessageId
        sessionId = state.sessionId
        remoteDialogComplete = state.remoteDialogComplete
        messages.value = state.messages.map { msg ->
            if (msg.isStreaming) {
                msg.copy(isStreaming = false, isAnalysisExpanded = false)
            } else {
                msg
            }
        }
        localState.restore(state.chatStep, state.preferenceBuffer, state.builder)
        if (sessionId != null) {
            lastRemoteSuccess = true
            lastConnectionError = null
        }
    }

    private suspend fun persistState() {
        chatSessionStore.save(
            ChatSessionState(
                sessionId = sessionId,
                messages = messages.value,
                nextMessageId = messageId,
                chatStep = localState.currentStep().name,
                preferenceBuffer = localState.exportPreferenceBuffer().toList(),
                builder = TripRequestBuilderSnapshot.from(localState.getBuilder()),
                remoteDialogComplete = remoteDialogComplete
            )
        )
    }

    override fun getPartialRequest(): Flow<TripRequest?> {
        return messages.map { localState.getBuilder().build() }
    }

    override suspend fun isRequestComplete(): Boolean {
        return remoteDialogComplete ||
            localState.isDialogComplete() ||
            localState.getBuilder().isComplete()
    }

    override suspend fun buildRequestFromChat(): TripRequest? {
        val builder = localState.getBuilder()
        return if (remoteDialogComplete || localState.isDialogComplete()) {
            builder.buildForPlan()
        } else {
            builder.build()
        }
    }

    private fun applyPartialRequest(partial: Map<String, Any>) {
        val builder = localState.getBuilder()
        (partial["origin"] as? String)?.takeIf { it.isNotBlank() }?.let { builder.origin = it }
        (partial["destination"] as? String)?.takeIf { it.isNotBlank() }?.let {
            builder.destination = it
        }
        when (val travelers = partial["travelers"]) {
            is Int -> if (travelers > 0) builder.travelers = travelers
            is Double -> if (travelers > 0) builder.travelers = travelers.toInt()
            is Number -> if (travelers.toInt() > 0) builder.travelers = travelers.toInt()
        }
        (partial["specialNeeds"] as? String)?.let { builder.specialNeeds = it }

        val dateRange = partial["dateRange"] as? Map<*, *>
        if (dateRange != null) {
            (dateRange["start"] as? String)?.let { text ->
                runCatching { LocalDate.parse(text) }.getOrNull()?.let { builder.startDate = it }
            }
            (dateRange["end"] as? String)?.let { text ->
                runCatching { LocalDate.parse(text) }.getOrNull()?.let { builder.endDate = it }
            }
        }

        val budget = partial["budget"] as? Map<*, *>
        if (budget != null) {
            when (budget["type"] as? String) {
                "amount" -> (budget["total"] as? Number)?.toInt()?.let { total ->
                    builder.budgetAmount = total
                    builder.budgetLevel = null
                }
                "level" -> (budget["level"] as? String)?.let { levelName ->
                    runCatching { BudgetLevel.valueOf(levelName) }.getOrNull()?.let { level ->
                        builder.budgetLevel = level
                        builder.budgetAmount = null
                    }
                }
            }
        }
    }

    private fun updateAgentMessage(id: Long, transform: (ChatMessage) -> ChatMessage) {
        messages.value = messages.value.map { msg ->
            if (msg.id == id) transform(msg) else msg
        }
    }
}
