package com.example.call.data

import kotlinx.coroutines.flow.Flow

class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    suspend fun addNote(note: NoteEntity) {
        dao.insert(note)
    }

    suspend fun deleteNote(id: Long) {
        dao.deleteById(id)
    }
}
