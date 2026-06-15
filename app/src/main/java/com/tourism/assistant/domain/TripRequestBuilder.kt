package com.tourism.assistant.domain

import com.tourism.assistant.domain.model.BudgetInput
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.DateRange
import com.tourism.assistant.domain.model.Preference
import com.tourism.assistant.domain.model.TripRequest
import java.time.LocalDate

class TripRequestBuilder {
    var origin: String = ""
    var destination: String = ""
    var startDate: LocalDate? = null
    var endDate: LocalDate? = null
    var travelers: Int = 1
    var budgetAmount: Int? = null
    var budgetLevel: BudgetLevel? = BudgetLevel.COMFORT
    var preferences: MutableSet<Preference> = mutableSetOf()
    var specialNeeds: String = ""

    fun mergeFrom(other: TripRequestBuilder) {
        if (other.origin.isNotBlank()) origin = other.origin
        if (other.destination.isNotBlank()) destination = other.destination
        if (other.startDate != null) startDate = other.startDate
        if (other.endDate != null) endDate = other.endDate
        if (other.travelers > 0) travelers = other.travelers
        if (other.budgetAmount != null) budgetAmount = other.budgetAmount
        if (other.budgetLevel != null) budgetLevel = other.budgetLevel
        if (other.preferences.isNotEmpty()) preferences = other.preferences.toMutableSet()
        if (other.specialNeeds.isNotBlank()) specialNeeds = other.specialNeeds
    }

    fun isComplete(): Boolean {
        return origin.isNotBlank() &&
            destination.isNotBlank() &&
            startDate != null &&
            endDate != null &&
            travelers > 0 &&
            (budgetAmount != null || budgetLevel != null)
    }

    fun build(): TripRequest? {
        if (!isComplete()) return null
        return buildResolved(
            start = startDate!!,
            end = endDate!!,
            budget = budgetAmount?.let { BudgetInput.Amount(it) }
                ?: BudgetInput.Level(budgetLevel ?: BudgetLevel.COMFORT)
        )
    }

    /** 云端对话已标记完成时，缺失日期/预算用合理默认值补齐 */
    fun buildForPlan(): TripRequest? {
        if (destination.isBlank() || origin.isBlank()) return null
        val start = startDate ?: LocalDate.now().plusDays(14)
        val end = endDate ?: start.plusDays(2)
        val budget: BudgetInput = budgetAmount?.let { BudgetInput.Amount(it) }
            ?: BudgetInput.Level(budgetLevel ?: BudgetLevel.COMFORT)
        return buildResolved(start, end, budget)
    }

    private fun buildResolved(
        start: LocalDate,
        end: LocalDate,
        budget: BudgetInput
    ): TripRequest {
        return TripRequest(
            origin = origin.trim(),
            destination = destination.trim(),
            dateRange = DateRange(start, end),
            travelers = travelers.coerceAtLeast(1),
            budget = budget,
            preferences = preferences.toSet(),
            specialNeeds = specialNeeds.trim()
        )
    }

    fun toBuilderSnapshot(): TripRequestBuilder {
        return TripRequestBuilder().also {
            it.origin = origin
            it.destination = destination
            it.startDate = startDate
            it.endDate = endDate
            it.travelers = travelers
            it.budgetAmount = budgetAmount
            it.budgetLevel = budgetLevel
            it.preferences = preferences.toMutableSet()
            it.specialNeeds = specialNeeds
        }
    }
}
