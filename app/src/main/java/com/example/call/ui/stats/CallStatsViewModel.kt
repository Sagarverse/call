package com.example.call.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.call.data.CallLogEntity
import com.example.call.data.CallLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class CallStatsViewModel(private val repository: CallLogRepository) : ViewModel() {
    private val _summary = MutableStateFlow(StatsSummary())
    val summary: StateFlow<StatsSummary> = _summary

    fun loadStats() {
        viewModelScope.launch {
            try {
                val logs = repository.getAllNow()
                _summary.value = computeSummary(logs)
            } catch (e: Exception) {
                _summary.value = StatsSummary()
            }
        }
    }

    private fun computeSummary(logs: List<CallLogEntity>): StatsSummary {
        val calendar = Calendar.getInstance()
        
        // Reset to midnight for today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis

        // Reset to start of week (e.g., Monday)
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        val weekStart = calendar.timeInMillis

        val dailyLogs = logs.filter { it.timestamp >= todayStart }
        val weeklyLogs = logs.filter { it.timestamp >= weekStart }

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
}
