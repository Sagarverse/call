package com.example.call.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.content.ContextCompat
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.ui.call.CallSummaryActivity
import com.example.call.ui.call.IncomingCallActivity
import com.example.call.ui.call.OngoingCallActivity
import com.example.call.util.MissedCallNotifier
import com.example.call.util.CalendarEventHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallInCallService : InCallService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            CallController.onCallStateChanged(call)
            when (state) {
                Call.STATE_RINGING -> showIncoming(call)
                Call.STATE_ACTIVE -> showOngoing(call)
                Call.STATE_DISCONNECTED -> {
                    handleCallEnd(call)
                }
                else -> showOngoing(call)
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            CallController.onCallDetailsChanged(call)
        }

        override fun onConferenceableCallsChanged(
            call: Call,
            conferenceableCalls: MutableList<Call>
        ) {
            super.onConferenceableCallsChanged(call, conferenceableCalls)
            CallController.onConferenceableCallsChanged(call)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallController.updateAudioState(audioState)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallController.bindService(this)
        CallController.addCall(call)
        call.registerCallback(callCallback)
        
        when (call.state) {
            Call.STATE_RINGING -> showIncoming(call)
            Call.STATE_DISCONNECTED -> handleCallEnd(call)
            else -> showOngoing(call)
        }
        OngoingCallService.start(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        CallController.removeCall(call)
        if (CallController.calls.value.calls.isEmpty()) {
            CallController.bindService(null)
            OngoingCallService.stop(this)
        }
    }

    private fun handleCallEnd(call: Call) {
        val details = call.details
        val isMissed = details.callDirection == Call.Details.DIRECTION_INCOMING &&
            details.connectTimeMillis == 0L
        if (isMissed) {
            val displayName = details.callerDisplayName?.toString()
            val number = details.handle?.schemeSpecificPart.orEmpty()
            if (number.isNotBlank()) {
                MissedCallNotifier.showMissedCall(this, displayName, number)
            }
        }
        val name = details.callerDisplayName?.toString()?.takeIf { it.isNotBlank() }
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val start = if (details.connectTimeMillis > 0L) {
            details.connectTimeMillis
        } else {
            details.creationTimeMillis
        }
        val end = System.currentTimeMillis().coerceAtLeast(start + 60_000L)
        CalendarEventHelper.insertCallEvent(
            this,
            if (name != null) "Call with $name" else "Call with $number",
            number,
            start,
            end
        )
        serviceScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(applicationContext).callLogDao()
            val repository = CallLogRepository(dao)
            val logId = repository.saveCallFromTelecom(
                applicationContext.contentResolver,
                call
            )
            
            launch(Dispatchers.Main) {
                showSummary(logId)
            }
        }
    }

    private fun showIncoming(call: Call) {
        val intent = Intent(this, IncomingCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun showOngoing(call: Call) {
        val intent = Intent(this, OngoingCallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun showSummary(logId: Long) {
        val intent = Intent(this, CallSummaryActivity::class.java).apply {
            putExtra("EXTRA_LOG_ID", logId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }
}
