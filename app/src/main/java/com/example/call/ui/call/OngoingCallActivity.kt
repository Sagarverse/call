package com.example.call.ui.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.call.MainActivity
import com.example.call.R
import com.example.call.databinding.ActivityOngoingCallBinding
import com.example.call.databinding.ViewCallControlBinding
import com.example.call.telecom.CallController
import com.example.call.util.ContactLookup
import com.example.call.util.AppSettings
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

class OngoingCallActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var binding: ActivityOngoingCallBinding
    private var isOnHold = false
    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null
    private var dtmfDialog: AlertDialog? = null
    
    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 20.0f
    private var isShakeEnabled = true

    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateCallTimer()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOngoingCallBinding.inflate(layoutInflater)
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

        setupControls()
        observeAudioState()
        observeCallState()
        setupSensors()
        
        binding.endCall.setOnClickListener {
            CallController.disconnect()
            finish()
        }
    }

    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        
        if (AppSettings.isShakeToAcceptEnabled(this)) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                wakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "CallApp:ProximityLock")
            }
        }
    }

    private fun observeCallState() {
        lifecycleScope.launch {
            CallController.currentCallFlow.collectLatest { call ->
                if (call == null) {
                    finish()
                }
            }
        }
    }

    private fun setupControls() {
        // Mute
        binding.mute.controlLabel.text = getString(R.string.mute)
        binding.mute.controlIcon.setImageDrawable(AppCompatResources.getDrawable(this, android.R.drawable.ic_lock_silent_mode))
        binding.mute.controlIcon.setOnClickListener {
            val currentState = CallController.audioState.value
            val isCurrentlyMuted = currentState?.isMuted ?: false
            CallController.setMuted(!isCurrentlyMuted)
        }

        // Keypad
        binding.keypad.controlLabel.text = getString(R.string.keypad)
        binding.keypad.controlIcon.setImageDrawable(AppCompatResources.getDrawable(this, android.R.drawable.ic_dialog_dialer))
        binding.keypad.controlIcon.setOnClickListener {
            showKeypadDialog()
        }

        // Speaker
        binding.speaker.controlLabel.text = getString(R.string.speaker)
        binding.speaker.controlIcon.setImageDrawable(AppCompatResources.getDrawable(this, android.R.drawable.stat_sys_speakerphone))
        binding.speaker.controlIcon.setOnClickListener {
            val currentState = CallController.audioState.value
            val isSpeakerOn = currentState?.route == CallAudioState.ROUTE_SPEAKER
            CallController.toggleSpeaker(!isSpeakerOn)
        }

        // Add Call
        binding.addCall.controlLabel.text = getString(R.string.add_call)
        binding.addCall.controlIcon.setImageDrawable(AppCompatResources.getDrawable(this, android.R.drawable.ic_input_add))
        binding.addCall.controlIcon.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        }

        // Hold
        binding.hold.controlLabel.text = getString(R.string.hold)
        binding.hold.controlIcon.setImageDrawable(AppCompatResources.getDrawable(this, android.R.drawable.ic_media_pause))
        binding.hold.controlIcon.setOnClickListener {
            isOnHold = !isOnHold
            CallController.currentCall?.let {
                if (isOnHold) it.hold() else it.unhold()
            }
            updateHoldStateUI()
        }

        // Record
        binding.record.controlLabel.text = getString(R.string.record)
        binding.record.controlIcon.setImageDrawable(AppCompatResources.getDrawable(this, android.R.drawable.presence_video_online))
        binding.record.controlIcon.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
            return
        }

        try {
            val file = File(externalCacheDir, "call_${System.currentTimeMillis()}.amr")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            updateRecordStateUI()
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Recording failed: " + e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            updateRecordStateUI()
            Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeAudioState() {
        lifecycleScope.launch {
            CallController.audioState.collectLatest { state ->
                state?.let {
                    updateMuteUI(it.isMuted)
                    updateSpeakerUI(it.route == CallAudioState.ROUTE_SPEAKER)
                }
            }
        }
    }

    private fun updateMuteUI(isMuted: Boolean) {
        val bindingMute = ViewCallControlBinding.bind(binding.mute.root)
        toggleControlVisuals(bindingMute, isMuted)
    }

    private fun updateSpeakerUI(isOn: Boolean) {
        val bindingSpeaker = ViewCallControlBinding.bind(binding.speaker.root)
        toggleControlVisuals(bindingSpeaker, isOn)
    }

    private fun updateHoldStateUI() {
        val bindingHold = ViewCallControlBinding.bind(binding.hold.root)
        toggleControlVisuals(bindingHold, isOnHold)
        bindingHold.controlLabel.text = if (isOnHold) getString(R.string.unhold) else getString(R.string.hold)
    }

    private fun updateRecordStateUI() {
        val bindingRecord = ViewCallControlBinding.bind(binding.record.root)
        val activeColor = ContextCompat.getColor(this, R.color.call_red)
        val inactiveColor = ContextCompat.getColor(this, R.color.glass_white)
        
        bindingRecord.controlIcon.backgroundTintList = ColorStateList.valueOf(if (isRecording) activeColor else inactiveColor)
        ImageViewCompat.setImageTintList(bindingRecord.controlIcon, ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)))
    }

    private fun toggleControlVisuals(controlBinding: ViewCallControlBinding, isActive: Boolean) {
        val activeColor = ContextCompat.getColor(this, R.color.white)
        val inactiveColor = ContextCompat.getColor(this, R.color.glass_white)
        val activeIconTint = ContextCompat.getColor(this, R.color.black)
        val inactiveIconTint = ContextCompat.getColor(this, R.color.white)

        if (isActive) {
            controlBinding.controlIcon.backgroundTintList = ColorStateList.valueOf(activeColor)
            ImageViewCompat.setImageTintList(controlBinding.controlIcon, ColorStateList.valueOf(activeIconTint))
        } else {
            controlBinding.controlIcon.backgroundTintList = ColorStateList.valueOf(inactiveColor)
            ImageViewCompat.setImageTintList(controlBinding.controlIcon, ColorStateList.valueOf(inactiveIconTint))
        }
    }

    private fun showKeypadDialog() {
        if (dtmfDialog?.isShowing == true) return
        val call = CallController.currentCall
        if (call == null) {
            Toast.makeText(this, getString(R.string.no_active_call), Toast.LENGTH_SHORT).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_dtmf_keypad, null)
        setupDtmfButtons(view)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.setOnDismissListener { dtmfDialog = null }
        dtmfDialog = dialog
        dialog.show()
    }

    private fun setupDtmfButtons(view: android.view.View) {
        val buttons = listOf(
            R.id.dtmf1 to '1',
            R.id.dtmf2 to '2',
            R.id.dtmf3 to '3',
            R.id.dtmf4 to '4',
            R.id.dtmf5 to '5',
            R.id.dtmf6 to '6',
            R.id.dtmf7 to '7',
            R.id.dtmf8 to '8',
            R.id.dtmf9 to '9',
            R.id.dtmfStar to '*',
            R.id.dtmf0 to '0',
            R.id.dtmfHash to '#'
        )

        buttons.forEach { (id, tone) ->
            view.findViewById<com.google.android.material.button.MaterialButton>(id)?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> CallController.playDtmf(tone)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> CallController.stopDtmf()
                }
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        proximitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onStart() {
        super.onStart()
        bindCallerInfo()
        handler.post(timerRunnable)
    }

    override fun onStop() {
        handler.removeCallbacks(timerRunnable)
        stopRecording()
        super.onStop()
    }

    private fun bindCallerInfo() {
        val number = CallController.currentCall
            ?.details
            ?.handle
            ?.schemeSpecificPart
            .orEmpty()
        val lookup = ContactLookup.lookup(this, number)
        binding.callerName.text = lookup.displayName ?: getString(R.string.unknown_caller)
        binding.callerNumber.text = number

        val photo = lookup.photo
        if (photo != null) {
            binding.callerBackground.setImageBitmap(photo)
        }
    }

    private fun updateCallTimer() {
        val call = CallController.currentCall ?: return
        val connectTime = call.details.connectTimeMillis
        if (connectTime > 0) {
            val elapsed = System.currentTimeMillis() - connectTime
            binding.callStatus.text = formatElapsed(elapsed)
        } else {
            binding.callStatus.text = getString(R.string.call_status_connecting)
        }
    }

    private fun formatElapsed(millis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                if (event.values[0] < (proximitySensor?.maximumRange ?: 1f)) {
                    if (wakeLock?.isHeld == false) wakeLock?.acquire()
                } else {
                    if (wakeLock?.isHeld == true) wakeLock?.release()
                }
            }
            Sensor.TYPE_ACCELEROMETER -> handleShakeToEnd(event)
        }
    }

    private fun handleShakeToEnd(event: SensorEvent) {
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
                CallController.disconnect()
                finish()
                Handler(Looper.getMainLooper()).postDelayed({ isShakeEnabled = true }, 2000)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
