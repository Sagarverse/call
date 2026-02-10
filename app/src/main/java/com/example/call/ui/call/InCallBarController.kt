package com.example.call.ui.call

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.telecom.Call
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.call.R
import com.example.call.telecom.CallController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

object InCallBarController {
    private const val BAR_TAG = "in_call_bar_view"

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                attach(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                detach(activity)
            }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun attach(activity: Activity) {
        if (activity is OngoingCallActivity || activity is IncomingCallActivity) return
        val root = activity.window.decorView as? ViewGroup ?: return
        var bar = root.findViewWithTag<View>(BAR_TAG)
        if (bar == null) {
            bar = LayoutInflater.from(activity).inflate(R.layout.view_in_call_bar, root, false)
            bar.tag = BAR_TAG
            root.addView(bar)
        }
        bindBar(activity, bar)
    }

    private fun detach(activity: Activity) {
        val root = activity.window.decorView as? ViewGroup ?: return
        val bar = root.findViewWithTag<View>(BAR_TAG) ?: return
        stopTimer(bar)
        root.removeView(bar)
    }

    private fun bindBar(activity: Activity, bar: View) {
        val label = bar.findViewById<TextView>(R.id.inCallBarLabel)
        val duration = bar.findViewById<TextView>(R.id.inCallBarDuration)

        bar.setOnClickListener {
            activity.startActivity(
                android.content.Intent(activity, OngoingCallActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }

        val owner = activity as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                CallController.calls.collect { calls ->
                    val call = CallController.getPrimaryCall()
                    if (call == null || call.state == Call.STATE_DISCONNECTED) {
                        bar.visibility = View.GONE
                        stopTimer(bar)
                        return@collect
                    }

                    if (!shouldShowForState(call.state)) {
                        bar.visibility = View.GONE
                        stopTimer(bar)
                        return@collect
                    }

                    bar.visibility = View.VISIBLE
                    label.text = if (calls.size > 1) {
                        activity.getString(R.string.in_call) + " (" + calls.size + ")"
                    } else {
                        resolveDisplayName(activity, call)
                    }
                    val startTime = resolveCallStartTime(call)
                    startTimer(activity, bar, duration, startTime)
                }
            }
        }
    }

    private fun shouldShowForState(state: Int): Boolean {
        return state == Call.STATE_ACTIVE ||
            state == Call.STATE_HOLDING ||
            state == Call.STATE_DIALING ||
            state == Call.STATE_CONNECTING
    }

    private fun startTimer(
        activity: Activity,
        bar: View,
        durationView: TextView,
        startTime: Long
    ) {
        val owner = activity as? LifecycleOwner ?: return
        if (startTime <= 0L) {
            durationView.text = activity.getString(R.string.call_timer_default)
            stopTimer(bar)
            return
        }

        val existing = bar.getTag(R.id.in_call_bar_job) as? Job
        if (existing?.isActive == true) return

        val job = owner.lifecycleScope.launch {
            while (isActive) {
                val elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(0)
                durationView.text = formatElapsed(elapsedSeconds)
                delay(1000)
            }
        }
        bar.setTag(R.id.in_call_bar_job, job)
    }

    private fun stopTimer(bar: View) {
        val job = bar.getTag(R.id.in_call_bar_job) as? Job
        job?.cancel()
        bar.setTag(R.id.in_call_bar_job, null)
    }

    private fun resolveDisplayName(activity: Activity, call: Call): String {
        val details = call.details
        val displayName = details?.callerDisplayName?.toString()?.trim().orEmpty()
        if (displayName.isNotEmpty()) return displayName
        val number = details?.handle?.schemeSpecificPart?.trim().orEmpty()
        return if (number.isNotEmpty()) number else activity.getString(R.string.ongoing_call)
    }

    private fun resolveCallStartTime(call: Call): Long {
        val details = call.details ?: return 0L
        return if (details.connectTimeMillis > 0L) details.connectTimeMillis else details.creationTimeMillis
    }

    private fun formatElapsed(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, remainingSeconds)
        }
    }
}
