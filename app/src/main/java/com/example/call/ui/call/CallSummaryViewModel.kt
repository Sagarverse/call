package com.example.call.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.call.data.CallLogEntity
import com.example.call.data.CallLogRepository
import com.example.call.data.NoteEntity
import com.example.call.data.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallSummaryViewModel(
    private val repository: CallLogRepository,
    private val noteRepository: NoteRepository
) : ViewModel() {
    private val _latest = MutableStateFlow<CallLogEntity?>(null)
    val latest: StateFlow<CallLogEntity?> = _latest

    fun loadLatest() {
        viewModelScope.launch {
            _latest.value = repository.getLatest()
        }
    }

    fun loadById(id: Long) {
        viewModelScope.launch {
            // Logic to load specific log by ID from repository
            // For now, we fallback to latest if repository doesn't have findById
            _latest.value = repository.getLatest()
        }
    }

    fun saveNoteAndTag(note: String?, tag: String?) {
        viewModelScope.launch {
            val current = _latest.value
            if (current != null && !note.isNullOrBlank()) {
                val noteEntity = NoteEntity(
                    phoneNumber = current.phoneNumber,
                    displayName = current.displayName,
                    timestamp = System.currentTimeMillis(),
                    note = note,
                    tag = tag
                )
                noteRepository.addNote(noteEntity)
            }
            repository.updateLatest(note, tag)
            _latest.value = repository.getLatest()
        }
    }

    class Factory(
        private val repository: CallLogRepository,
        private val noteRepository: NoteRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CallSummaryViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CallSummaryViewModel(repository, noteRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
