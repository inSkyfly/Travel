package com.tourism.assistant.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_plans")
data class SavedPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val planJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
