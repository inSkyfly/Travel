package com.tourism.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.repository.TripPlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tripPlanRepository: TripPlanRepository
) : ViewModel() {

    val savedPlans: StateFlow<List<TripPlan>> = tripPlanRepository.getAllPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deletePlan(id: Long) {
        viewModelScope.launch {
            tripPlanRepository.deletePlan(id)
        }
    }
}
