package com.example.call.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import android.media.RingtoneManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.call.R
import com.example.call.databinding.ActivitySettingsBinding
import com.example.call.ui.sim.SimManagementActivity
import com.example.call.ui.permissions.PermissionsActivity
import com.example.call.util.AppSettings
import com.example.call.util.CallThemeManager

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

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        AppSettings.setRingtoneUri(this, uri?.toString())
        updateRingtoneUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        setupSwitches()
        setupThemeToggle()
        setupCallThemeToggle()
        updateRecordingPathUI()
        updateVoicemailUI()
        updateRingtoneUI()
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

        binding.ringtoneButton.setOnClickListener {
            launchRingtonePicker()
        }

        binding.voicemailNumberButton.setOnClickListener {
            showVoicemailDialog()
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

        binding.vibrateOnRingSwitch.isChecked = AppSettings.isVibrateOnRingEnabled(this)
        binding.vibrateOnRingSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setVibrateOnRingEnabled(this, isChecked)
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

    private fun setupCallThemeToggle() {
        val current = AppSettings.getCallTheme(this)
        when (current) {
            CallThemeManager.THEME_OCEAN -> binding.callThemeToggle.check(R.id.callThemeOcean)
            CallThemeManager.THEME_SUNSET -> binding.callThemeToggle.check(R.id.callThemeSunset)
            CallThemeManager.THEME_NEON -> binding.callThemeToggle.check(R.id.callThemeNeon)
            else -> binding.callThemeToggle.check(R.id.callThemeDefault)
        }

        updateThemePreviewSelection(current)

        binding.callThemePreviewDefault.setOnClickListener {
            binding.callThemeToggle.check(R.id.callThemeDefault)
        }
        binding.callThemePreviewOcean.setOnClickListener {
            binding.callThemeToggle.check(R.id.callThemeOcean)
        }
        binding.callThemePreviewSunset.setOnClickListener {
            binding.callThemeToggle.check(R.id.callThemeSunset)
        }
        binding.callThemePreviewNeon.setOnClickListener {
            binding.callThemeToggle.check(R.id.callThemeNeon)
        }

        binding.callThemeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val theme = when (checkedId) {
                R.id.callThemeOcean -> CallThemeManager.THEME_OCEAN
                R.id.callThemeSunset -> CallThemeManager.THEME_SUNSET
                R.id.callThemeNeon -> CallThemeManager.THEME_NEON
                else -> CallThemeManager.THEME_DEFAULT
            }
            AppSettings.setCallTheme(this, theme)
            updateThemePreviewSelection(theme)
        }
    }

    private fun updateThemePreviewSelection(theme: String) {
        val selected = ContextCompat.getColor(this, R.color.white)
        val normal = ContextCompat.getColor(this, R.color.glass_stroke)

        binding.callThemePreviewDefault.strokeColor = if (theme == CallThemeManager.THEME_DEFAULT) {
            selected
        } else normal
        binding.callThemePreviewOcean.strokeColor = if (theme == CallThemeManager.THEME_OCEAN) {
            selected
        } else normal
        binding.callThemePreviewSunset.strokeColor = if (theme == CallThemeManager.THEME_SUNSET) {
            selected
        } else normal
        binding.callThemePreviewNeon.strokeColor = if (theme == CallThemeManager.THEME_NEON) {
            selected
        } else normal
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

    private fun launchRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            val existing = AppSettings.getRingtoneUri(this@SettingsActivity)
            if (!existing.isNullOrBlank()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
            }
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun updateRingtoneUI() {
        val uri = AppSettings.getRingtoneUri(this)
        if (uri.isNullOrBlank()) {
            binding.ringtoneText.text = getString(R.string.ringtone_default)
            return
        }
        val ringtone = RingtoneManager.getRingtone(this, Uri.parse(uri))
        binding.ringtoneText.text = ringtone?.getTitle(this) ?: getString(R.string.ringtone_default)
    }

    private fun showVoicemailDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(AppSettings.getVoicemailNumber(this@SettingsActivity).orEmpty())
            setSelection(text?.length ?: 0)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.voicemail_number))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val number = input.text?.toString()?.trim()
                AppSettings.setVoicemailNumber(this, number?.ifBlank { null })
                updateVoicemailUI()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateVoicemailUI() {
        val number = AppSettings.getVoicemailNumber(this)
        binding.voicemailNumberText.text = number ?: getString(R.string.voicemail_not_set)
    }
}
