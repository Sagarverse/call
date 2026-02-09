package com.example.call

import android.app.Application
import com.google.android.material.color.DynamicColors

class CallApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
