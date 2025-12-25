package ru.ny.nextappprediction.domain.predictor

import android.util.Log
import ru.ny.nextappprediction.data.repository.AppLaunchRepository
import kotlin.math.ln

/**
 * Naive Bayes классификатор для предсказания следующего приложения
 * на основе контекстных признаков: час, день недели, слот времени,
 * зарядка, уровень батареи, предыдущее приложение, физическая активность
 */
class NaiveBayesPredictor(private val repository: AppLaunchRepository) : Predictor {

    companion object {
        private const val TAG = "NaiveBayesPredictor"
    }

    override fun getModelName(): String = "Naive Bayes"

    private val minDataThreshold = 50
    private val alpha = 1.0 // Laplace smoothing parameter

    // P(app)
    private var appCounts = mutableMapOf<String, Int>()
    private var totalRecords = 0

    // P(hour | app)
    private var hourGivenApp = mutableMapOf<String, MutableMap<Int, Int>>()

    // P(dayOfWeek | app)
    private var dayOfWeekGivenApp = mutableMapOf<String, MutableMap<Int, Int>>()

    // P(timeSlot | app)
    private var timeSlotGivenApp = mutableMapOf<String, MutableMap<Int, Int>>()

    // P(isCharging | app)
    private var isChargingGivenApp = mutableMapOf<String, MutableMap<Boolean, Int>>()

    // P(batteryLevel | app) - разбиваем на диапазоны: 0-25, 26-50, 51-75, 76-100
    private var batteryRangeGivenApp = mutableMapOf<String, MutableMap<Int, Int>>()

    // P(previousApp | app)
    private var previousAppGivenApp = mutableMapOf<String, MutableMap<String, Int>>()

    // P(activityType | app)
    private var activityTypeGivenApp = mutableMapOf<String, MutableMap<String, Int>>()

    // P(isWeekend | app) - выходные дни
    private var isWeekendGivenApp = mutableMapOf<String, MutableMap<Boolean, Int>>()

    // P(dayOfMonth | app) - день месяца (1-31)
    private var dayOfMonthGivenApp = mutableMapOf<String, MutableMap<Int, Int>>()

    /**
     * Обучает модель на последних 5000 записях для производительности
     */
    override suspend fun train() {
        val startTime = System.currentTimeMillis()

        val launches = repository.getRecentForTraining(5000)

        if (launches.size < minDataThreshold) {
            Log.d(TAG, "Not enough data: ${launches.size} < $minDataThreshold")
            return
        }

        // Очищаем старые данные
        appCounts.clear()
        hourGivenApp.clear()
        dayOfWeekGivenApp.clear()
        timeSlotGivenApp.clear()
        isChargingGivenApp.clear()
        batteryRangeGivenApp.clear()
        previousAppGivenApp.clear()
        activityTypeGivenApp.clear()
        isWeekendGivenApp.clear()
        dayOfMonthGivenApp.clear()

        totalRecords = launches.size

        // Подсчитываем частоты
        for (launch in launches) {
            val app = launch.packageName

            // P(app)
            appCounts[app] = appCounts.getOrDefault(app, 0) + 1

            // P(hour | app)
            hourGivenApp.getOrPut(app) { mutableMapOf() }
                .merge(launch.hour, 1, Int::plus)

            // P(dayOfWeek | app)
            dayOfWeekGivenApp.getOrPut(app) { mutableMapOf() }
                .merge(launch.dayOfWeek, 1, Int::plus)

            // P(timeSlot | app)
            timeSlotGivenApp.getOrPut(app) { mutableMapOf() }
                .merge(launch.timeSlot, 1, Int::plus)

            // P(isCharging | app)
            isChargingGivenApp.getOrPut(app) { mutableMapOf() }
                .merge(launch.isCharging, 1, Int::plus)

            // P(batteryRange | app)
            val batteryRange = getBatteryRange(launch.batteryLevel)
            batteryRangeGivenApp.getOrPut(app) { mutableMapOf() }
                .merge(batteryRange, 1, Int::plus)

            // P(previousApp | app)
            launch.previousApp?.let { prevApp ->
                previousAppGivenApp.getOrPut(app) { mutableMapOf() }
                    .merge(prevApp, 1, Int::plus)
            }

            // P(activityType | app)
            launch.activityType?.let { activity ->
                activityTypeGivenApp.getOrPut(app) { mutableMapOf() }
                    .merge(activity, 1, Int::plus)
            }

            // P(isWeekend | app) - вычисляем из dayOfWeek
            val isWeekend = isWeekend(launch.dayOfWeek)
            isWeekendGivenApp.getOrPut(app) { mutableMapOf() }
                .merge(isWeekend, 1, Int::plus)

            // P(dayOfMonth | app) - извлекаем из timestamp
            val dayOfMonth = getDayOfMonth(launch.timestamp)
            dayOfMonthGivenApp.getOrPut(app) { mutableMapOf() }
                .merge(dayOfMonth, 1, Int::plus)
        }

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Training completed: ${launches.size} records, ${duration}ms, ${appCounts.size} unique apps, 10 features")
    }

