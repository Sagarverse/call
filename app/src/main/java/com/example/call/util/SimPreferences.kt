package com.example.call.util

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import androidx.core.content.edit

object SimPreferences {
    private const val PREFS = "sim_prefs"
    private const val KEY_COMPONENT = "preferred_component"
    private const val KEY_ID = "preferred_id"

    fun setPreferred(context: Context, handle: PhoneAccountHandle?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (handle == null) {
            prefs.edit { clear() }
            return
        }
        prefs.edit {
            putString(KEY_COMPONENT, handle.componentName.flattenToString())
            putString(KEY_ID, handle.id)
        }
    }

    fun getPreferred(context: Context, available: List<PhoneAccountHandle>): PhoneAccountHandle? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val componentString = prefs.getString(KEY_COMPONENT, null) ?: return null
        val id = prefs.getString(KEY_ID, null) ?: return null
        val component = ComponentName.unflattenFromString(componentString) ?: return null
        return available.firstOrNull { handle ->
            handle.componentName == component && handle.id == id
        }
    }
}
