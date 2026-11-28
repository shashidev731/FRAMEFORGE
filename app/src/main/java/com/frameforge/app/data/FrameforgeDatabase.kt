package com.frameforge.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProjectEntity::class, MediaEntity::class, EditOperationEntity::class, TimelineClipEntity::class], version = 12, exportSchema = false)
abstract class FrameforgeDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun mediaDao(): MediaDao
    abstract fun editOperationDao(): EditOperationDao
    abstract fun timelineClipDao(): TimelineClipDao
}
