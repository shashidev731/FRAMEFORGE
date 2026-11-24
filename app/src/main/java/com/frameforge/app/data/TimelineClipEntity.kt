package com.frameforge.app.data

import androidx.room.ColumnInfo
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
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val timelineStartMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val gapBeforeMs: Long = 0L,
    @ColumnInfo(defaultValue = "1.0") val audioVolume: Float = 1f,
    @ColumnInfo(defaultValue = "0") val audioFadeInMs: Long = 0L,
    @ColumnInfo(defaultValue = "0") val audioFadeOutMs: Long = 0L,
    @ColumnInfo(defaultValue = "1.0") val speed: Float = 1.0f,
    @ColumnInfo(defaultValue = "'NONE'") val filterEffect: String = "NONE",
    @ColumnInfo(defaultValue = "0") val rotationDegrees: Int = 0,
    @ColumnInfo(defaultValue = "''") val textOverlay: String = "",
    val createdAt: Long
)
