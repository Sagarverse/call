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
    private val adapter = CallLogAdapter(
        onCallClick = { log -> startCall(log.phoneNumber) },
        onContactClick = { log -> openOrCreateContact(log.phoneNumber) }
    )

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
        val repository = CallLogRepository(dao)
        viewModel = ViewModelProvider(
            this,
            CallLogViewModel.Factory(repository)
        )[CallLogViewModel::class.java]

        binding.callLogsList.layoutManager = LinearLayoutManager(this)
        binding.callLogsList.adapter = adapter
        setupSwipeGestures()

        lifecycleScope.launch {
            viewModel.filteredLogs.collectLatest { logs ->
                adapter.submitList(logs)
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

        requestPermissionsIfNeeded()

        binding.backButton.setOnClickListener { finish() }
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
                val log = adapter.currentList[position]
                
                if (direction == ItemTouchHelper.RIGHT) {
                    startCall(log.phoneNumber)
                } else {
                    startMessage(log.phoneNumber)
                }
                
                // Reset the item view so it doesn't stay swiped
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
                val itemView = viewHolder.itemView
                val p = Paint()
                
                if (dX > 0) { // Swiping Right (Call)
                    p.color = Color.parseColor("#30D158") // Call Green
                    val background = RectF(itemView.left.toFloat(), itemView.top.toFloat(), dX, itemView.bottom.toFloat())
                    c.drawRect(background, p)
                } else if (dX < 0) { // Swiping Left (Message)
                    p.color = Color.parseColor("#007AFF") // iOS Blue
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
            this,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        val contactsGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!callLogGranted || !contactsGranted) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CONTACTS
                )
            )
        } else {
            viewModel.syncFromSystem(contentResolver, true)
        }
    }

    private fun startCall(number: String) {
        if (number.isBlank()) return
        val uri = Uri.fromParts("tel", number, null)
        val telecomManager = getSystemService(TelecomManager::class.java)
        val extras = Bundle()
        try {
            telecomManager.placeCall(uri, extras)
        } catch (_: SecurityException) {
            val intent = Intent(Intent.ACTION_DIAL, uri)
            startActivity(intent)
        }
    }

    private fun startMessage(number: String) {
        if (number.isBlank()) return
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
        startActivity(intent)
    }

    private fun openOrCreateContact(number: String) {
        if (number.isBlank()) return
        val contactUri = ContactLookup.findContactUri(this, number)
        if (contactUri != null) {
            val intent = Intent(Intent.ACTION_VIEW, contactUri)
            startActivity(intent)
        } else {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
            }
            startActivity(intent)
        }
    }
}
