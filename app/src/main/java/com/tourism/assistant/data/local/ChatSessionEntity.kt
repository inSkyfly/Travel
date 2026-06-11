package com.tourism.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_session")
data class ChatSessionEntity(
    @PrimaryKey val id: Int = 1,
    val stateJson: String
)
