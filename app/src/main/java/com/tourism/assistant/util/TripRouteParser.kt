package com.tourism.assistant.util

object TripRouteParser {
    private val routePatterns = listOf(
        Regex("从\\s*([^，,。；;！!？?\\s到至\\-—~]+?)\\s*(?:到|至|->|—|-|~)\\s*([^，,。；;！!？?\\s]+)"),
        Regex("([^，,。；;！!？?\\s]+?)\\s*(?:到|至|->)\\s*([^，,。；;！!？?\\s]+)"),
        Regex("([^，,。；;！!？?\\s]+?)\\s*--\\s*([^，,。；;！!？?\\s]+)")
    )

    fun parse(text: String): Pair<String, String>? {
        val normalized = text.trim()
        if (normalized.isEmpty()) return null
        for (pattern in routePatterns) {
            val match = pattern.find(normalized) ?: continue
            val origin = match.groupValues[1].trim()
            val destination = match.groupValues[2].trim()
            if (origin.isNotBlank() && destination.isNotBlank() && origin != destination) {
                return origin to destination
            }
        }
        return null
    }
}
