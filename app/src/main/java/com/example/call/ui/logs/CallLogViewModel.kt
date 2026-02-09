package com.example.call.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.call.data.CallLogEntity
import com.example.call.data.CallLogRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallLogViewModel(private val repository: CallLogRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(Filter.ALL)

    private val callLogs: StateFlow<List<CallLogEntity>> = repository.observeLogs()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredLogs: StateFlow<List<CallLogEntity>> = combine(
        callLogs,
        query,
        filter
    ) { logs, queryValue, filterValue ->
        val normalizedQuery = queryValue.trim().lowercase()
        logs.filter { log ->
            matchesFilter(log, filterValue) && matchesQuery(log, normalizedQuery)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncFromSystem(contentResolver: android.content.ContentResolver, canReadContacts: Boolean) {
        viewModelScope.launch {
            try {
                repository.syncFromSystem(contentResolver, canReadContacts)
            } catch (e: Exception) {
                // Handle or log the error
            }
        }
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun updateFilter(value: Filter) {
        filter.value = value
    }

    private fun matchesFilter(log: CallLogEntity, value: Filter): Boolean {
        return when (value) {
            Filter.ALL -> true
            Filter.MISSED -> log.direction.equals("Missed", true)
            Filter.INCOMING -> log.direction.equals("Incoming", true)
            Filter.OUTGOING -> log.direction.equals("Outgoing", true)
        }
    }

    private fun matchesQuery(log: CallLogEntity, value: String): Boolean {
        if (value.isBlank()) return true
        val name = log.displayName?.lowercase().orEmpty()
        val number = log.phoneNumber.lowercase()
        val note = log.note?.lowercase().orEmpty()
        val tag = log.tag?.lowercase().orEmpty()
        return name.contains(value) ||
            number.contains(value) ||
            note.contains(value) ||
            tag.contains(value)
    }

    enum class Filter {
        ALL,
        MISSED,
        INCOMING,
        OUTGOING
    }

    class Factory(private val repository: CallLogRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CallLogViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CallLogViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
