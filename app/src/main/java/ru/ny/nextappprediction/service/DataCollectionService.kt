package ru.ny.nextappprediction.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import ru.ny.nextappprediction.MainActivity
import ru.ny.nextappprediction.NapApplication
import ru.ny.nextappprediction.data.collector.ActivityRecognitionHelper
import ru.ny.nextappprediction.data.collector.UsageDataCollector
import ru.ny.nextappprediction.data.repository.AppLaunchRepository
import ru.ny.nextappprediction.domain.predictor.MarkovPredictor
import ru.ny.nextappprediction.util.AppUtils

class DataCollectionService : Service() {

    companion object {
        private const val TAG = "DataCollectionService"
        const val CHANNEL_ID = "nap_collection_channel"
        const val NOTIFICATION_ID = 1
        const val COLLECTION_INTERVAL_MS = 60_000L // 1 минута
        const val RETRAIN_INTERVAL_MS = 300_000L // 5 минут
        const val CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 часа
        const val MAX_DATA_AGE_MS = 30L * 24 * 60 * 60 * 1000L // 30 дней
    }

    private lateinit var collector: UsageDataCollector
    private lateinit var predictor: MarkovPredictor
    private lateinit var repository: AppLaunchRepository
    private lateinit var activityHelper: ActivityRecognitionHelper
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var collectionJob: Job? = null
    private var lastRetrainTime = 0L
    private var lastCleanupTime = 0L

    override fun onCreate() {
        super.onCreate()

        val database = (application as NapApplication).database
        val dao = database.appLaunchDao()
        repository = AppLaunchRepository(dao)

        activityHelper = ActivityRecognitionHelper(this)
        collector = UsageDataCollector(this, dao, activityHelper)
        predictor = MarkovPredictor(repository)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Запускаем foreground service с notification
        val notification = createNotification("Инициализация...")
        startForeground(NOTIFICATION_ID, notification)

        // Запускаем activity recognition
        scope.launch {
            activityHelper.startRecognition()
        }

        // Запускаем периодический сбор данных
        collectionJob = scope.launch {
            while (isActive) {
                try {
                    collectAndPredict()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(COLLECTION_INTERVAL_MS)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        activityHelper.stopRecognition()
        collectionJob?.cancel()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Сбор данных"
            val descriptionText = "Фоновый сбор статистики использования приложений"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(prediction: String): Notification {
        // PendingIntent для открытия MainActivity при клике
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("🎯 NextAppPrediction")
            .setContentText("Предсказание: $prediction")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
    }

    private fun updateNotification(prediction: String) {
        val notification = createNotification(prediction)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun collectAndPredict() {
        // Проверяем разрешение перед сбором данных
        if (!collector.hasPermission()) {
            updateNotification("Нет разрешения на статистику")
            return
        }

        // Собираем новые данные
        collector.collectAndSave()

        val currentTime = System.currentTimeMillis()

        // Переобучаем предиктор только раз в 5 минут
        if (currentTime - lastRetrainTime >= RETRAIN_INTERVAL_MS) {
            predictor.train()
            lastRetrainTime = currentTime
        }

        // Очищаем старые данные раз в 24 часа
        if (currentTime - lastCleanupTime >= CLEANUP_INTERVAL_MS) {
            val cutoffTime = currentTime - MAX_DATA_AGE_MS
            val deletedCount = repository.deleteOlderThan(cutoffTime)
            if (deletedCount > 0) {
                Log.d(TAG, "Cleanup: deleted $deletedCount records older than 30 days")
            }
            lastCleanupTime = currentTime
        }

        // Получаем предсказание
        val lastLaunch = repository.getLastLaunch()
        val predictions = predictor.predictNext(lastLaunch?.packageName, topK = 1)

        // Обновляем notification
        val predictionText = if (predictions.isNotEmpty()) {
            val (packageName, probability) = predictions.first()
            val appName = AppUtils.getAppName(this, packageName)
            val percent = (probability * 100).toInt()
            "$appName ($percent%)"
        } else {
            "Сбор данных..."
        }

        updateNotification(predictionText)
    }
}
