package com.example.call.ui.dialer

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DialerViewModel : ViewModel() {
    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber

    fun appendDigit(value: String) {
        _dialedNumber.value += value
    }

    fun removeLastDigit() {
        val current = _dialedNumber.value
        if (current.isNotEmpty()) {
            _dialedNumber.value = current.dropLast(1)
        }
    }

    fun clearDigits() {
        _dialedNumber.value = ""
    }
}
