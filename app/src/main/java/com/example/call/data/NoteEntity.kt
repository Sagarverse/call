package com.example.call.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val displayName: String?,
    val timestamp: Long,
    val note: String,
    val tag: String?,
    val isPinned: Boolean = false
)
