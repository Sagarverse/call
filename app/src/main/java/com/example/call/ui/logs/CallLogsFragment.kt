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
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.call.MainActivity
import com.example.call.R
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.databinding.ActivityCallLogBinding
import com.example.call.util.BlockedNumberStore
import com.example.call.util.ContactLookup
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CallLogsFragment : Fragment() {
    private var _binding: ActivityCallLogBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: CallLogViewModel
    private lateinit var repository: CallLogRepository
    private val adapter = CallLogAdapter(
        onEntryClick = { log -> handleEntryClick(log) },
        onEntryLongPress = { log -> handleEntryLongPress(log) },
        onContactClick = { log -> openOrCreateContact(log.phoneNumber) },
        onTagLongPress = { log -> showTagOptions(log) },
        onQuickCall = { log -> startCall(log.phoneNumber) },
        onQuickMessage = { log -> startMessage(log.phoneNumber) }
    )
    private var timelineMode = false
    private var actionMode: ActionMode? = null
    private val selectedIds = linkedSetOf<Long>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val hasCallLog = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        val hasContacts = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCallLog) {
            viewModel.syncFromSystem(requireContext().contentResolver, hasContacts)
        } else {
            Toast.makeText(requireContext(), getString(R.string.call_log_permission), Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityCallLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = AppDatabase.getInstance(requireContext()).callLogDao()
        repository = CallLogRepository(dao)
        viewModel = ViewModelProvider(
            this,
            CallLogViewModel.Factory(repository)
        )[CallLogViewModel::class.java]

        binding.callLogsList.layoutManager = LinearLayoutManager(requireContext())
        binding.callLogsList.adapter = adapter
        binding.callLogsList.setHasFixedSize(true)
        binding.callLogsList.itemAnimator = null
        binding.callLogsList.setItemViewCacheSize(16)
        setupSwipeGestures()
        setupTimelineToggle()

        viewLifecycleOwner.lifecycleScope.launch {
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

        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.showDialerPage()
        }
    }

    private fun handleEntryClick(log: com.example.call.data.CallLogEntity) {
        if (actionMode != null) {
            toggleSelection(log)
        } else {
            startCall(log.phoneNumber)
        }
    }

    private fun handleEntryLongPress(log: com.example.call.data.CallLogEntity) {
        if (actionMode != null) {
            toggleSelection(log)
        } else {
            showLogActions(log)
        }
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
            ContextCompat.getColor(requireContext(), R.color.call_green)
        } else {
            ContextCompat.getColor(requireContext(), R.color.text_secondary)
        }
        binding.timelineToggle.iconTint = android.content.res.ColorStateList.valueOf(tint)
    }

    private fun setupSwipeGestures() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewHolder is CallLogAdapter.DividerViewHolder) 0 else super.getSwipeDirs(recyclerView, viewHolder)
            }

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

                viewHolder.itemView.translationX = 0f
                adapter.notifyItemChanged(position)
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.translationX = 0f
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
        val callLogGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        val contactsGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        val missing = mutableListOf<String>()
        if (!callLogGranted) missing.add(Manifest.permission.READ_CALL_LOG)
        if (!contactsGranted) missing.add(Manifest.permission.READ_CONTACTS)

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.syncFromSystem(requireContext().contentResolver, contactsGranted)
        }
    }

    private fun startCall(number: String) {
        if (number.isBlank()) return
        val uri = Uri.fromParts("tel", number, null)
        val telecomManager = requireContext().getSystemService(TelecomManager::class.java)
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

    private fun startEmail(number: String) {
        val email = ContactLookup.findContactEmail(requireContext(), number)
        if (email.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.no_email_found), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
    }

    private fun openOrCreateContact(number: String) {
        if (number.isBlank()) return
        val contactUri = ContactLookup.findContactUri(requireContext(), number)
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
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.tag_filter))
            .setItems(options) { _, which ->
                val tag = when (which) {
                    0 -> CallLogTags.WORK
                    1 -> CallLogTags.PERSONAL
                    2 -> CallLogTags.SPAM
                    else -> null
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.updateNoteTag(log.id, log.note, tag)
                }
            }
            .show()
    }

    private fun showLogActions(log: com.example.call.data.CallLogEntity) {
        val actions = mutableListOf<String>()
        val actionHandlers = mutableListOf<() -> Unit>()
        val isBlocked = BlockedNumberStore.isBlocked(requireContext(), log.phoneNumber)

        actions.add(getString(R.string.open_contact))
        actionHandlers.add { openOrCreateContact(log.phoneNumber) }

        actions.add(getString(R.string.copy_number))
        actionHandlers.add { copyNumber(log.phoneNumber) }

        actions.add(getString(R.string.edit_note))
        actionHandlers.add { showNoteDialog(log) }

        actions.add(getString(R.string.set_tag))
        actionHandlers.add { showTagOptions(log) }

        actions.add(if (isBlocked) getString(R.string.unblock_number) else getString(R.string.block_number))
        actionHandlers.add { toggleBlock(log.phoneNumber, isBlocked) }

        actions.add(getString(R.string.delete_call_log))
        actionHandlers.add { deleteLog(log) }

        showActionSheet(getString(R.string.call_log_actions), actions, actionHandlers)
    }

    private fun showActionSheet(
        title: String,
        actions: List<String>,
        handlers: List<() -> Unit>
    ) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_action_sheet, null)
        val titleView = view.findViewById<android.widget.TextView>(R.id.sheetTitle)
        val listView = view.findViewById<android.widget.ListView>(R.id.sheetList)
        titleView.text = title
        listView.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, actions)
        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            handlers.getOrNull(position)?.invoke()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun copyNumber(number: String) {
        if (number.isBlank()) return
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.phone_number), number))
        Toast.makeText(requireContext(), getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    private fun shareNumber(number: String) {
        if (number.isBlank()) return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, number)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_number)))
    }

    private fun markSpam(log: com.example.call.data.CallLogEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.updateNoteTag(log.id, log.note, CallLogTags.SPAM)
        }
    }

    private fun toggleBlock(number: String, isBlocked: Boolean) {
        if (isBlocked) {
            BlockedNumberStore.unblock(requireContext(), number)
            Toast.makeText(requireContext(), getString(R.string.number_unblocked), Toast.LENGTH_SHORT).show()
        } else {
            BlockedNumberStore.block(requireContext(), number)
            Toast.makeText(requireContext(), getString(R.string.number_blocked), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNoteDialog(log: com.example.call.data.CallLogEntity) {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext())
        input.setText(log.note.orEmpty())
        input.hint = getString(R.string.note_hint)
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.edit_note))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val note = input.text?.toString()?.trim().orEmpty()
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.updateNoteTag(log.id, note, log.tag)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteLog(log: com.example.call.data.CallLogEntity) {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.deleteById(log.id)
        }
    }

    private fun confirmClearAll() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.clear_all_logs))
            .setMessage(getString(R.string.clear_all_logs_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.clearAll()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startSelectionMode(log: com.example.call.data.CallLogEntity) {
        if (actionMode == null) {
            actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(selectionCallback)
        }
        toggleSelection(log)
    }

    private fun toggleSelection(log: com.example.call.data.CallLogEntity) {
        if (selectedIds.contains(log.id)) {
            selectedIds.remove(log.id)
        } else {
            selectedIds.add(log.id)
        }
        adapter.setSelection(selectedIds)
        updateActionModeTitle()
        if (selectedIds.isEmpty()) {
            actionMode?.finish()
        }
    }

    private fun updateActionModeTitle() {
        actionMode?.title = getString(R.string.selected_count, selectedIds.size)
    }

    private val selectionCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            requireActivity().menuInflater.inflate(R.menu.menu_call_log_selection, menu)
            updateActionModeTitle()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_delete -> {
                    val ids = selectedIds.toList()
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.deleteByIds(ids)
                    }
                    mode.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedIds.clear()
            adapter.setSelection(selectedIds)
            actionMode = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
