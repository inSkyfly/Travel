package com.tourism.assistant.data.remote

import com.tourism.assistant.agent.ChatStateMachine
import com.tourism.assistant.data.mock.MockAgentRepository
import com.tourism.assistant.data.remote.dto.ChatResetRequestDto
import com.tourism.assistant.domain.model.ChatMessage
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.model.TripRequest
import com.tourism.assistant.domain.repository.AgentRepository
import com.tourism.assistant.util.ChatStreamRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteAgentRepository @Inject constructor(
    private val streamClient: ChatStreamClient,
    private val api: TravelApiService,
    private val mockFallback: MockAgentRepository
) : AgentRepository {

    private val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val localState = ChatStateMachine()
    private var messageId = 0L
    private var sessionId: String? = null
    private var preferRemote = true
    private var lastRemoteSuccess = false
    private var remoteDialogComplete = false
    private var lastConnectionError: String? = null

    fun isUsingRemoteBackend(): Boolean = preferRemote && lastRemoteSuccess
    fun getLastConnectionError(): String? = lastConnectionError

    override suspend fun generatePlan(request: TripRequest): TripPlan {
        if (preferRemote) {
            try {
                return api.generatePlan(request)
            } catch (_: Exception) {
            }
        }
        return mockFallback.generatePlan(request)
    }

    override fun getChatMessages(): Flow<List<ChatMessage>> = messages.asStateFlow()

    override suspend fun sendUserMessage(message: String): ChatMessage {
        val (reply, dialogComplete) = localState.processInput(message)

        val userMsg = ChatMessage(++messageId, message, isFromAgent = false)
        val agentId = ++messageId
        messages.value = messages.value + userMsg + ChatMessage(
            id = agentId,
            content = "",
            isFromAgent = true,
            isStreaming = true,
            isAnalysisExpanded = true
        )

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

    override suspend fun resetChat() {
        localState.reset()
        messageId = 0L
        lastRemoteSuccess = false
        remoteDialogComplete = false
        mockFallback.resetChat()

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
        return localState.getBuilder().build()
    }

    private fun updateAgentMessage(id: Long, transform: (ChatMessage) -> ChatMessage) {
        messages.value = messages.value.map { msg ->
            if (msg.id == id) transform(msg) else msg
        }
    }
}
