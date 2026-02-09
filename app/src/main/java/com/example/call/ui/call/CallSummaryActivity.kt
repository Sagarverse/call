package com.example.call.ui.call

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.view.children
import com.example.call.R
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.data.NoteRepository
import com.example.call.databinding.ActivityCallSummaryBinding
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CallSummaryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallSummaryBinding
    private lateinit var viewModel: CallSummaryViewModel
    private var selectedTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getInstance(this)
        val repository = CallLogRepository(db.callLogDao())
        val noteRepository = NoteRepository(db.noteDao())
        
        viewModel = ViewModelProvider(
            this,
            CallSummaryViewModel.Factory(repository, noteRepository)
        )[CallSummaryViewModel::class.java]

        setupTagChips()

        binding.saveNote.setOnClickListener {
            val note = binding.noteInput.text?.toString()?.trim().orEmpty()
            viewModel.saveNoteAndTag(note.ifBlank { null }, selectedTag)
            finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.latest.collect { displayLog ->
                    if (displayLog == null) {
                        binding.callerNameSummary.text = getString(R.string.no_recent_call)
                        binding.callerNumberSummary.text = ""
                        binding.callTimeSummary.text = ""
                        binding.saveNote.isEnabled = false
                        return@collect
                    }

                    val displayName = displayLog.displayName ?: displayLog.phoneNumber
                    binding.callerNameSummary.text = displayName
                    binding.callerNumberSummary.text = displayLog.phoneNumber
                    binding.callTimeSummary.text = DateFormat.getDateTimeInstance()
                        .format(Date(displayLog.timestamp))
                    binding.noteInput.setText(displayLog.note ?: "")
                    binding.saveNote.isEnabled = true
                    selectedTag = displayLog.tag
                    selectChipByTag(displayLog.tag)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val specificLogId = intent.getLongExtra("EXTRA_LOG_ID", -1L)
        if (specificLogId != -1L) {
            viewModel.loadById(specificLogId)
        } else {
            viewModel.loadLatest()
        }
    }

    private fun setupTagChips() {
        binding.tagGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            selectedTag = checkedIds.firstOrNull()?.let { id ->
                group.findViewById<Chip>(id).text.toString()
            }
        }
    }

    private fun selectChipByTag(tag: String?) {
        if (tag.isNullOrBlank()) {
            binding.tagGroup.clearCheck()
            return
        }
        val chip = binding.tagGroup.children
            .filterIsInstance<Chip>()
            .firstOrNull { it.text.toString() == tag }
        chip?.isChecked = true
    }
}
