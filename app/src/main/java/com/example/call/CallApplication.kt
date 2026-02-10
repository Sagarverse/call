package com.example.call

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.call.ui.call.InCallBarController
import com.example.call.util.GesturePreferences
import com.google.android.material.color.DynamicColors

class CallApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        InCallBarController.register(this)
        
        // Apply the saved theme mode globally on app startup
        val savedMode = GesturePreferences.getThemeMode(this)
        AppCompatDelegate.setDefaultNightMode(savedMode)
    }
}
