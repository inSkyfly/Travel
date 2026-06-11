package com.tourism.assistant.ui.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tourism.assistant.domain.model.FoodRec
import com.tourism.assistant.domain.model.LocalTransportMode
import com.tourism.assistant.domain.model.TimeSlotActivity
import com.tourism.assistant.domain.model.TripPlan
import com.tourism.assistant.domain.repository.TripPlanRepository
import com.tourism.assistant.util.DeepLinkHelper
import com.tourism.assistant.util.MapNavigationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanUiState(
    val plan: TripPlan? = null,
    val isLoading: Boolean = true,
    val selectedFoodTaste: String? = null
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripPlanRepository: TripPlanRepository,
    val deepLinkHelper: DeepLinkHelper,
    val mapNavigationHelper: MapNavigationHelper
) : ViewModel() {

    private val planId: Long = savedStateHandle.get<Long>("planId") ?: -1L

    private val _uiState = MutableStateFlow(PlanUiState())
    val uiState: StateFlow<PlanUiState> = _uiState.asStateFlow()

    init {
        if (planId > 0) {
            loadPlan(planId)
        } else {
            _uiState.value = PlanUiState(isLoading = false)
        }
    }

    fun loadPlan(id: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val plan = tripPlanRepository.getPlanById(id)
            _uiState.value = PlanUiState(plan = plan, isLoading = false)
        }
    }

    fun setPlan(plan: TripPlan) {
        _uiState.value = PlanUiState(plan = plan, isLoading = false)
    }

    fun filterFoodByTaste(taste: String?) {
        _uiState.value = _uiState.value.copy(selectedFoodTaste = taste)
    }

    fun openFoodShop(food: FoodRec) {
        deepLinkHelper.openFoodShop(food)
    }

    fun openMetroRoute(activity: TimeSlotActivity) {
        mapNavigationHelper.openBaiduMapRoute(activity, LocalTransportMode.METRO)
    }

    fun openTaxiRoute(activity: TimeSlotActivity) {
        mapNavigationHelper.openBaiduMapRoute(activity, LocalTransportMode.TAXI)
    }
}
