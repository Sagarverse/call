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
            finish() // Acting as "Done"
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.latest.collect { latest ->
                    if (latest == null) {
                        binding.callerNameSummary.text = getString(R.string.no_recent_call)
                        binding.callerNumberSummary.text = ""
                        binding.callTimeSummary.text = ""
                        binding.saveNote.isEnabled = false
                        return@collect
                    }

                    val displayName = latest.displayName ?: latest.phoneNumber
                    binding.callerNameSummary.text = displayName
                    binding.callerNumberSummary.text = latest.phoneNumber
                    binding.callTimeSummary.text = DateFormat.getDateTimeInstance()
                        .format(Date(latest.timestamp))
                    binding.noteInput.setText(latest.note ?: "")
                    binding.saveNote.isEnabled = true
                    selectedTag = latest.tag
                    selectChipByTag(latest.tag)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.loadLatest()
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
