package ru.ny.nextappprediction.data.collector

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.BatteryManager
import android.os.Process
import ru.ny.nextappprediction.data.db.AppLaunchDao
import ru.ny.nextappprediction.data.db.AppLaunchEntity
import ru.ny.nextappprediction.util.TimeUtils
import java.util.Calendar

class UsageDataCollector(
    private val context: Context,
    private val dao: AppLaunchDao,
    private val activityHelper: ActivityRecognitionHelper
) {
    private val systemPackageKeywords = listOf(
        "launcher",
        "systemui",
        "settings",
        "keyboard",
        "inputmethod",
        "android",
        "google.android.packageinstaller"
    )

    /**
     * Проверяет, есть ли разрешение на UsageStats
     */
    fun hasPermission(): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Собирает данные об использовании приложений и сохраняет в БД
     */
    suspend fun collectAndSave() {
        if (!hasPermission()) {
            return
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Получаем события за последние 5 минут
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (5 * 60 * 1000) // 5 минут назад

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        // Получаем timestamp последней записи в БД для предотвращения дубликатов
        val lastLaunch = dao.getLastLaunch()
        val lastTimestamp = lastLaunch?.timestamp ?: 0L

        // Сохраняем данные событий в список пар (packageName, timestamp)
        val newEvents = mutableListOf<Pair<String, Long>>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            // Фильтруем только запуски приложений (ACTIVITY_RESUMED)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                // Фильтруем системные приложения
                if (isSystemPackage(event.packageName)) {
                    continue
                }

                // Фильтруем своё приложение
                if (event.packageName == context.packageName) {
                    continue
                }

                // Проверяем, что событие новое
                if (event.timeStamp > lastTimestamp) {
                    // Сохраняем packageName и timestamp
                    newEvents.add(event.packageName to event.timeStamp)
                }
            }
        }

        // Сохраняем новые события
        var previousApp = lastLaunch?.packageName

        for ((packageName, timestamp) in newEvents) {
            val entity = createAppLaunchEntity(
                packageName = packageName,
                timestamp = timestamp,
                previousApp = previousApp
            )

            dao.insert(entity)
            previousApp = packageName
        }
    }

    /**
     * Создаёт AppLaunchEntity с заполненными полями
     */
    private fun createAppLaunchEntity(
        packageName: String,
        timestamp: Long,
        previousApp: String?
    ): AppLaunchEntity {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 7=Saturday
        val timeSlot = TimeUtils.getTimeSlot(hour)

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging

        // Get current activity type
        val activityType = activityHelper.getCurrentActivity()

        return AppLaunchEntity(
            packageName = packageName,
            timestamp = timestamp,
            hour = hour,
            dayOfWeek = dayOfWeek,
            timeSlot = timeSlot,
            previousApp = previousApp,
            isCharging = isCharging,
            batteryLevel = batteryLevel,
            activityType = activityType
        )
    }

    /**
     * Проверяет, является ли пакет системным
     */
    private fun isSystemPackage(packageName: String): Boolean {
        val lowerCasePackage = packageName.lowercase()
        return systemPackageKeywords.any { keyword ->
            lowerCasePackage.contains(keyword)
        }
    }
}
