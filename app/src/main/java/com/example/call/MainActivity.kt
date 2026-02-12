package com.example.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.example.call.R
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.databinding.ActivityDialerBinding
import com.example.call.ui.dialer.DialerViewModel
import com.example.call.ui.main.ViewPagerAdapter
import com.example.call.ui.settings.SettingsActivity
import com.example.call.util.RoleHelper
import com.example.call.util.SimPreferences

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDialerBinding
    private lateinit var viewModel: DialerViewModel
    private lateinit var repository: CallLogRepository

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

        setupViewPager()
        setupBottomNav()
        
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (!RoleHelper.isDefaultDialer(this)) {
            RoleHelper.requestDialerRole(this, roleRequestLauncher)
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ViewPagerAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateTabs(position)
            }
        })
    }

    private fun setupBottomNav() {
        binding.dialerTab.setOnClickListener { binding.viewPager.currentItem = 0 }
        binding.callLogsTab.setOnClickListener { binding.viewPager.currentItem = 1 }
        binding.contactsTab.setOnClickListener { binding.viewPager.currentItem = 2 }
        binding.statsTab.setOnClickListener { binding.viewPager.currentItem = 3 }
    }

    private fun updateTabs(position: Int) {
        val activeColor = ContextCompat.getColor(this, R.color.call_green)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        binding.dialerIndicator.visibility = if (position == 0) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.dialerButton.setTextColor(if (position == 0) activeColor else inactiveColor)

        binding.logsIndicator.visibility = if (position == 1) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.callLogsButton.setTextColor(if (position == 1) activeColor else inactiveColor)

        binding.contactsIndicator.visibility = if (position == 2) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.contactsButton.setTextColor(if (position == 2) activeColor else inactiveColor)

        binding.statsIndicator.visibility = if (position == 3) android.view.View.VISIBLE else android.view.View.INVISIBLE
        binding.callStatsButton.setTextColor(if (position == 3) activeColor else inactiveColor)
    }

    fun showDialerPage() {
        binding.viewPager.currentItem = 0
    }

    fun requestCallPermissionsIfNeeded() {
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
        
        if (RoleHelper.isDefaultDialer(this)) {
            try {
                telecomManager.placeCall(uri, extras)
            } catch (e: SecurityException) {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                val callIntent = Intent(Intent.ACTION_CALL, uri)
                if (account != null) {
                    callIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                }
                startActivity(callIntent)
            }
        } else {
            val callIntent = Intent(Intent.ACTION_CALL, uri)
            if (account != null) {
                callIntent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
            }
            startActivity(callIntent)
        }
    }
}
