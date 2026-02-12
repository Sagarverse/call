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
import java.util.Locale

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
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = calendar.timeInMillis

        val weekCalendar = Calendar.getInstance().apply {
            timeInMillis = todayStart
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        }
        if (weekCalendar.timeInMillis > now) {
            weekCalendar.add(Calendar.WEEK_OF_YEAR, -1)
        }
        val weekStart = weekCalendar.timeInMillis

        val dailyLogs = logs.filter { it.timestamp >= todayStart }
        val weeklyLogs = logs.filter { it.timestamp >= weekStart }

        val topContactsText = buildTopContacts(weeklyLogs)
        val peakHoursText = buildPeakHours(weeklyLogs)

        return StatsSummary(
            dailyCount = dailyLogs.size,
            dailyDurationSeconds = dailyLogs.sumOf { it.durationSeconds },
            weeklyCount = weeklyLogs.size,
            weeklyDurationSeconds = weeklyLogs.sumOf { it.durationSeconds },
            topContactsText = topContactsText,
            peakHoursText = peakHoursText
        )
    }

    private fun buildTopContacts(logs: List<CallLogEntity>): String {
        if (logs.isEmpty()) return ""
        val totals = mutableMapOf<String, Long>()
        logs.forEach { log ->
            val name = log.displayName?.takeIf { it.isNotBlank() } ?: log.phoneNumber
            totals[name] = (totals[name] ?: 0L) + log.durationSeconds
        }
        val top = totals.entries
            .sortedByDescending { it.value }
            .take(5)

        return top.joinToString(separator = "\n") { entry ->
            "${entry.key} - ${formatDurationShort(entry.value)}"
        }
    }

    private fun buildPeakHours(logs: List<CallLogEntity>): String {
        if (logs.isEmpty()) return ""
        val counts = IntArray(24)
        val calendar = Calendar.getInstance()
        logs.forEach { log ->
            calendar.timeInMillis = log.timestamp
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            if (hour in 0..23) counts[hour]++
        }

        val top = counts
            .mapIndexed { hour, count -> hour to count }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(3)

        return top.joinToString(separator = "\n") { (hour, count) ->
            String.format(Locale.getDefault(), "%02d:00 - %d calls", hour, count)
        }
    }

    private fun formatDurationShort(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%dh %02dm", hours, remainingMinutes)
        } else {
            String.format(Locale.getDefault(), "%dm", remainingMinutes)
        }
    }

    data class StatsSummary(
        val dailyCount: Int = 0,
        val dailyDurationSeconds: Long = 0,
        val weeklyCount: Int = 0,
        val weeklyDurationSeconds: Long = 0,
        val topContactsText: String = "",
        val peakHoursText: String = ""
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
