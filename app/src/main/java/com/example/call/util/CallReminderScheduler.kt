package com.example.call.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.call.R

object CallReminderScheduler {
    private const val CHANNEL_ID = "call_reminders"

    fun schedule(context: Context, displayName: String?, number: String, triggerAtMillis: Long) {
        ensureChannel(context)
        val intent = Intent(context, CallReminderReceiver::class.java).apply {
            putExtra(CallReminderReceiver.EXTRA_NAME, displayName)
            putExtra(CallReminderReceiver.EXTRA_NUMBER, number)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (number.hashCode() * 13) + triggerAtMillis.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }

    fun showReminderNotification(context: Context, displayName: String?, number: String) {
        ensureChannel(context)
        val title = context.getString(R.string.call_back_reminder)
        val text = displayName?.takeIf { it.isNotBlank() } ?: number

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.fromParts("tel", number, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val dialPending = PendingIntent.getActivity(
            context,
            number.hashCode(),
            dialIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(dialPending)
            .addAction(0, context.getString(R.string.call), dialPending)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify((number.hashCode() * 19) + 5, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.call_back_reminder),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }
}
