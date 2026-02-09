package com.example.call.util

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import androidx.activity.result.ActivityResultLauncher

object RoleHelper {
    fun isDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            telecomManager?.defaultDialerPackage == context.packageName
        }
    }

    fun requestDialerRole(
        activity: Activity,
        launcher: ActivityResultLauncher<android.content.Intent>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(RoleManager::class.java)
            val intent = roleManager?.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            if (intent != null) {
                launcher.launch(intent)
            }
            return
        }

        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
        }
        launcher.launch(intent)
    }
}
