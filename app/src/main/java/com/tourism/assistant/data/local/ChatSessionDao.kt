package com.tourism.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatSessionDao {

    @Query("SELECT * FROM chat_session WHERE id = 1")
    suspend fun get(): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatSessionEntity)

    @Query("DELETE FROM chat_session")
    suspend fun delete()
}
