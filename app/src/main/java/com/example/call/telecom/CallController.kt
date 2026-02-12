package com.example.call.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CallController {
    data class CallListState(
        val calls: List<Call>,
        val version: Long
    )

    private val _calls = MutableStateFlow(CallListState(emptyList(), 0L))
    val calls: StateFlow<CallListState> = _calls

    private val _currentCallFlow = MutableStateFlow<Call?>(null)
    val currentCallFlow: StateFlow<Call?> = _currentCallFlow

    var currentCall: Call?
        get() = _currentCallFlow.value
        private set(value) {
            _currentCallFlow.value = value
        }

    @Volatile
    var inCallService: CallInCallService? = null
        private set

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState

    fun updateCall(call: Call?) {
        currentCall = call
    }

    fun addCall(call: Call) {
        val updated = (_calls.value.calls + call).distinct()
        updateCalls(updated)
        updateCurrentCall()
    }

    fun removeCall(call: Call) {
        val updated = _calls.value.calls.filter { it != call }
        updateCalls(updated)
        updateCurrentCall()
    }

    fun onCallStateChanged(call: Call) {
        if (_calls.value.calls.contains(call)) {
            touchCalls()
            updateCurrentCall()
        }
    }

    fun onCallDetailsChanged(call: Call) {
        if (_calls.value.calls.contains(call)) {
            touchCalls()
        }
    }

    fun onConferenceableCallsChanged(call: Call) {
        if (_calls.value.calls.contains(call)) {
            touchCalls()
        }
    }

    fun bindService(service: CallInCallService?) {
        inCallService = service
    }

    fun updateAudioState(state: CallAudioState) {
        _audioState.value = state
    }

    fun getPrimaryCall(): Call? = currentCall

    fun getActiveCall(): Call? = _calls.value.calls.firstOrNull { it.state == Call.STATE_ACTIVE }

    fun getHoldingCall(): Call? = _calls.value.calls.firstOrNull { it.state == Call.STATE_HOLDING }

    fun canSwap(): Boolean = getActiveCall() != null && getHoldingCall() != null

    fun swapCalls() {
        val active = getActiveCall()
        val holding = getHoldingCall()
        if (active != null && holding != null) {
            active.hold()
            holding.unhold()
            updateCurrentCall()
        }
    }

    fun canMerge(): Boolean {
        val active = getActiveCall()
        val holding = getHoldingCall()
        if (active == null || holding == null) return false
        val activeCanMerge = ((active.details?.callCapabilities ?: 0) and
            Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
        val holdingCanMerge = ((holding.details?.callCapabilities ?: 0) and
            Call.Details.CAPABILITY_MERGE_CONFERENCE) != 0
        val conferenceable = active.conferenceableCalls.contains(holding) || holding.conferenceableCalls.contains(active)
        return activeCanMerge || holdingCanMerge || conferenceable
    }

    fun mergeCalls() {
        val active = getActiveCall()
        val holding = getHoldingCall()
        if (active == null || holding == null) return
        if (active.conferenceableCalls.contains(holding)) {
            active.conference(holding)
        } else if (holding.conferenceableCalls.contains(active)) {
            holding.conference(active)
        } else {
            active.conference(holding)
        }
        updateCurrentCall()
    }

    fun accept() {
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        currentCall?.reject(false, null)
    }

    fun disconnect() {
        currentCall?.disconnect()
    }

    fun setMuted(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    fun toggleSpeaker(enabled: Boolean) {
        val route = if (enabled) {
            CallAudioState.ROUTE_SPEAKER
        } else {
            CallAudioState.ROUTE_EARPIECE
        }
        inCallService?.setAudioRoute(route)
    }

    fun setAudioRoute(route: Int) {
        inCallService?.setAudioRoute(route)
    }

    fun playDtmf(tone: Char) {
        currentCall?.playDtmfTone(tone)
    }

    fun stopDtmf() {
        currentCall?.stopDtmfTone()
    }

    private fun updateCurrentCall() {
        val calls = _calls.value.calls
        val active = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
        val dialing = calls.firstOrNull {
            it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING
        }
        val holding = calls.firstOrNull { it.state == Call.STATE_HOLDING }
        currentCall = active ?: dialing ?: holding ?: calls.firstOrNull()
    }

    private fun updateCalls(updatedCalls: List<Call>) {
        _calls.value = CallListState(updatedCalls, _calls.value.version + 1)
    }

    private fun touchCalls() {
        _calls.value = _calls.value.copy(version = _calls.value.version + 1)
    }
}
