package com.tourism.assistant.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tourism.assistant.domain.SharedTripSession
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.Preference
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.repository.AgentRepository
import com.tourism.assistant.domain.repository.TripPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class FormUiState(
    val origin: String = "",
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val travelers: String = "2",
    val budgetAmount: String = "",
    val budgetLevel: BudgetLevel = BudgetLevel.COMFORT,
    val useBudgetLevel: Boolean = true,
    val preferences: Set<Preference> = emptySet(),
    val specialNeeds: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FormViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val tripPlanRepository: TripPlanRepository,
    private val sharedTripSession: SharedTripSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(FormUiState())
    val uiState: StateFlow<FormUiState> = _uiState.asStateFlow()

    init {
        syncFromSession()
    }

    private fun syncFromSession() {
        val builder = sharedTripSession.currentBuilder()
        _uiState.value = FormUiState(
            origin = builder.origin,
            destination = builder.destination,
            startDate = builder.startDate,
            endDate = builder.endDate,
            travelers = builder.travelers.toString(),
            budgetAmount = builder.budgetAmount?.toString() ?: "",
            budgetLevel = builder.budgetLevel ?: BudgetLevel.COMFORT,
            useBudgetLevel = builder.budgetAmount == null,
            preferences = builder.preferences,
            specialNeeds = builder.specialNeeds
        )
    }

    fun updateOrigin(value: String) = update { copy(origin = value) }
    fun updateDestination(value: String) = update { copy(destination = value) }
    fun updateStartDate(value: LocalDate?) = update { copy(startDate = value) }
    fun updateEndDate(value: LocalDate?) = update { copy(endDate = value) }
    fun updateTravelers(value: String) = update { copy(travelers = value) }
    fun updateBudgetAmount(value: String) = update { copy(budgetAmount = value, useBudgetLevel = value.isBlank()) }
    fun updateBudgetLevel(value: BudgetLevel) = update { copy(budgetLevel = value, useBudgetLevel = true) }
    fun togglePreference(pref: Preference) = update {
        val next = preferences.toMutableSet()
        if (next.contains(pref)) next.remove(pref) else next.add(pref)
        copy(preferences = next)
    }
    fun updateSpecialNeeds(value: String) = update { copy(specialNeeds = value) }

    private fun update(transform: FormUiState.() -> FormUiState) {
        _uiState.value = _uiState.value.transform()
        persistToSession()
    }

    private fun persistToSession() {
        val state = _uiState.value
        sharedTripSession.update { builder ->
            builder.origin = state.origin
            builder.destination = state.destination
            builder.startDate = state.startDate
            builder.endDate = state.endDate
            builder.travelers = state.travelers.toIntOrNull() ?: 1
            if (state.useBudgetLevel) {
                builder.budgetLevel = state.budgetLevel
                builder.budgetAmount = null
            } else {
                builder.budgetAmount = state.budgetAmount.toIntOrNull()
                builder.budgetLevel = null
            }
            builder.preferences = state.preferences.toMutableSet()
            builder.specialNeeds = state.specialNeeds
        }
    }

    fun generatePlan(onSuccess: (TripPlan) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            try {
                val request = sharedTripSession.currentBuilder().build()
                if (request == null) {
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = "请填写完整表单信息"
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
