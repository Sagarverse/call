package com.example.call.util

import com.example.call.R

object CallThemeManager {
    const val THEME_DEFAULT = "default"
    const val THEME_OCEAN = "ocean"
    const val THEME_SUNSET = "sunset"
    const val THEME_NEON = "neon"

    fun getOverlayRes(theme: String): Int {
        return when (theme) {
            THEME_OCEAN -> R.drawable.bg_call_theme_ocean
            THEME_SUNSET -> R.drawable.bg_call_theme_sunset
            THEME_NEON -> R.drawable.bg_call_theme_neon
            else -> R.drawable.bg_call_theme_default
        }
    }
}
