package com.frameforge.app.data
import androidx.room.Database
import androidx.room.RoomDatabase
@Database(entities = [ProjectEntity::class], version = 1, exportSchema = false)
abstract class FrameforgeDatabase : RoomDatabase() { abstract fun projectDao(): ProjectDao }
