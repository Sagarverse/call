package com.example.call.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

object AppSettings {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_SHAKE_TO_ACCEPT = "shake_to_accept"
    private const val KEY_FLIP_TO_SILENCE = "flip_to_silence"
    private const val KEY_POWER_BUTTON_MUTES = "power_button_mutes"
    private const val KEY_VOLUME_SHORTCUTS = "volume_shortcuts"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_AUTO_CALL_RECORDING = "auto_call_recording"
    private const val KEY_RECORDING_STORAGE_PATH = "recording_storage_path"
    private const val KEY_VOICEMAIL_NUMBER = "voicemail_number"
    private const val KEY_VIBRATE_ON_RING = "vibrate_on_ring"
    private const val KEY_RINGTONE_URI = "ringtone_uri"
    private const val KEY_CALL_THEME = "call_theme"

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

    fun isAutoCallRecordingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_CALL_RECORDING, false)
    }

    fun setAutoCallRecordingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_CALL_RECORDING, enabled).apply()
    }

    fun getRecordingStoragePath(context: Context): String? {
        return getPrefs(context).getString(KEY_RECORDING_STORAGE_PATH, null)
    }

    fun setRecordingStoragePath(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_RECORDING_STORAGE_PATH, path).apply()
    }

    fun getVoicemailNumber(context: Context): String? {
        return getPrefs(context).getString(KEY_VOICEMAIL_NUMBER, null)
    }

    fun setVoicemailNumber(context: Context, number: String?) {
        getPrefs(context).edit().putString(KEY_VOICEMAIL_NUMBER, number).apply()
    }

    fun isVibrateOnRingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VIBRATE_ON_RING, true)
    }

    fun setVibrateOnRingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VIBRATE_ON_RING, enabled).apply()
    }

    fun getRingtoneUri(context: Context): String? {
        return getPrefs(context).getString(KEY_RINGTONE_URI, null)
    }

    fun setRingtoneUri(context: Context, uri: String?) {
        getPrefs(context).edit().putString(KEY_RINGTONE_URI, uri).apply()
    }

    fun getCallTheme(context: Context): String {
        return getPrefs(context).getString(KEY_CALL_THEME, "default") ?: "default"
    }

    fun setCallTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_CALL_THEME, theme).apply()
    }
}
