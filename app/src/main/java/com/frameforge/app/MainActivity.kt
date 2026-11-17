package com.frameforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.frameforge.app.data.FrameforgeDatabase
import com.frameforge.app.data.ProjectRepository
import com.frameforge.app.data.MediaRepository
import com.frameforge.app.ui.FrameforgeRoot
import com.frameforge.app.ui.theme.FrameforgeTheme

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS media (uri TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, addedAt INTEGER NOT NULL)"
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS edit_operations (operationId TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, sequence INTEGER NOT NULL, operationType TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL, isApplied INTEGER NOT NULL DEFAULT 1)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_edit_operations_projectId_sequence ON edit_operations(projectId, sequence)")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, FrameforgeDatabase::class.java, "frameforge.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
        setContent {
            FrameforgeTheme {
                FrameforgeRoot(ProjectRepository(db.projectDao()), MediaRepository(db.mediaDao()))
            }
        }
    }
}
