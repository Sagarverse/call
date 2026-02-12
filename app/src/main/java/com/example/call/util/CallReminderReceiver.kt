package com.example.call.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_NUMBER = "extra_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME)
        val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
        CallReminderScheduler.showReminderNotification(context, name, number)
    }
}
