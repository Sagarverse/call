package com.example.call.ui.sim

import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.example.call.databinding.ActivitySimManagementBinding
import com.example.call.util.SimPreferences

class SimManagementActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySimManagementBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        renderAccounts()
    }

    private fun renderAccounts() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            binding.simGroup.removeAllViews()
            binding.simGroup.clearCheck()
            binding.emptyState.text = getString(com.example.call.R.string.permissions_needed)
            return
        }

        val telecomManager = getSystemService(TelecomManager::class.java)
        val accounts = telecomManager.callCapablePhoneAccounts
        val preferred = SimPreferences.getPreferred(this, accounts)

        binding.simGroup.removeAllViews()
        binding.simGroup.clearCheck()

        if (accounts.isEmpty()) {
            binding.emptyState.text = getString(com.example.call.R.string.no_sim_found)
            return
        }

        binding.emptyState.text = ""
        accounts.forEach { handle ->
            val label = telecomManager.getPhoneAccount(handle)?.label?.toString() ?: "SIM"
            val radio = RadioButton(this).apply {
                text = label
                isChecked = handle == preferred
                setOnClickListener {
                    SimPreferences.setPreferred(this@SimManagementActivity, handle)
                }
            }
            binding.simGroup.addView(radio)
        }

        binding.clearPreference.setOnClickListener {
            SimPreferences.setPreferred(this, null)
            renderAccounts()
        }
    }
}
