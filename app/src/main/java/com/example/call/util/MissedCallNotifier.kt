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

object MissedCallNotifier {
    private const val CHANNEL_ID = "missed_calls"
    private const val REMINDER_DELAY_MILLIS = 10 * 60 * 1000L

    fun showMissedCall(context: Context, displayName: String?, number: String) {
        ensureChannel(context)
        val title = context.getString(R.string.missed_call)
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
        manager.notify(number.hashCode(), notification)

        scheduleReminder(context, displayName, number)
    }

    fun showReminder(context: Context, displayName: String?, number: String) {
        ensureChannel(context)
        val title = context.getString(R.string.missed_call_reminder)
        val text = displayName?.takeIf { it.isNotBlank() } ?: number

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.fromParts("tel", number, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val dialPending = PendingIntent.getActivity(
            context,
            (number.hashCode() * 31) + 1,
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
        manager.notify((number.hashCode() * 31) + 1, notification)
    }

    private fun scheduleReminder(context: Context, displayName: String?, number: String) {
        val intent = Intent(context, MissedCallReminderReceiver::class.java).apply {
            putExtra(MissedCallReminderReceiver.EXTRA_NAME, displayName)
            putExtra(MissedCallReminderReceiver.EXTRA_NUMBER, number)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (number.hashCode() * 17) + 7,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + REMINDER_DELAY_MILLIS
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.missed_call),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
    }
}
