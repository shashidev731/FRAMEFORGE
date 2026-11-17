package com.frameforge.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY addedAt DESC")
    fun observeMedia(): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaEntity)

    @Query("DELETE FROM media WHERE uri = :uri")
    suspend fun delete(uri: String)
}
