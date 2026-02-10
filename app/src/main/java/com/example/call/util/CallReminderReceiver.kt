package com.example.call.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        if (number.isBlank()) return
        val name = intent.getStringExtra(EXTRA_NAME)
        CallReminderScheduler.showReminderNotification(context, name, number)
    }

    companion object {
        const val EXTRA_NUMBER = "extra_number"
        const val EXTRA_NAME = "extra_name"
    }
}
