package com.frameforge.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "timeline_clips",
    indices = [Index(value = ["projectId", "trackIndex", "position"], unique = true)]
)
data class TimelineClipEntity(
    @PrimaryKey val clipId: String,
    val projectId: String,
    val mediaUri: String,
    val displayName: String,
    val mimeType: String,
    val trackIndex: Int = 0,
    val position: Int,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val durationMs: Long = 0L,
    val createdAt: Long
)
