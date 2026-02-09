package com.example.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.databinding.ActivityDialerBinding
import com.example.call.ui.dialer.DialerViewModel
import com.example.call.ui.logs.CallLogActivity
import com.example.call.ui.notes.NoteActivity
import com.example.call.ui.settings.SettingsActivity
import com.example.call.ui.stats.CallStatsActivity
import com.example.call.util.GesturePreferences
import com.example.call.util.RoleHelper
import com.example.call.util.SimPreferences
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDialerBinding
    private lateinit var viewModel: DialerViewModel
    private lateinit var repository: CallLogRepository
    private lateinit var gestureDetector: GestureDetector

    private var volumeUpPressCount = 0
    private var volumeDownPressCount = 0
    private var lastVolumeUpTime: Long = 0
    private var lastVolumeDownTime: Long = 0

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (RoleHelper.isDefaultDialer(this)) {
            Toast.makeText(this, "App set as default dialer", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            placeCallIfPossible()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDialerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = AppDatabase.getInstance(this).callLogDao()
        repository = CallLogRepository(dao)
        viewModel = ViewModelProvider(this)[DialerViewModel::class.java]

        setupDialPad()
        setupActions()
        setupSwipeGestures()
        
        binding.dialerInput.showSoftInputOnFocus = false
        
        handleIntent(intent)

        if (!RoleHelper.isDefaultDialer(this)) {
            RoleHelper.requestDialerRole(this, roleRequestLauncher)
        }
    }

    private fun setupSwipeGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffY) > abs(diffX) && abs(diffY) > 100 && abs(velocityY) > 100) {
                    if (diffY < 0) { // Swipe Up
                        startActivity(Intent(this@MainActivity, CallLogActivity::class.java))
                        return true
                    }
                }
                return false
            }
        })

        binding.dialerRoot.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_VIEW) {
            val number = data?.schemeSpecificPart ?: ""
            if (number.isNotEmpty()) {
                viewModel.clearDigits()
                number.forEach { char ->
                    if (char.isDigit() || char == '*' || char == '#' || char == '+') {
                        viewModel.appendDigit(char.toString())
                    }
                }
                binding.dialerInput.setText(viewModel.dialedNumber.value)
            }
        }
    }

    private fun setupDialPad() {
        val dialButtons = listOf(
            Triple(binding.digit1, "1", ""),
            Triple(binding.digit2, "2", "ABC"),
            Triple(binding.digit3, "3", "DEF"),
            Triple(binding.digit4, "4", "GHI"),
            Triple(binding.digit5, "5", "JKL"),
            Triple(binding.digit6, "6", "MNO"),
            Triple(binding.digit7, "7", "PQRS"),
            Triple(binding.digit8, "8", "TUV"),
            Triple(binding.digit9, "9", "WXYZ"),
            Triple(binding.digitStar, "*", ""),
            Triple(binding.digit0, "0", "+"),
            Triple(binding.digitHash, "#", "")
        )

        dialButtons.forEach { (include, digit, letters) ->
            include.buttonNumber.text = digit
            include.buttonLetters.text = letters
            include.root.setOnClickListener {
                viewModel.appendDigit(digit)
                binding.dialerInput.setText(viewModel.dialedNumber.value)
            }
            include.root.setOnLongClickListener {
                if (digit == "0") {
                    viewModel.appendDigit("+")
                    binding.dialerInput.setText(viewModel.dialedNumber.value)
                    true
                } else false
            }
        }

        binding.backspace.setOnClickListener {
            viewModel.removeLastDigit()
            binding.dialerInput.setText(viewModel.dialedNumber.value)
        }
        
        binding.backspace.setOnLongClickListener {
            viewModel.clearDigits()
            binding.dialerInput.setText("")
            true
        }
    }

    private fun setupActions() {
        binding.callButton.setOnClickListener {
            if (!RoleHelper.isDefaultDialer(this)) {
                RoleHelper.requestDialerRole(this, roleRequestLauncher)
                return@setOnClickListener
            }
            requestCallPermissionsIfNeeded()
        }

        binding.callLogsButton.setOnClickListener {
            startActivity(Intent(this, CallLogActivity::class.java))
        }

        binding.notesButton.setOnClickListener {
            startActivity(Intent(this, NoteActivity::class.java))
        }

        binding.callStatsButton.setOnClickListener {
            startActivity(Intent(this, CallStatsActivity::class.java))
        }

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun requestCallPermissionsIfNeeded() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest) {
            permissionRequestLauncher.launch(permissions.toTypedArray())
        } else {
            placeCallIfPossible()
        }
    }

    private fun placeCallIfPossible() {
        val number = viewModel.dialedNumber.value?.trim() ?: ""
        if (number.isEmpty()) return

        startOutgoingCall(number)
    }

    private fun startOutgoingCall(number: String) {
        val telecomManager = getSystemService(TelecomManager::class.java)
        val accounts = getCallCapableAccounts(telecomManager)
        
        if (accounts.size > 1) {
            val preferred = SimPreferences.getPreferred(this, accounts)
            if (preferred != null) {
                placeCall(number, preferred)
            } else {
                showAccountPicker(number, accounts)
            }
        } else {
            placeCall(number, accounts.firstOrNull())
        }
    }

    private fun getCallCapableAccounts(telecomManager: TelecomManager): List<PhoneAccountHandle> {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            try {
                telecomManager.callCapablePhoneAccounts
            } catch (e: SecurityException) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    private fun showAccountPicker(number: String, accounts: List<PhoneAccountHandle>) {
        val telecomManager = getSystemService(TelecomManager::class.java)
        val labels = accounts.map { handle ->
            telecomManager.getPhoneAccount(handle)?.label?.toString() ?: "SIM"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_sim))
            .setItems(labels) { _, which ->
                placeCall(number, accounts[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun placeCall(number: String, account: PhoneAccountHandle?) {
        val uri = Uri.fromParts("tel", number, null)
        val telecomManager = getSystemService(TelecomManager::class.java)
        val extras = Bundle()
        if (account != null) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
        }
        try {
            telecomManager.placeCall(uri, extras)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!GesturePreferences.isVolumeShortcutsEnabled(this)) {
            return super.onKeyDown(keyCode, event)
        }

        val now = System.currentTimeMillis()
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (now - lastVolumeUpTime < 500) {
                    volumeUpPressCount++
                } else {
                    volumeUpPressCount = 1
                }
                lastVolumeUpTime = now
                if (volumeUpPressCount == 2) {
                    volumeUpPressCount = 0
                    callLatestMissed()
                    return true
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (now - lastVolumeDownTime < 500) {
                    volumeDownPressCount++
                } else {
                    volumeDownPressCount = 1
                }
                lastVolumeDownTime = now
                if (volumeDownPressCount == 2) {
                    volumeDownPressCount = 0
                    callLatestOutgoing()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun callLatestMissed() {
        lifecycleScope.launch {
            val latest = repository.getLatestMissed()
            if (latest != null) {
                startOutgoingCall(latest.phoneNumber)
            } else {
                Toast.makeText(this@MainActivity, "No missed calls found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun callLatestOutgoing() {
        lifecycleScope.launch {
            val latest = repository.getLatestOutgoing()
            if (latest != null) {
                startOutgoingCall(latest.phoneNumber)
            } else {
                Toast.makeText(this@MainActivity, "No outgoing calls found", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
