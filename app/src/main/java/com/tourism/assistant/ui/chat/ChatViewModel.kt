package com.tourism.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tourism.assistant.BuildConfig
import com.tourism.assistant.data.remote.RemoteAgentRepository
import com.tourism.assistant.domain.SharedTripSession
import com.tourism.assistant.domain.model.ChatMessage
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.repository.AgentRepository
import com.tourism.assistant.domain.repository.TripPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isComplete: Boolean = false,
    val isGenerating: Boolean = false,
    val isReplying: Boolean = false,
    val error: String? = null,
    val aiBackendHint: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val tripPlanRepository: TripPlanRepository,
    private val sharedTripSession: SharedTripSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val messages = agentRepository.getChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            agentRepository.resetChat()
            val hint = when {
                !BuildConfig.USE_REMOTE_AI -> "AI 模式：本地 Mock（USE_REMOTE_AI=false）"
                agentRepository is RemoteAgentRepository && agentRepository.isUsingRemoteBackend() ->
                    "AI 模式：云端 RAG + 大模型（${BuildConfig.API_BASE_URL}）"
                agentRepository is RemoteAgentRepository ->
                    agentRepository.getLastConnectionError() ?: "AI 模式：已降级 Mock"
                else -> "AI 模式：本地 Mock"
            }
            _uiState.value = _uiState.value.copy(aiBackendHint = hint)
        }
        viewModelScope.launch {
            messages.collect { list ->
                val complete = agentRepository.isRequestComplete()
                _uiState.value = _uiState.value.copy(
                    messages = list,
                    isComplete = complete
                )
                agentRepository.buildRequestFromChat()?.let { request ->
                    sharedTripSession.update { builder ->
                        builder.mergeFrom(sharedTripSession.currentBuilder())
                        builder.origin = request.origin
                        builder.destination = request.destination
                        builder.startDate = request.dateRange.start
                        builder.endDate = request.dateRange.end
                        builder.travelers = request.travelers
                        when (val budget = request.budget) {
                            is com.tourism.assistant.domain.model.BudgetInput.Amount -> {
                                builder.budgetAmount = budget.total
                                builder.budgetLevel = null
                            }
                            is com.tourism.assistant.domain.model.BudgetInput.Level -> {
                                builder.budgetLevel = budget.level
                                builder.budgetAmount = null
                            }
                        }
                        builder.preferences = request.preferences.toMutableSet()
                        builder.specialNeeds = request.specialNeeds
                    }
                }
            }
        }
    }

    fun onInputChange(value: String) {
        _uiState.value = _uiState.value.copy(input = value)
    }

    fun sendMessage() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.isReplying) return
        _uiState.value = _uiState.value.copy(input = "", isReplying = true)
        viewModelScope.launch {
            try {
                agentRepository.sendUserMessage(text)
            } finally {
                val complete = agentRepository.isRequestComplete()
                _uiState.value = _uiState.value.copy(isReplying = false, isComplete = complete)
            }
        }
    }

    fun generatePlan(onSuccess: (TripPlan) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            try {
                val request = agentRepository.buildRequestFromChat()
                    ?: sharedTripSession.currentBuilder().build()
                if (request == null) {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = "请先完成需求对话"
                    )
                    return@launch
                }
                val plan = agentRepository.generatePlan(request)
                val id = tripPlanRepository.savePlan(plan)
                onSuccess(plan.copy(id = id))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = e.message ?: "生成失败"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isGenerating = false)
            }
        }
    }
}
