package com.example.call.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val systemId: Long? = null,
    val phoneNumber: String,
    val displayName: String?,
    val direction: String,
    val timestamp: Long,
    val durationSeconds: Long,
    val note: String?,
    val tag: String?
)
