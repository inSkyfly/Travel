package com.tourism.assistant.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_session (
                id INTEGER PRIMARY KEY NOT NULL,
                stateJson TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}
