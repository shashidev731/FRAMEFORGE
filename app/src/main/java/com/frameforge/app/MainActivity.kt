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
import com.frameforge.app.data.EditHistoryRepository
import com.frameforge.app.data.TimelineRepository
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

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS timeline_clips (clipId TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, mediaUri TEXT NOT NULL, displayName TEXT NOT NULL, mimeType TEXT NOT NULL, trackIndex INTEGER NOT NULL, position INTEGER NOT NULL, trimStartMs INTEGER NOT NULL, trimEndMs INTEGER, createdAt INTEGER NOT NULL)")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_timeline_clips_projectId_trackIndex_position ON timeline_clips(projectId, trackIndex, position)")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN timelineStartMs INTEGER NOT NULL DEFAULT 0")
        database.execSQL("""
            UPDATE timeline_clips
            SET timelineStartMs = COALESCE((
                SELECT SUM(MAX(COALESCE(previous.trimEndMs, previous.durationMs) - previous.trimStartMs, 0))
                FROM timeline_clips AS previous
                WHERE previous.projectId = timeline_clips.projectId
                  AND previous.trackIndex = timeline_clips.trackIndex
                  AND previous.position < timeline_clips.position
            ), 0)
        """.trimIndent())
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN gapBeforeMs INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN audioVolume REAL NOT NULL DEFAULT 1.0")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN audioFadeInMs INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN audioFadeOutMs INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN speed REAL NOT NULL DEFAULT 1.0")
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN filterEffect TEXT NOT NULL DEFAULT 'NONE'")
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN rotationDegrees INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE timeline_clips ADD COLUMN textOverlay TEXT NOT NULL DEFAULT ''")
        database.execSQL("ALTER TABLE projects ADD COLUMN resolution TEXT NOT NULL DEFAULT '1080p'")
        database.execSQL("ALTER TABLE projects ADD COLUMN frameRate INTEGER NOT NULL DEFAULT 30")
    }
}


private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Reserve track 0 for the main visual sequence, 1..99 for audio,
        // and 100+ for visual overlays. Use legacy-compatible UPDATE syntax:
        // Android versions supported by minSdk 26 may ship SQLite older than 3.33.
        TimelineTrackMigrationSql.statements.forEach(database::execSQL)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Room.databaseBuilder(applicationContext, FrameforgeDatabase::class.java, "frameforge.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()
        val preferences = getSharedPreferences("frameforge_preferences", MODE_PRIVATE)
        val darkTheme = preferences.getBoolean("dark_theme", true)
        setContent {
            FrameforgeTheme(darkTheme = darkTheme) {
                FrameforgeRoot(
                    ProjectRepository(db.projectDao(), db.editOperationDao(), db.timelineClipDao()),
                    MediaRepository(db.mediaDao()),
                    EditHistoryRepository(db.editOperationDao()),
                    TimelineRepository(db.timelineClipDao(), db.editOperationDao(), db)
                )
            }
        }
    }
}
