package com.example.call.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.call.R
import com.example.call.databinding.ActivitySettingsBinding
import com.example.call.ui.sim.SimManagementActivity
import com.example.call.ui.permissions.PermissionsActivity
import com.example.call.ui.notes.NoteActivity
import com.example.call.util.GesturePreferences

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        setupSwitches()
        setupThemeToggle()
    }

    private fun setupButtons() {
        binding.permissionsButton.setOnClickListener {
            startActivity(Intent(this, PermissionsActivity::class.java))
        }

        binding.simButton.setOnClickListener {
            startActivity(Intent(this, SimManagementActivity::class.java))
        }

        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupSwitches() {
        binding.shakeToAcceptSwitch.isChecked = GesturePreferences.isShakeToAcceptEnabled(this)
        binding.shakeToAcceptSwitch.setOnCheckedChangeListener { _, isChecked ->
            GesturePreferences.setShakeToAcceptEnabled(this, isChecked)
        }

        binding.flipToSilenceSwitch.isChecked = GesturePreferences.isFlipToSilenceEnabled(this)
        binding.flipToSilenceSwitch.setOnCheckedChangeListener { _, isChecked ->
            GesturePreferences.setFlipToSilenceEnabled(this, isChecked)
        }

        binding.powerButtonMutesSwitch.isChecked = GesturePreferences.isPowerButtonMutesEnabled(this)
        binding.powerButtonMutesSwitch.setOnCheckedChangeListener { _, isChecked ->
            GesturePreferences.setPowerButtonMutesEnabled(this, isChecked)
        }

        binding.volumeShortcutsSwitch.isChecked = GesturePreferences.isVolumeShortcutsEnabled(this)
        binding.volumeShortcutsSwitch.setOnCheckedChangeListener { _, isChecked ->
            GesturePreferences.setVolumeShortcutsEnabled(this, isChecked)
        }
    }

    private fun setupThemeToggle() {
        val currentMode = GesturePreferences.getThemeMode(this)
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
                GesturePreferences.setThemeMode(this, mode)
            }
        }
    }
}
