package com.example.call.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telecom.Call
import androidx.core.app.NotificationCompat
import com.example.call.R
import com.example.call.ui.call.OngoingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OngoingCallService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_END_CALL -> CallController.disconnect()
            ACTION_TOGGLE_MUTE -> {
                val muted = CallController.audioState.value?.isMuted ?: false
                CallController.setMuted(!muted)
            }
            ACTION_TOGGLE_SPEAKER -> {
                val isSpeaker = CallController.audioState.value?.route == android.telecom.CallAudioState.ROUTE_SPEAKER
                CallController.toggleSpeaker(!isSpeaker)
            }
            ACTION_TOGGLE_HOLD -> {
                val call = CallController.currentCall
                if (call != null) {
                    if (call.state == Call.STATE_HOLDING) {
                        call.unhold()
                    } else {
                        call.hold()
                    }
                }
            }
            ACTION_OPEN_CALL -> openCallScreen()
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            CallController.currentCallFlow.collectLatest {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        serviceScope.launch {
            CallController.audioState.collectLatest {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()
        val call = CallController.currentCall
        val displayName = resolveDisplayName(call)
        val startTime = resolveCallStartTime(call)
        val isMuted = CallController.audioState.value?.isMuted ?: false
        val isSpeaker = CallController.audioState.value?.route == android.telecom.CallAudioState.ROUTE_SPEAKER

        val openIntent = Intent(this, OngoingCallService::class.java).apply { action = ACTION_OPEN_CALL }
        val openPending = PendingIntent.getService(
            this,
            REQUEST_OPEN,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(this, OngoingCallService::class.java).apply { action = ACTION_END_CALL }
        val endPending = PendingIntent.getService(
            this,
            REQUEST_END,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, OngoingCallService::class.java).apply { action = ACTION_TOGGLE_MUTE }
        val mutePending = PendingIntent.getService(
            this,
            REQUEST_MUTE,
            muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val speakerIntent = Intent(this, OngoingCallService::class.java).apply { action = ACTION_TOGGLE_SPEAKER }
        val speakerPending = PendingIntent.getService(
            this,
            REQUEST_SPEAKER,
            speakerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val holdIntent = Intent(this, OngoingCallService::class.java).apply { action = ACTION_TOGGLE_HOLD }
        val holdPending = PendingIntent.getService(
            this,
            REQUEST_HOLD,
            holdIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CONTENT,
            Intent(this, OngoingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayName)
            .setContentText(getString(R.string.ongoing_call))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setUsesChronometer(startTime > 0L)
            .setWhen(if (startTime > 0L) startTime else System.currentTimeMillis())
            .addAction(
                0,
                if (isMuted) getString(R.string.unmute) else getString(R.string.mute),
                mutePending
            )
            .addAction(
                0,
                if (isSpeaker) getString(R.string.speaker_off) else getString(R.string.speaker),
                speakerPending
            )
            .addAction(
                0,
                if (call?.state == Call.STATE_HOLDING) getString(R.string.unhold) else getString(R.string.hold),
                holdPending
            )
            .addAction(0, getString(R.string.return_to_call), openPending)
            .addAction(0, getString(R.string.end_call), endPending)
            .setOngoing(true)
            .build()
    }

    private fun resolveDisplayName(call: Call?): String {
        val details = call?.details
        val displayName = details?.callerDisplayName?.toString()?.trim().orEmpty()
        if (displayName.isNotEmpty()) return displayName
        val number = details?.handle?.schemeSpecificPart?.trim().orEmpty()
        return if (number.isNotEmpty()) number else getString(R.string.ongoing_call)
    }

    private fun resolveCallStartTime(call: Call?): Long {
        val details = call?.details ?: return 0L
        return if (details.connectTimeMillis > 0L) details.connectTimeMillis else details.creationTimeMillis
    }

    private fun openCallScreen() {
        val intent = Intent(this, OngoingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.ongoing_call),
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    companion object {
        private const val CHANNEL_ID = "call_ongoing"
        private const val NOTIFICATION_ID = 101
        private const val ACTION_END_CALL = "com.example.call.ACTION_END_CALL"
        private const val ACTION_TOGGLE_MUTE = "com.example.call.ACTION_TOGGLE_MUTE"
        private const val ACTION_TOGGLE_SPEAKER = "com.example.call.ACTION_TOGGLE_SPEAKER"
        private const val ACTION_TOGGLE_HOLD = "com.example.call.ACTION_TOGGLE_HOLD"
        private const val ACTION_OPEN_CALL = "com.example.call.ACTION_OPEN_CALL"

        private const val REQUEST_CONTENT = 200
        private const val REQUEST_END = 201
        private const val REQUEST_MUTE = 202
        private const val REQUEST_SPEAKER = 203
        private const val REQUEST_HOLD = 204
        private const val REQUEST_OPEN = 205

        fun start(context: Context) {
            val intent = Intent(context, OngoingCallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OngoingCallService::class.java))
        }
    }
}
