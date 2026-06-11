package com.tourism.assistant.data.local

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSessionLocalStore @Inject constructor(
    private val dao: ChatSessionDao,
    private val gson: Gson
) {
    suspend fun load(): ChatSessionState? = withContext(Dispatchers.IO) {
        dao.get()?.let { gson.fromJson(it.stateJson, ChatSessionState::class.java) }
    }

    suspend fun save(state: ChatSessionState) = withContext(Dispatchers.IO) {
        dao.upsert(ChatSessionEntity(stateJson = gson.toJson(state)))
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.delete()
    }
}
