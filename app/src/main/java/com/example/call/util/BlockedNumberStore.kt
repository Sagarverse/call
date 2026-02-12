package com.example.call.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BlockedNumberStore {
    private const val PREFS_NAME = "blocked_numbers"
    private const val KEY_BLOCKED = "blocked_json"

    fun isBlocked(context: Context, number: String): Boolean {
        val normalized = normalizeNumber(number)
        if (normalized.isBlank()) return false
        return getBlockedSet(context).contains(normalized)
    }

    fun block(context: Context, number: String) {
        val normalized = normalizeNumber(number)
        if (normalized.isBlank()) return
        val current = getBlockedSet(context).toMutableSet()
        if (current.add(normalized)) {
            save(context, current)
        }
    }

    fun unblock(context: Context, number: String) {
        val normalized = normalizeNumber(number)
        if (normalized.isBlank()) return
        val current = getBlockedSet(context).toMutableSet()
        if (current.remove(normalized)) {
            save(context, current)
        }
    }

    private fun getBlockedSet(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BLOCKED, null) ?: return emptySet()
        return try {
            val json = JSONArray(raw)
            val results = HashSet<String>(json.length())
            for (i in 0 until json.length()) {
                val value = json.optString(i)
                if (value.isNotBlank()) {
                    results.add(value)
                }
            }
            results
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun save(context: Context, numbers: Set<String>) {
        val array = JSONArray()
        numbers.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BLOCKED, array.toString())
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
