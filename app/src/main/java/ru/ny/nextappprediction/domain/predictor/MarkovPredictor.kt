package ru.ny.nextappprediction.domain.predictor

import android.util.Log
import ru.ny.nextappprediction.data.repository.AppLaunchRepository

class MarkovPredictor(private val repository: AppLaunchRepository) : Predictor {

    companion object {
        private const val TAG = "MarkovPredictor"
    }

    override fun getModelName(): String = "Markov Chain"

    // Матрица переходов: previousApp -> (nextApp -> count)
    private var transitions: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()

    // Общий счётчик приложений для MFU fallback
    private var appCounts: MutableMap<String, Int> = mutableMapOf()

    // Минимальное количество переходов для использования Markov
    private val minTransitionsThreshold = 5

    /**
     * Обучает модель на последних 5000 записях из БД для производительности
     */
    override suspend fun train() {
        val startTime = System.currentTimeMillis()

        val launches = repository.getRecentForTraining(5000)

        transitions.clear()
        appCounts.clear()

        for (launch in launches) {
            // Считаем общую частоту приложений (для MFU)
            appCounts[launch.packageName] = (appCounts[launch.packageName] ?: 0) + 1

            // Считаем переходы
            launch.previousApp?.let { prev ->
                val nextCounts = transitions.getOrPut(prev) { mutableMapOf() }
                nextCounts[launch.packageName] = (nextCounts[launch.packageName] ?: 0) + 1
            }
        }

        val duration = System.currentTimeMillis() - startTime
        Log.d(TAG, "Training completed: ${launches.size} records, ${duration}ms, ${appCounts.size} unique apps")
    }

    /**
     * Предсказывает следующие приложения
     * @param currentApp текущее/последнее приложение (nullable)
     * @param topK количество предсказаний
     * @return список пар (packageName, probability)
     */
    fun predictNext(currentApp: String?, topK: Int = 3): List<Pair<String, Float>> {
        // Если есть currentApp и достаточно данных — используем Markov
        if (currentApp != null) {
            val nextCounts = transitions[currentApp]
            if (nextCounts != null && nextCounts.values.sum() >= minTransitionsThreshold) {
                val total = nextCounts.values.sum().toFloat()
                return nextCounts.entries
                    .sortedByDescending { it.value }
                    .take(topK)
                    .map { it.key to (it.value / total) }
            }
        }

        // Fallback на MFU (Most Frequently Used)
        return getMostFrequent(topK)
    }

    /**
     * Возвращает самые частые приложения (MFU fallback)
     */
    private fun getMostFrequent(topK: Int): List<Pair<String, Float>> {
        val total = appCounts.values.sum().toFloat()
        if (total == 0f) return emptyList()

        return appCounts.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { it.key to (it.value / total) }
    }

    /**
     * Проверяет, достаточно ли данных для предсказания
     */
    override fun hasEnoughData(): Boolean {
        return appCounts.values.sum() >= 50 // минимум 50 записей
    }
}
