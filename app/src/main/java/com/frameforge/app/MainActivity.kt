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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, FrameforgeDatabase::class.java, "frameforge.db")
            .addMigrations(MIGRATION_1_2)
            .build()
        setContent {
            FrameforgeTheme {
                FrameforgeRoot(ProjectRepository(db.projectDao()), MediaRepository(db.mediaDao()))
            }
        }
    }
}
