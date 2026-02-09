package com.example.call.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.example.call.data.AppDatabase
import com.example.call.data.CallLogRepository
import com.example.call.ui.call.CallSummaryActivity
import com.example.call.ui.call.IncomingCallActivity
import com.example.call.ui.call.OngoingCallActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallInCallService : InCallService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            when (state) {
                Call.STATE_RINGING -> showIncoming(call)
                Call.STATE_ACTIVE -> showOngoing(call)
                Call.STATE_DISCONNECTED -> {
                    saveCallToLog(call)
                    showSummary()
                }
                else -> showOngoing(call)
            }
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallController.updateAudioState(audioState)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallController.bindService(this)
        CallController.updateCall(call)
        call.registerCallback(callCallback)
        
        when (call.state) {
            Call.STATE_RINGING -> showIncoming(call)
            Call.STATE_DISCONNECTED -> {
                saveCallToLog(call)
                showSummary()
            }
            else -> showOngoing(call)
        }
        OngoingCallService.start(this)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        CallController.updateCall(null)
        CallController.bindService(null)
        OngoingCallService.stop(this)
    }

    override fun onDestroy() {
        CallController.bindService(null)
        super.onDestroy()
    }

    private fun saveCallToLog(call: Call) {
        serviceScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(applicationContext).callLogDao()
            val repository = CallLogRepository(dao)
            repository.saveCallFromTelecom(call)
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

    private fun showSummary() {
        val intent = Intent(this, CallSummaryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }
}
