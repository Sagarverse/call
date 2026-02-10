package com.example.call.ui.logs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.addTextChangedListener
import com.example.call.R
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.databinding.ActivityCallLogBinding
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import com.example.call.util.ContactLookup
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class CallLogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCallLogBinding
    private lateinit var viewModel: CallLogViewModel
    private lateinit var repository: CallLogRepository
    private val adapter = CallLogAdapter(
        onCallClick = { log -> startCall(log.phoneNumber) },
        onContactClick = { log -> openOrCreateContact(log.phoneNumber) },
        onTagLongPress = { log -> showTagOptions(log) }
    )
    private var timelineMode = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val hasCallLog = result[Manifest.permission.READ_CALL_LOG] == true
        val hasContacts = result[Manifest.permission.READ_CONTACTS] == true
        if (hasCallLog) {
            viewModel.syncFromSystem(contentResolver, hasContacts)
        } else {
            Toast.makeText(this, getString(R.string.call_log_permission), Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = AppDatabase.getInstance(this).callLogDao()
        repository = CallLogRepository(dao)
        viewModel = ViewModelProvider(
            this,
            CallLogViewModel.Factory(repository)
        )[CallLogViewModel::class.java]

        binding.callLogsList.layoutManager = LinearLayoutManager(this)
        binding.callLogsList.adapter = adapter
        setupSwipeGestures()
        setupTimelineToggle()

        lifecycleScope.launch {
            viewModel.filteredLogs.collectLatest { logs ->
                adapter.submitLogs(logs)
            }
        }

        binding.searchInput.addTextChangedListener {
            viewModel.updateQuery(it?.toString().orEmpty())
        }

        binding.filterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filterValue = when (checkedIds.firstOrNull()) {
                R.id.filter_missed -> CallLogViewModel.Filter.MISSED
                R.id.filter_incoming -> CallLogViewModel.Filter.INCOMING
                R.id.filter_outgoing -> CallLogViewModel.Filter.OUTGOING
                else -> CallLogViewModel.Filter.ALL
            }
            viewModel.updateFilter(filterValue)
        }

        binding.tagFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val filterValue = when (checkedIds.firstOrNull()) {
                R.id.tag_filter_work -> CallLogViewModel.TagFilter.WORK
                R.id.tag_filter_personal -> CallLogViewModel.TagFilter.PERSONAL
                R.id.tag_filter_spam -> CallLogViewModel.TagFilter.SPAM
                R.id.tag_filter_none -> CallLogViewModel.TagFilter.NONE
                else -> CallLogViewModel.TagFilter.ALL
            }
            viewModel.updateTagFilter(filterValue)
        }

        requestPermissionsIfNeeded()

        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupTimelineToggle() {
        updateTimelineToggleUi()
        binding.timelineToggle.setOnClickListener {
            timelineMode = !timelineMode
            adapter.setTimelineMode(timelineMode)
            updateTimelineToggleUi()
        }
    }

    private fun updateTimelineToggleUi() {
        val tint = if (timelineMode) {
            ContextCompat.getColor(this, R.color.call_green)
        } else {
            ContextCompat.getColor(this, R.color.text_secondary)
        }
        binding.timelineToggle.iconTint = android.content.res.ColorStateList.valueOf(tint)
    }

    private fun setupSwipeGestures() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION || position >= adapter.currentList.size) {
                    return
                }
                val item = adapter.currentList[position]
                
                if (item is CallLogAdapter.LogItem.Entry) {
                    if (direction == ItemTouchHelper.RIGHT) {
                        startCall(item.log.phoneNumber)
                    } else {
                        startMessage(item.log.phoneNumber)
                    }
                }
                
                adapter.notifyItemChanged(position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (viewHolder is CallLogAdapter.DividerViewHolder) {
                    super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
                    return
                }

                val itemView = viewHolder.itemView
                val p = Paint()
                
                if (dX > 0) { // Call
                    p.color = Color.parseColor("#30D158")
                    val background = RectF(itemView.left.toFloat(), itemView.top.toFloat(), dX, itemView.bottom.toFloat())
                    c.drawRect(background, p)
                } else if (dX < 0) { // Message
                    p.color = Color.parseColor("#007AFF")
                    val background = RectF(itemView.right.toFloat() + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    c.drawRect(background, p)
                }
                
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.callLogsList)
    }

    private fun requestPermissionsIfNeeded() {
        val callLogGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val contactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!callLogGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS))
        } else {
            viewModel.syncFromSystem(contentResolver, contactsGranted)
        }
    }

    private fun startCall(number: String) {
        if (number.isBlank()) return
        val uri = Uri.fromParts("tel", number, null)
        val telecomManager = getSystemService(TelecomManager::class.java)
        try {
            telecomManager.placeCall(uri, Bundle())
        } catch (_: SecurityException) {
            startActivity(Intent(Intent.ACTION_DIAL, uri))
        }
    }

    private fun startMessage(number: String) {
        if (number.isBlank()) return
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    }

    private fun openOrCreateContact(number: String) {
        if (number.isBlank()) return
        val contactUri = ContactLookup.findContactUri(this, number)
        if (contactUri != null) {
            startActivity(Intent(Intent.ACTION_VIEW, contactUri))
        } else {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
            }
            startActivity(intent)
        }
    }

    private fun showTagOptions(log: com.example.call.data.CallLogEntity) {
        val options = arrayOf(
            getString(R.string.tag_work),
            getString(R.string.tag_personal),
            getString(R.string.tag_spam),
            getString(R.string.tag_none)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.tag_filter))
            .setItems(options) { _, which ->
                val tag = when (which) {
                    0 -> CallLogTags.WORK
                    1 -> CallLogTags.PERSONAL
                    2 -> CallLogTags.SPAM
                    else -> null
                }
                lifecycleScope.launch {
                    repository.updateNoteTag(log.id, log.note, tag)
                }
            }
            .show()
    }
}
