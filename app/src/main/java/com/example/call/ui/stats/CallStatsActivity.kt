package com.example.call.ui.stats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.call.R
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.databinding.ActivityCallStatsBinding
import kotlinx.coroutines.launch

class CallStatsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallStatsBinding
    private lateinit var viewModel: CallStatsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = AppDatabase.getInstance(this).callLogDao()
        val repository = CallLogRepository(dao)
        viewModel = ViewModelProvider(
            this,
            CallStatsViewModel.Factory(repository)
        )[CallStatsViewModel::class.java]

        binding.backButton.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.summary.collect { summary ->
                    binding.dailyCalls.text = getString(
                        R.string.stats_calls_format,
                        summary.dailyCount
                    )
                    binding.dailyDuration.text = getString(
                        R.string.stats_duration_format,
                        formatDuration(summary.dailyDurationSeconds)
                    )
                    binding.weeklyCalls.text = getString(
                        R.string.stats_calls_format,
                        summary.weeklyCount
                    )
                    binding.weeklyDuration.text = getString(
                        R.string.stats_duration_format,
                        formatDuration(summary.weeklyDurationSeconds)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.loadStats()
    }

    private fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) {
            getString(R.string.stats_hours_minutes_format, hours, remainingMinutes)
        } else {
            getString(R.string.stats_minutes_format, remainingMinutes)
        }
    }
}
