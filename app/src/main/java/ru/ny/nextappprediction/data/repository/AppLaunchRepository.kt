package ru.ny.nextappprediction.data.repository

import kotlinx.coroutines.flow.Flow
import ru.ny.nextappprediction.data.db.AppLaunchDao
import ru.ny.nextappprediction.data.db.AppLaunchEntity

/**
 * Repository для работы с данными о запусках приложений.
 * Инкапсулирует доступ к DAO и предоставляет высокоуровневый API.
 */
class AppLaunchRepository(private val dao: AppLaunchDao) {

    /**
     * Вставляет новую запись о запуске приложения
     */
    suspend fun insert(launch: AppLaunchEntity): Long {
        return dao.insert(launch)
    }

    /**
     * Возвращает все записи (Flow для реактивного UI)
     */
    fun getAll(): Flow<List<AppLaunchEntity>> {
        return dao.getAll()
    }

    /**
     * Возвращает количество записей (Flow для реактивного UI)
     */
    fun getCount(): Flow<Int> {
        return dao.getCount()
    }

    /**
     * Возвращает последнюю запись
     */
    suspend fun getLastLaunch(): AppLaunchEntity? {
        return dao.getLastLaunch()
    }

    /**
     * Возвращает последние N записей для обучения моделей
     * @param limit максимальное количество записей (по умолчанию 5000)
     */
    suspend fun getRecentForTraining(limit: Int = 5000): List<AppLaunchEntity> {
        return dao.getRecentForTraining(limit)
    }

    /**
     * Возвращает все записи для экспорта (отсортированы по возрастанию времени)
     */
    suspend fun getAllForExport(): List<AppLaunchEntity> {
        return dao.getAllForExport()
    }

    /**
     * Удаляет записи старше указанного времени
     * @param cutoffTimestamp записи с timestamp меньше этого значения будут удалены
     * @return количество удалённых записей
     */
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int {
        return dao.deleteOlderThan(cutoffTimestamp)
    }

    /**
     * Удаляет все записи
     */
    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
