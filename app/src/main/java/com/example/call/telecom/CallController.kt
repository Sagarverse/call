package com.example.call.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CallController {
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

    fun bindService(service: CallInCallService?) {
        inCallService = service
    }

    fun updateAudioState(state: CallAudioState) {
        _audioState.value = state
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

    fun playDtmf(tone: Char) {
        currentCall?.playDtmfTone(tone)
    }

    fun stopDtmf() {
        currentCall?.stopDtmfTone()
    }
}
