package com.example.call.ui.call

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.TelecomManager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import android.media.Ringtone
import android.media.RingtoneManager
import android.animation.ValueAnimator
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.call.R
import com.example.call.databinding.ActivityIncomingCallBinding
import com.example.call.telecom.CallController
import com.example.call.util.ContactLookup
import com.example.call.util.GesturePreferences
import com.example.call.util.AppSettings
import com.example.call.util.CallThemeManager
import com.example.call.util.ContactRingtoneStore
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
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null
    private var overlayAnimator: ValueAnimator? = null
    private var backgroundAnimator: ValueAnimator? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: android.content.Intent? = null
    private var isListening = false

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
        setupVibration()
        setupRingtone()
        applyCallTheme()
        setupVoiceCommands()

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

    private fun setupVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun setupRingtone() {
        val number = CallController.currentCall?.details?.handle?.schemeSpecificPart.orEmpty()
        val perContact = ContactRingtoneStore.getRingtoneUri(this, number)
        val fallback = AppSettings.getRingtoneUri(this)
        val uriString = perContact ?: fallback
        val uri = if (uriString.isNullOrBlank()) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } else {
            android.net.Uri.parse(uriString)
        }
        ringtone = RingtoneManager.getRingtone(this, uri)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        startRingingVibrationIfNeeded()
        startRingtoneIfNeeded()
        startBackgroundAnimation()
        startVoiceListeningIfAllowed()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopRingingVibration()
        stopRingtone()
        stopBackgroundAnimation()
        stopVoiceListening()
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
        stopRingingVibration()
        stopRingtone()
        CallController.accept()
        finish()
    }

    private fun declineCall() {
        stopRingingVibration()
        stopRingtone()
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

    private fun applyCallTheme() {
        val theme = AppSettings.getCallTheme(this)
        val overlayRes = CallThemeManager.getOverlayRes(theme)
        binding.callThemeOverlay.setBackgroundResource(overlayRes)
    }

    private fun startBackgroundAnimation() {
        if (overlayAnimator?.isRunning == true) return
        overlayAnimator = ValueAnimator.ofFloat(0.35f, 0.65f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                binding.callThemeOverlay.alpha = animator.animatedValue as Float
            }
        }
        backgroundAnimator = ValueAnimator.ofFloat(1.0f, 1.06f).apply {
            duration = 6000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                binding.callerBackground.scaleX = scale
                binding.callerBackground.scaleY = scale
            }
        }
        overlayAnimator?.start()
        backgroundAnimator?.start()
    }

    private fun stopBackgroundAnimation() {
        overlayAnimator?.cancel()
        backgroundAnimator?.cancel()
        overlayAnimator = null
        backgroundAnimator = null
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
        stopRingingVibration()
        stopRingtone()
        Toast.makeText(this, "Ringer silenced", Toast.LENGTH_SHORT).show()
    }

    private fun startRingingVibrationIfNeeded() {
        if (!AppSettings.isVibrateOnRingEnabled(this)) return
        val vib = vibrator ?: return
        val pattern = longArrayOf(0, 400, 600, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }
    }

    private fun stopRingingVibration() {
        vibrator?.cancel()
    }

    private fun startRingtoneIfNeeded() {
        ringtone?.let {
            if (!it.isPlaying) {
                it.play()
            }
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
    }

    private fun setupVoiceCommands() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }
                override fun onError(error: Int) {
                    isListening = false
                    restartVoiceListening()
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                        .joinToString(" ")
                        .lowercase()
                    handleVoiceCommand(matches)
                    restartVoiceListening()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                        .joinToString(" ")
                        .lowercase()
                    if (matches.isNotBlank()) {
                        handleVoiceCommand(matches)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    private fun startVoiceListeningIfAllowed() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) return
        if (speechRecognizer == null || speechIntent == null) return
        if (isListening) return
        isListening = true
        speechRecognizer?.startListening(speechIntent)
    }

    private fun restartVoiceListening() {
        window.decorView.postDelayed({
            startVoiceListeningIfAllowed()
        }, 800)
    }

    private fun stopVoiceListening() {
        isListening = false
        speechRecognizer?.stopListening()
    }

    private fun handleVoiceCommand(text: String) {
        when {
            text.contains("pick up") || text.contains("pickup") || text.contains("answer") || text.contains("accept") -> {
                answerCall()
            }
            text.contains("end") || text.contains("hang up") || text.contains("hangup") || text.contains("reject") || text.contains("decline") -> {
                declineCall()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
