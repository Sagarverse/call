package com.example.call.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.call.R
import com.example.call.databinding.ActivitySettingsBinding
import com.example.call.ui.sim.SimManagementActivity
import com.example.call.ui.permissions.PermissionsActivity
import com.example.call.util.AppSettings

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            AppSettings.setRecordingStoragePath(this, it.toString())
            binding.recordingPathText.text = it.path
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        setupSwitches()
        setupThemeToggle()
        updateRecordingPathUI()
    }

    private fun setupButtons() {
        binding.permissionsButton.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        binding.simButton.setOnClickListener {
            startActivity(Intent(this, SimManagementActivity::class.java))
        }

        binding.recordingLocationButton.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupSwitches() {
        binding.shakeToAcceptSwitch.isChecked = AppSettings.isShakeToAcceptEnabled(this)
        binding.shakeToAcceptSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setShakeToAcceptEnabled(this, isChecked)
        }

        binding.flipToSilenceSwitch.isChecked = AppSettings.isFlipToSilenceEnabled(this)
        binding.flipToSilenceSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setFlipToSilenceEnabled(this, isChecked)
        }

        binding.powerButtonMutesSwitch.isChecked = AppSettings.isPowerButtonMutesEnabled(this)
        binding.powerButtonMutesSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setPowerButtonMutesEnabled(this, isChecked)
        }

        binding.volumeShortcutsSwitch.isChecked = AppSettings.isVolumeShortcutsEnabled(this)
        binding.volumeShortcutsSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setVolumeShortcutsEnabled(this, isChecked)
        }

        binding.autoRecordSwitch.isChecked = AppSettings.isAutoCallRecordingEnabled(this)
        binding.autoRecordSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setAutoCallRecordingEnabled(this, isChecked)
        }
    }

    private fun setupThemeToggle() {
        val currentMode = AppSettings.getThemeMode(this)
        when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.themeToggleGroup.check(R.id.themeLight)
            AppCompatDelegate.MODE_NIGHT_YES -> binding.themeToggleGroup.check(R.id.themeDark)
            else -> binding.themeToggleGroup.check(R.id.themeSystem)
        }

        binding.themeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val mode = when (checkedId) {
                    R.id.themeLight -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                AppSettings.setThemeMode(this, mode)
            }
        }
    }

    private fun updateRecordingPathUI() {
        val path = AppSettings.getRecordingStoragePath(this)
        if (path != null) {
            val uri = Uri.parse(path)
            binding.recordingPathText.text = uri.path
        } else {
            binding.recordingPathText.text = "Default: Internal Storage/Recordings"
        }
    }
}
