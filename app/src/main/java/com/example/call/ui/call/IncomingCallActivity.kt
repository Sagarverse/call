package com.example.call.ui.call

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.call.databinding.ActivityIncomingCallBinding
import com.example.call.telecom.CallController
import com.example.call.util.ContactLookup
import com.example.call.util.GesturePreferences
import kotlin.math.abs
import android.telecom.TelecomManager
import com.example.call.R

class IncomingCallActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityIncomingCallBinding
    private lateinit var gestureDetector: GestureDetector
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var lastShakeTime: Long = 0

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
        if (GesturePreferences.isShakeToAcceptEnabled(this)) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        if (GesturePreferences.isFlipToSilenceEnabled(this)) {
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
                val diffY = e2.y - e1.y
                if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY < 0) onSwipeUp() else onSwipeDown()
                    return true
                }
                return false
            }
        })
    }

    private fun onSwipeUp() = answerCall()
    private fun onSwipeDown() = declineCall()

    private fun answerCall() {
        CallController.accept()
        finish()
    }

    private fun declineCall() {
        CallController.reject()
        finish()
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
        if (GesturePreferences.isPowerButtonMutesEnabled(this) && keyCode == KeyEvent.KEYCODE_POWER) {
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
        val now = System.currentTimeMillis()
        if ((now - lastShakeTime) > 1000) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val acceleration = (x * x + y * y + z * z) / (SensorManager.GRAVITY_EARTH * SensorManager.GRAVITY_EARTH)
            if (acceleration > 4) { // Adjust threshold as needed
                lastShakeTime = now
                answerCall()
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
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
