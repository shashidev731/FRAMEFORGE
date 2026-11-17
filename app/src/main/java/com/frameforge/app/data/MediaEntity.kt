package com.frameforge.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mimeType: String,
    val addedAt: Long
)
