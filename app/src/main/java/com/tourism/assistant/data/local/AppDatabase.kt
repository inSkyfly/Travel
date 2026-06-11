package com.tourism.assistant.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedPlanEntity::class, ChatSessionEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedPlanDao(): SavedPlanDao
    abstract fun chatSessionDao(): ChatSessionDao
}
