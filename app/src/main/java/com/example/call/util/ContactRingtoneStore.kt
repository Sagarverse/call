package com.example.call.util

import android.content.Context
import org.json.JSONObject

object ContactRingtoneStore {
    private const val PREFS_NAME = "contact_ringtones"
    private const val KEY_MAP = "ringtone_map"

    fun getRingtoneUri(context: Context, number: String): String? {
        val normalized = normalizeNumber(number)
        if (normalized.isBlank()) return null
        val map = readMap(context)
        if (!map.has(normalized)) return null
        return map.optString(normalized).takeIf { it.isNotBlank() }
    }

    fun setRingtoneUri(context: Context, number: String, uri: String?) {
        val normalized = normalizeNumber(number)
        if (normalized.isBlank()) return
        val map = readMap(context)
        if (uri.isNullOrBlank()) {
            map.remove(normalized)
        } else {
            map.put(normalized, uri)
        }
        writeMap(context, map)
    }

    private fun readMap(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MAP, null)
        return try {
            if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeMap(context: Context, map: JSONObject) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MAP, map.toString())
            .apply()
    }

    private fun normalizeNumber(number: String): String {
        val trimmed = number.trim()
        val builder = StringBuilder(trimmed.length)
        for (ch in trimmed) {
            if (ch.isDigit() || ch == '+') {
                builder.append(ch)
            }
        }
        return builder.toString()
    }
}
