package ru.ny.nextappprediction.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLaunchDao {
    @Insert
    suspend fun insert(launch: AppLaunchEntity): Long

    @Query("SELECT * FROM app_launches ORDER BY timestamp DESC")
    fun getAll(): Flow<List<AppLaunchEntity>>

    @Query("SELECT * FROM app_launches ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<AppLaunchEntity>>

    @Query("SELECT COUNT(*) FROM app_launches")
    fun getCount(): Flow<Int>

    @Query("SELECT * FROM app_launches ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastLaunch(): AppLaunchEntity?

    @Query("SELECT * FROM app_launches ORDER BY timestamp ASC")
    suspend fun getAllForExport(): List<AppLaunchEntity>

    @Query("SELECT * FROM app_launches ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentForTraining(limit: Int = 5000): List<AppLaunchEntity>

    @Query("DELETE FROM app_launches")
    suspend fun deleteAll()

    /**
     * Удаляет записи старше указанного timestamp
     * @return количество удалённых записей
     */
    @Query("DELETE FROM app_launches WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int
}
