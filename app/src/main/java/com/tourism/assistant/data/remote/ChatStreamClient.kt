package com.tourism.assistant.data.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tourism.assistant.BuildConfig
import com.tourism.assistant.data.remote.dto.ChatRequestDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class ChatStreamEvent(
    val type: String,
    val delta: String = "",
    val content: String = "",
    val session_id: String = "",
    val is_complete: Boolean = false,
    val partial_request: Map<String, Any>? = null
)

@Singleton
class ChatStreamClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    suspend fun streamChat(
        sessionId: String?,
        message: String,
        onEvent: suspend (ChatStreamEvent) -> Unit
    ) = withContext(Dispatchers.IO) {
        val json = gson.toJson(ChatRequestDto(session_id = sessionId, message = message))
        val request = Request.Builder()
            .url("${BuildConfig.API_BASE_URL}api/v1/chat/stream")
            .post(json.toRequestBody("application/json".toMediaType()))
            .header("Accept", "text/event-stream")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("empty body")
            body.source().use { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    onEvent(parseEvent(payload))
                }
            }
        }
    }

    private fun parseEvent(payload: String): ChatStreamEvent {
        val obj = gson.fromJson(payload, JsonObject::class.java)
        val partial = obj.get("partial_request")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.let { jsonObj ->
                gson.fromJson(jsonObj, Map::class.java) as Map<String, Any>
            }
        return ChatStreamEvent(
            type = obj.get("type")?.asString ?: "",
            delta = obj.get("delta")?.asString ?: "",
            content = obj.get("content")?.asString ?: "",
            session_id = obj.get("session_id")?.asString ?: "",
            is_complete = obj.get("is_complete")?.asBoolean ?: false,
            partial_request = partial
        )
    }
}
