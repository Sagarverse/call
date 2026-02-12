package com.example.call.ui.stats

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.call.MainActivity
import com.example.call.R
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.databinding.ActivityCallStatsBinding
import kotlinx.coroutines.launch

class CallStatsFragment : Fragment() {
    private var _binding: ActivityCallStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: CallStatsViewModel
    private lateinit var repository: CallLogRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val hasCallLog = result[Manifest.permission.READ_CALL_LOG] == true
        val hasContacts = result[Manifest.permission.READ_CONTACTS] == true
        if (hasCallLog) {
            syncAndLoad(hasContacts)
        } else {
            viewModel.loadStats()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityCallStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = AppDatabase.getInstance(requireContext()).callLogDao()
        repository = CallLogRepository(dao)
        viewModel = ViewModelProvider(
            this,
            CallStatsViewModel.Factory(repository)
        )[CallStatsViewModel::class.java]

        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.showDialerPage()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
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
                    binding.topContactsValue.text = if (summary.topContactsText.isBlank()) {
                        getString(R.string.stats_no_data)
                    } else {
                        summary.topContactsText
                    }
                    binding.peakHoursValue.text = if (summary.peakHoursText.isBlank()) {
                        getString(R.string.stats_no_data)
                    } else {
                        summary.peakHoursText
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val callLogGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        val contactsGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (callLogGranted) {
            syncAndLoad(contactsGranted)
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
            )
        }
    }

    private fun syncAndLoad(canReadContacts: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.syncFromSystem(requireContext().contentResolver, canReadContacts)
            viewModel.loadStats()
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
