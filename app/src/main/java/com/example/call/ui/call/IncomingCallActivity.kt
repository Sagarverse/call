package com.example.call.ui.call

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.call.R
import com.example.call.databinding.ActivityIncomingCallBinding
import com.example.call.telecom.CallController
import com.example.call.util.ContactLookup
import com.example.call.util.AppSettings
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlin.math.abs

class IncomingCallActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityIncomingCallBinding
    private lateinit var gestureDetector: GestureDetector
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 20.0f
    private var isShakeEnabled = true

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setupGestures()
        setupSensors()

        binding.acceptCall.setOnClickListener { answerCall() }
        binding.rejectCall.setOnClickListener { declineCall() }

        binding.incomingRoot.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (AppSettings.isShakeToAcceptEnabled(this)) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        if (AppSettings.isFlipToSilenceEnabled(this)) {
            proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                if (abs(diffX) > abs(diffY)) {
                    // Horizontal Swipe
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) onSwipeRight() else onSwipeLeft()
                        return true
                    }
                } else {
                    // Vertical Swipe
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) onSwipeUp() else onSwipeDown()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun onSwipeUp() = answerCall()
    private fun onSwipeDown() = declineCall()
    private fun onSwipeRight() = silenceRinger()
    private fun onSwipeLeft() = showQuickReplies()

    private fun answerCall() {
        CallController.accept()
        finish()
    }

    private fun declineCall() {
        CallController.reject()
        finish()
    }

    private fun showQuickReplies() {
        val replies = listOf(
            "Can't talk now. What's up?",
            "I'll call you back soon.",
            "In a meeting. Text me?",
            "Driving, call you later.",
            "Can't talk now. Call me later?"
        )

        val dialog = BottomSheetDialog(this, com.google.android.material.R.style.Theme_Material3_Light_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_quick_replies, null)
        val listView = view.findViewById<android.widget.ListView>(R.id.repliesList)
        
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, replies)
        listView.setOnItemClickListener { _, _, position, _ ->
            sendQuickReply(replies[position])
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun sendQuickReply(message: String) {
        val number = CallController.currentCall?.details?.handle?.schemeSpecificPart.orEmpty()
        if (number.isNotEmpty()) {
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(android.telephony.SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    android.telephony.SmsManager.getDefault()
                }
                smsManager.sendTextMessage(number, null, message, null, null)
                Toast.makeText(this, "Reply sent", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to send SMS", Toast.LENGTH_SHORT).show()
            }
        }
        declineCall()
    }

    override fun onStart() {
        super.onStart()
        bindCallerInfo()
    }

    private fun bindCallerInfo() {
        val number = CallController.currentCall?.details?.handle?.schemeSpecificPart.orEmpty()
        val lookup = ContactLookup.lookup(this, number)
        binding.callerName.text = lookup.displayName ?: getString(R.string.unknown_caller)
        binding.callerNumber.text = number

        lookup.photo?.let {
            binding.callerAvatar.setImageBitmap(it)
            binding.callerBackground.setImageBitmap(it)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (AppSettings.isPowerButtonMutesEnabled(this) && keyCode == KeyEvent.KEYCODE_POWER) {
            silenceRinger()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleShake(event)
            Sensor.TYPE_PROXIMITY -> handleFlip(event)
        }
    }

    private fun handleShake(event: SensorEvent) {
        if (!isShakeEnabled) return
        val now = System.currentTimeMillis()
        if ((now - lastShakeTime) > 1000) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val acceleration = (x * x + y * y + z * z) / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH)
            if (acceleration > SHAKE_THRESHOLD) { 
                lastShakeTime = now
                isShakeEnabled = false
                answerCall()
                Handler(Looper.getMainLooper()).postDelayed({ isShakeEnabled = true }, 2000)
            }
        }
    }

    private fun handleFlip(event: SensorEvent) {
        if (event.values[0] < (proximitySensor?.maximumRange ?: 1f)) {
            silenceRinger()
        }
    }

    private fun silenceRinger() {
        val telecomManager = getSystemService(TelecomManager::class.java)
        telecomManager?.silenceRinger()
        Toast.makeText(this, "Ringer silenced", Toast.LENGTH_SHORT).show()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
