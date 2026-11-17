package com.frameforge.app.data

import androidx.room.ColumnInfo
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A non-destructive edit instruction. The payload stores tool parameters;
 * original media is never modified by recording an operation.
 */
@Entity(
    tableName = "edit_operations",
    indices = [Index(value = ["projectId", "sequence"], unique = true)]
)
data class EditOperationEntity(
    @PrimaryKey val operationId: String,
    val projectId: String,
    val sequence: Long,
    val operationType: String,
    val payload: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "1") @ColumnInfo(defaultValue = "1") val isApplied: Boolean = true
)
