package com.frameforge.app.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val aspectRatio: String,
    val createdAt: Long,
    val updatedAt: Long
)
