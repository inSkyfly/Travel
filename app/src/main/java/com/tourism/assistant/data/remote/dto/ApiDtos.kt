package com.tourism.assistant.data.remote.dto

data class ChatRequestDto(
    val session_id: String? = null,
    val message: String
)

data class ChatResetRequestDto(
    val session_id: String? = null
)

data class ChatResponseDto(
    val session_id: String,
    val message: String,
    val is_complete: Boolean = false,
    val partial_request: Map<String, Any>? = null,
    val rag_used: Boolean = false
)

data class RagSearchRequestDto(
    val query: String,
    val destination: String = "",
    val top_k: Int = 6
)

data class RagSearchResultDto(
    val text: String,
    val metadata: Map<String, Any> = emptyMap(),
    val score: Double = 0.0
)

data class RagSearchResponseDto(
    val query: String,
    val results: List<RagSearchResultDto> = emptyList()
)

data class CrawlRequestDto(
    val destination: String
)

data class HealthResponseDto(
    val status: String,
    val corpus_chunks: Int = 0,
    val use_mock_llm: Boolean = true
)
