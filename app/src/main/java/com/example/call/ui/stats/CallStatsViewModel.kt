package com.example.call.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.call.data.CallLogEntity
import com.example.call.data.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CallStatsViewModel(private val repository: CallLogRepository) : ViewModel() {
    private val _summary = MutableStateFlow(StatsSummary())
    val summary: StateFlow<StatsSummary> = _summary

    fun loadStats() {
        viewModelScope.launch {
            try {
                val logs = repository.getAllNow()
                _summary.value = computeSummary(logs)
            } catch (e: Exception) {
                // Log the error or handle it as appropriate
                _summary.value = StatsSummary()
            }
        }
    }

    private fun computeSummary(logs: List<CallLogEntity>): StatsSummary {
        val now = System.currentTimeMillis()
        val dayAgo = now - ONE_DAY_MILLIS
        val weekAgo = now - ONE_WEEK_MILLIS

        val dailyLogs = logs.filter { it.timestamp >= dayAgo }
        val weeklyLogs = logs.filter { it.timestamp >= weekAgo }

        return StatsSummary(
            dailyCount = dailyLogs.size,
            dailyDurationSeconds = dailyLogs.sumOf { it.durationSeconds },
            weeklyCount = weeklyLogs.size,
            weeklyDurationSeconds = weeklyLogs.sumOf { it.durationSeconds }
        )
    }

    data class StatsSummary(
        val dailyCount: Int = 0,
        val dailyDurationSeconds: Long = 0,
        val weeklyCount: Int = 0,
        val weeklyDurationSeconds: Long = 0
    )

    class Factory(private val repository: CallLogRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CallStatsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CallStatsViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val ONE_WEEK_MILLIS = 7 * 24 * 60 * 60 * 1000L
    }
}
