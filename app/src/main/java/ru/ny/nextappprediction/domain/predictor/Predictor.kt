package ru.ny.nextappprediction.domain.predictor

/**
 * Интерфейс для предикторов следующего приложения.
 * Позволяет использовать разные алгоритмы предсказания через полиморфизм.
 */
interface Predictor {

    /**
     * Обучает модель на данных из репозитория
     */
    suspend fun train()

    /**
     * Проверяет, достаточно ли данных для предсказания
     * @return true если данных достаточно (обычно >= 50 записей)
     */
    fun hasEnoughData(): Boolean

    /**
     * Возвращает название модели для отображения в UI
     */
    fun getModelName(): String
}
