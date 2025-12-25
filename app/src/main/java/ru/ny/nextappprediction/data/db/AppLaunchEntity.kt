package ru.ny.nextappprediction.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_launches",
    indices = [Index(value = ["timestamp"])]
)
data class AppLaunchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val hour: Int, // 0-23
    val dayOfWeek: Int, // 1-7
    val timeSlot: Int, // 0=ночь (0-6), 1=утро (6-12), 2=день (12-18), 3=вечер (18-24)
    val previousApp: String?,
    val isCharging: Boolean,
    val batteryLevel: Int, // 0-100
    val activityType: String? = null // STILL, WALKING, RUNNING, ON_BICYCLE, IN_VEHICLE, UNKNOWN
)
