package com.tourism.assistant.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlanDao {
    @Query("SELECT * FROM saved_plans ORDER BY created_at DESC")
    fun getAllPlans(): Flow<List<SavedPlanEntity>>

    @Query("SELECT * FROM saved_plans WHERE id = :id")
    suspend fun getPlanById(id: Long): SavedPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedPlanEntity): Long

    @Query("DELETE FROM saved_plans WHERE id = :id")
    suspend fun deleteById(id: Long)
}
