package com.frameforge.app.data

import kotlinx.coroutines.flow.Flow

class MediaRepository(private val dao: MediaDao) {
    val media: Flow<List<MediaEntity>> = dao.observeMedia()

    suspend fun add(uri: String, name: String, mimeType: String) {
        dao.upsert(MediaEntity(uri, name, mimeType, System.currentTimeMillis()))
    }

    suspend fun delete(uri: String) = dao.delete(uri)
}
