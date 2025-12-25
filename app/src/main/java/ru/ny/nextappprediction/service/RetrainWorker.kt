package ru.ny.nextappprediction.service

import android.content.Context
import androidx.work.*
import ru.ny.nextappprediction.NapApplication
import ru.ny.nextappprediction.data.repository.AppLaunchRepository
import ru.ny.nextappprediction.domain.predictor.MarkovPredictor
import ru.ny.nextappprediction.domain.predictor.NaiveBayesPredictor
import java.util.*
import java.util.concurrent.TimeUnit

class RetrainWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = (applicationContext as NapApplication).database
            val dao = database.appLaunchDao()
            val repository = AppLaunchRepository(dao)

            // Обучаем обе модели
            val markovPredictor = MarkovPredictor(repository)
            val naiveBayesPredictor = NaiveBayesPredictor(repository)

            markovPredictor.train()
            naiveBayesPredictor.train()

            // TODO: Можно добавить сериализацию моделей в файл
            // для персистентности между перезапусками (см. TODO #9)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "retrain_worker"

        /**
         * Планирует ежедневное переобучение ночью
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            val request = PeriodicWorkRequestBuilder<RetrainWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .setInitialDelay(calculateDelayUntilNight(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }

        private fun calculateDelayUntilNight(): Long {
            val now = Calendar.getInstance()
            val night = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 3)
                set(Calendar.MINUTE, 0)
                if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
            }
            return night.timeInMillis - now.timeInMillis
        }
    }
}
