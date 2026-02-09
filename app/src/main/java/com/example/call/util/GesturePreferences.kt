package com.example.call.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object GesturePreferences {
    private const val PREFS_NAME = "gesture_prefs"
    private const val KEY_SHAKE_TO_ACCEPT = "shake_to_accept"
    private const val KEY_FLIP_TO_SILENCE = "flip_to_silence"
    private const val KEY_POWER_BUTTON_MUTES = "power_button_mutes"
    private const val KEY_VOLUME_SHORTCUTS = "volume_shortcuts"
    private const val KEY_THEME_MODE = "theme_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isShakeToAcceptEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHAKE_TO_ACCEPT, false)
    }

    fun setShakeToAcceptEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHAKE_TO_ACCEPT, enabled).apply()
    }

    fun isFlipToSilenceEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FLIP_TO_SILENCE, true)
    }

    fun setFlipToSilenceEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FLIP_TO_SILENCE, enabled).apply()
    }

    fun isPowerButtonMutesEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_POWER_BUTTON_MUTES, true)
    }

    fun setPowerButtonMutesEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_POWER_BUTTON_MUTES, enabled).apply()
    }

    fun isVolumeShortcutsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VOLUME_SHORTCUTS, true)
    }

    fun setVolumeShortcutsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VOLUME_SHORTCUTS, enabled).apply()
    }

    fun getThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    fun setThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
