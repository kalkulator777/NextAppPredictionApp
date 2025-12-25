package ru.ny.nextappprediction.data.collector

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ActivityRecognitionHelper(private val context: Context) {

    companion object {
        private const val ACTION_ACTIVITY_RECOGNITION = "ru.ny.nextappprediction.ACTIVITY_RECOGNITION"
        private const val UPDATE_INTERVAL_MS = 60_000L // 1 minute
    }

    @Volatile
    private var lastActivity: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (ActivityRecognitionResult.hasResult(intent)) {
                val result = ActivityRecognitionResult.extractResult(intent!!)
                val activity = result?.mostProbableActivity
                activity?.let {
                    lastActivity = getActivityString(it.type)
                }
            }
        }
    }

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(ACTION_ACTIVITY_RECOGNITION).apply {
            setPackage(context.packageName) // Make intent explicit for Android 14+
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No permission required before API 29
        }
    }

    suspend fun startRecognition() = suspendCancellableCoroutine<Boolean> { continuation ->
        if (!hasPermission()) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        // Register receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                receiver,
                IntentFilter(ACTION_ACTIVITY_RECOGNITION),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_ACTIVITY_RECOGNITION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // Request activity updates
        // Permission checked above
        @SuppressLint("MissingPermission")
        val task = ActivityRecognition.getClient(context)
            .requestActivityUpdates(UPDATE_INTERVAL_MS, pendingIntent)

        task.addOnSuccessListener {
                continuation.resume(true)
            }
            .addOnFailureListener {
                continuation.resume(false)
            }
    }

    @SuppressLint("MissingPermission")
    fun stopRecognition() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        ActivityRecognition.getClient(context).removeActivityUpdates(pendingIntent)
    }

    fun getCurrentActivity(): String? = lastActivity

    private fun getActivityString(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.TILTING -> "TILTING"
            else -> "UNKNOWN"
        }
    }
}
