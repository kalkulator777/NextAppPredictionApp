package ru.ny.nextappprediction.ui.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process

class PermissionChecker(private val context: Context) {

    data class PermissionState(
        val usageStats: Boolean,
        val notifications: Boolean,
        val batteryOptimization: Boolean,
        val activityRecognition: Boolean
    )

    fun checkAll(): PermissionState {
        return PermissionState(
            usageStats = hasUsageStatsPermission(),
            notifications = hasNotificationPermission(),
            batteryOptimization = isIgnoringBatteryOptimizations(),
            activityRecognition = hasActivityRecognitionPermission()
        )
    }

    fun allGranted(): Boolean {
        val state = checkAll()
        return state.usageStats && state.notifications && state.batteryOptimization && state.activityRecognition
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationPermission(): Boolean {
        // Для Android 13+ (API 33) проверяем POST_NOTIFICATIONS
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // Для API < 33 разрешение не требуется
            true
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasActivityRecognitionPermission(): Boolean {
        // Для Android 10+ (API 29) проверяем ACTIVITY_RECOGNITION
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            // Для API < 29 разрешение не требуется
            true
        }
    }
}
