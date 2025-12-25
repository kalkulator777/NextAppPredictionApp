package ru.ny.nextappprediction.util

import android.content.Context

/**
 * Утилиты для работы с приложениями
 */
object AppUtils {

    // Кэш имён приложений для оптимизации (избегаем повторных IPC вызовов к PackageManager)
    private val appNameCache = mutableMapOf<String, String>()

    /**
     * Получает имя приложения по packageName с кэшированием
     * @param context контекст для доступа к PackageManager
     * @param packageName имя пакета приложения
     * @return человекочитаемое имя приложения или последняя часть packageName как fallback
     */
    fun getAppName(context: Context, packageName: String): String {
        // Проверяем кэш
        appNameCache[packageName]?.let { return it }

        // Если нет в кэше - получаем из PackageManager
        val appName = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            // Fallback: извлекаем последнюю часть packageName (e.g., "com.example.app" -> "app")
            packageName.substringAfterLast('.')
        }

        // Сохраняем в кэш
        appNameCache[packageName] = appName
        return appName
    }

    /**
     * Очищает кэш имён приложений
     * Полезно при переустановке приложений
     */
    fun clearCache() {
        appNameCache.clear()
    }
}
