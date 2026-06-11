package com.tourism.assistant.data.local

import com.tourism.assistant.domain.TripRequestBuilder
import com.tourism.assistant.domain.model.BudgetLevel
import com.tourism.assistant.domain.model.ChatMessage
import com.tourism.assistant.domain.model.Preference
import java.time.LocalDate

data class TripRequestBuilderSnapshot(
    val origin: String = "",
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val travelers: Int = 1,
    val budgetAmount: Int? = null,
    val budgetLevel: BudgetLevel? = BudgetLevel.COMFORT,
    val preferences: List<Preference> = emptyList(),
    val specialNeeds: String = ""
) {
    fun applyTo(builder: TripRequestBuilder) {
        builder.origin = origin
        builder.destination = destination
        builder.startDate = startDate
        builder.endDate = endDate
        builder.travelers = travelers
        builder.budgetAmount = budgetAmount
        builder.budgetLevel = budgetLevel
        builder.preferences = preferences.toMutableSet()
        builder.specialNeeds = specialNeeds
    }

    companion object {
        fun from(builder: TripRequestBuilder): TripRequestBuilderSnapshot {
            return TripRequestBuilderSnapshot(
                origin = builder.origin,
                destination = builder.destination,
                startDate = builder.startDate,
                endDate = builder.endDate,
                travelers = builder.travelers,
                budgetAmount = builder.budgetAmount,
                budgetLevel = builder.budgetLevel,
                preferences = builder.preferences.toList(),
                specialNeeds = builder.specialNeeds
            )
        }
    }
}

data class ChatSessionState(
    val sessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val nextMessageId: Long = 0L,
    val chatStep: String = "GREETING",
    val preferenceBuffer: List<Preference> = emptyList(),
    val builder: TripRequestBuilderSnapshot = TripRequestBuilderSnapshot(),
    val remoteDialogComplete: Boolean = false
)