    /**
     * Предсказывает топ-K наиболее вероятных приложений
     */
    fun predictNext(
        hour: Int,
        dayOfWeek: Int,
        timeSlot: Int,
        isCharging: Boolean,
        batteryLevel: Int,
        previousApp: String?,
        activityType: String?,
        isWeekend: Boolean,
        dayOfMonth: Int,
        topK: Int = 3
    ): List<Pair<String, Float>> {
        if (!hasEnoughData()) {
            return getMostFrequent(topK)
        }

        val batteryRange = getBatteryRange(batteryLevel)
        val logProbabilities = mutableMapOf<String, Double>()

        // Вычисляем log P(app | features) для каждого приложения
        for ((app, count) in appCounts) {
            var logProb = 0.0

            // P(app)
            logProb += ln(count.toDouble() / totalRecords)

            // P(hour | app)
            logProb += getLaplaceLogProb(
                hourGivenApp[app]?.get(hour),
                appCounts[app],
                24 // всего часов
            )

            // P(dayOfWeek | app)
            logProb += getLaplaceLogProb(
                dayOfWeekGivenApp[app]?.get(dayOfWeek),
                appCounts[app],
                7 // всего дней недели
            )

            // P(timeSlot | app)
            logProb += getLaplaceLogProb(
                timeSlotGivenApp[app]?.get(timeSlot),
                appCounts[app],
                4 // всего слотов
            )

            // P(isCharging | app)
            logProb += getLaplaceLogProb(
                isChargingGivenApp[app]?.get(isCharging),
                appCounts[app],
                2 // true/false
            )

            // P(batteryRange | app)
            logProb += getLaplaceLogProb(
                batteryRangeGivenApp[app]?.get(batteryRange),
                appCounts[app],
                4 // 4 диапазона
            )

            // P(previousApp | app)
            if (previousApp != null) {
                logProb += getLaplaceLogProb(
                    previousAppGivenApp[app]?.get(previousApp),
                    appCounts[app],
                    appCounts.size // всего уникальных приложений
                )
            }

            // P(activityType | app)
            if (activityType != null) {
                logProb += getLaplaceLogProb(
                    activityTypeGivenApp[app]?.get(activityType),
                    appCounts[app],
                    8 // всего типов активности
                )
            }

            // P(isWeekend | app)
            logProb += getLaplaceLogProb(
                isWeekendGivenApp[app]?.get(isWeekend),
                appCounts[app],
                2 // true/false
            )

            // P(dayOfMonth | app)
            logProb += getLaplaceLogProb(
                dayOfMonthGivenApp[app]?.get(dayOfMonth),
                appCounts[app],
                31 // 1-31 дней
            )

            logProbabilities[app] = logProb
        }

        // Сортируем по убыванию вероятности
        val sorted = logProbabilities.entries
            .sortedByDescending { it.value }
            .take(topK)

        // Нормализуем вероятности (softmax для log-вероятностей)
        val maxLogProb = sorted.firstOrNull()?.value ?: 0.0
        val expProbs = sorted.map { it.key to kotlin.math.exp(it.value - maxLogProb) }
        val sumExpProbs = expProbs.sumOf { it.second }

        return expProbs.map { (app, expProb) ->
            app to (expProb / sumExpProbs).toFloat()
        }
    }

    /**
     * Вычисляет log-вероятность с Laplace сглаживанием
     */
    private fun getLaplaceLogProb(count: Int?, totalForApp: Int?, numCategories: Int): Double {
        val c = count ?: 0
        val n = totalForApp ?: 1
        return ln((c + alpha) / (n + alpha * numCategories))
    }

    /**
     * Возвращает диапазон батареи: 0 (0-25%), 1 (26-50%), 2 (51-75%), 3 (76-100%)
     */
    private fun getBatteryRange(batteryLevel: Int): Int {
        return when {
            batteryLevel <= 25 -> 0
            batteryLevel <= 50 -> 1
            batteryLevel <= 75 -> 2
            else -> 3
        }
    }

    /**
     * Проверяет, достаточно ли данных для предсказания
     */
    override fun hasEnoughData(): Boolean {
        return totalRecords >= minDataThreshold
    }

    /**
     * Возвращает наиболее часто используемые приложения (fallback)
     */
    private fun getMostFrequent(topK: Int): List<Pair<String, Float>> {
        val sorted = appCounts.entries
            .sortedByDescending { it.value }
            .take(topK)

        val total = sorted.sumOf { it.value }.toFloat()
        if (total == 0f) return emptyList()

        return sorted.map { it.key to (it.value / total) }
    }

    /**
     * Определяет, является ли день выходным
     * dayOfWeek: 1=Воскресенье, 7=Суббота (Calendar.DAY_OF_WEEK)
     */
    private fun isWeekend(dayOfWeek: Int): Boolean {
        return dayOfWeek == 1 || dayOfWeek == 7 // Воскресенье или Суббота
    }

    /**
     * Извлекает день месяца из timestamp (1-31)
     */
    private fun getDayOfMonth(timestamp: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        return calendar.get(java.util.Calendar.DAY_OF_MONTH)
    }
}
