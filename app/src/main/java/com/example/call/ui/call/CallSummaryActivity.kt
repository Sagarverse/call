package com.example.call.ui.call

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.example.call.util.CallReminderScheduler
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class CallSummaryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallSummaryBinding
    private lateinit var viewModel: CallSummaryViewModel
    private var selectedTag: String? = null
    private var currentLog: com.example.call.data.CallLogEntity? = null

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

        binding.remindMe.setOnClickListener {
            showCustomReminderDialog()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.latest.collect { displayLog ->
                    if (displayLog == null) {
                        binding.callerNameSummary.text = getString(R.string.no_recent_call)
                        binding.callerNumberSummary.text = ""
                        binding.callTimeSummary.text = ""
                        binding.saveNote.isEnabled = false
                        binding.remindMe.isEnabled = false
                        return@collect
                    }

                    currentLog = displayLog
                    val displayName = displayLog.displayName ?: displayLog.phoneNumber
                    binding.callerNameSummary.text = displayName
                    binding.callerNumberSummary.text = displayLog.phoneNumber
                    binding.callTimeSummary.text = DateFormat.getDateTimeInstance()
                        .format(Date(displayLog.timestamp))
                    binding.noteInput.setText(displayLog.note ?: "")
                    binding.saveNote.isEnabled = true
                    binding.remindMe.isEnabled = true
                    selectedTag = displayLog.tag
                    selectChipByTag(displayLog.tag)
                }
            }
        }
    }

    private fun showCustomReminderDialog() {
        val log = currentLog ?: return
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(60, 40, 60, 0)
        }

        val input = EditText(this).apply {
            hint = "Value"
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val unitSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@CallSummaryActivity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("Seconds", "Minutes", "Hours")
            )
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        container.addView(input)
        container.addView(unitSpinner)

        AlertDialog.Builder(this)
            .setTitle("Set Callback Reminder")
            .setView(container)
            .setPositiveButton("Set") { _, _ ->
                val value = input.text.toString().toLongOrNull()
                if (value != null && value > 0) {
                    val delayMillis = when (unitSpinner.selectedItemPosition) {
                        0 -> value * 1000L
                        1 -> value * 60 * 1000L
                        else -> value * 60 * 60 * 1000L
                    }
                    val triggerAt = System.currentTimeMillis() + delayMillis
                    CallReminderScheduler.schedule(
                        this,
                        log.displayName,
                        log.phoneNumber,
                        triggerAt
                    )
                    Toast.makeText(this, "Reminder set for $value ${unitSpinner.selectedItem}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
