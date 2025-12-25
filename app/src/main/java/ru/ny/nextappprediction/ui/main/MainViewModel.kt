package ru.ny.nextappprediction.ui.main

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.ny.nextappprediction.NapApplication
import ru.ny.nextappprediction.data.collector.ActivityRecognitionHelper
import ru.ny.nextappprediction.data.export.CsvExporter
import ru.ny.nextappprediction.data.repository.AppLaunchRepository
import ru.ny.nextappprediction.domain.predictor.MarkovPredictor
import ru.ny.nextappprediction.domain.predictor.NaiveBayesPredictor
import ru.ny.nextappprediction.service.DataCollectionService
import ru.ny.nextappprediction.util.TimeUtils
import android.os.BatteryManager
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = (application as NapApplication).database
    private val dao = database.appLaunchDao()
    private val repository = AppLaunchRepository(dao)
    private val markovPredictor = MarkovPredictor(repository)
    private val naiveBayesPredictor = NaiveBayesPredictor(repository)
    private val activityHelper = ActivityRecognitionHelper(application)

    // SharedPreferences для сохранения выбранной модели
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Количество записей (Flow из Room)
    val eventCount: StateFlow<Int> = repository.getCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        loadSavedModel()
        loadData()
        checkServiceState()
    }

    companion object {
        private const val PREFS_NAME = "nap_preferences"
        private const val KEY_SELECTED_MODEL = "selected_model"
    }

    /**
     * Загружает сохранённую модель из SharedPreferences
     */
    private fun loadSavedModel() {
        val savedModelName = prefs.getString(KEY_SELECTED_MODEL, PredictorModel.MARKOV.name)
        val savedModel = try {
            PredictorModel.valueOf(savedModelName ?: PredictorModel.MARKOV.name)
        } catch (e: IllegalArgumentException) {
            PredictorModel.MARKOV
        }
        _uiState.update { it.copy(selectedModel = savedModel) }
    }

    private fun loadData() {
        viewModelScope.launch {
            markovPredictor.train()
            naiveBayesPredictor.train()
            updatePredictions()
        }
    }

    fun refreshPredictions() {
        viewModelScope.launch {
            markovPredictor.train()
            naiveBayesPredictor.train()
            updatePredictions()
        }
    }

    fun switchModel() {
        viewModelScope.launch {
            val newModel = if (_uiState.value.selectedModel == PredictorModel.MARKOV) {
                PredictorModel.NAIVE_BAYES
            } else {
                PredictorModel.MARKOV
            }
            // Сохраняем выбранную модель в SharedPreferences
            prefs.edit().putString(KEY_SELECTED_MODEL, newModel.name).apply()
            _uiState.update { it.copy(selectedModel = newModel) }
            updatePredictions()
        }
    }

    private suspend fun updatePredictions() {
        val predictions = when (_uiState.value.selectedModel) {
            PredictorModel.MARKOV -> {
                val lastLaunch = repository.getLastLaunch()
                markovPredictor.predictNext(lastLaunch?.packageName)
            }
            PredictorModel.NAIVE_BAYES -> {
                // Получаем текущий контекст
                val calendar = Calendar.getInstance()
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val timeSlot = TimeUtils.getTimeSlot(hour)

                val batteryManager = getApplication<Application>().getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val isCharging = batteryManager.isCharging

                val lastLaunch = repository.getLastLaunch()
                val previousApp = lastLaunch?.packageName
                val activityType = activityHelper.getCurrentActivity()

                // Дополнительные признаки
                val isWeekend = dayOfWeek == Calendar.SUNDAY || dayOfWeek == Calendar.SATURDAY
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)

                naiveBayesPredictor.predictNext(
                    hour = hour,
                    dayOfWeek = dayOfWeek,
                    timeSlot = timeSlot,
                    isCharging = isCharging,
                    batteryLevel = batteryLevel,
                    previousApp = previousApp,
                    activityType = activityType,
                    isWeekend = isWeekend,
                    dayOfMonth = dayOfMonth
                )
            }
        }

        val hasEnoughData = when (_uiState.value.selectedModel) {
            PredictorModel.MARKOV -> markovPredictor.hasEnoughData()
            PredictorModel.NAIVE_BAYES -> naiveBayesPredictor.hasEnoughData()
        }

        _uiState.update { it.copy(predictions = predictions, hasEnoughData = hasEnoughData) }
    }

    fun startService() {
        val intent = Intent(getApplication(), DataCollectionService::class.java)
        ContextCompat.startForegroundService(getApplication(), intent)
        _uiState.update { it.copy(isServiceRunning = true) }
    }

    fun stopService() {
        val intent = Intent(getApplication(), DataCollectionService::class.java)
        getApplication<Application>().stopService(intent)
        _uiState.update { it.copy(isServiceRunning = false) }
    }

    fun exportData() {
        viewModelScope.launch {
            try {
                val exporter = CsvExporter(getApplication(), repository)
                val file = exporter.export()
                if (file != null) {
                    _uiState.update { it.copy(lastExportPath = file.absolutePath, exportError = null) }
                } else {
                    _uiState.update { it.copy(exportError = "Нет данных для экспорта") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(exportError = "Ошибка экспорта: ${e.message}") }
            }
        }
    }

    /**
     * Проверяет реальное состояние сервиса через ActivityManager
     */
    private fun checkServiceState() {
        val isRunning = isServiceRunning(DataCollectionService::class.java)
        _uiState.update { it.copy(isServiceRunning = isRunning) }
    }

    /**
     * Проверяет, запущен ли указанный сервис
     */
    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE).any { service ->
            serviceClass.name == service.service.className
        }
    }
}

enum class PredictorModel {
    MARKOV,
    NAIVE_BAYES
}

data class MainUiState(
    val predictions: List<Pair<String, Float>> = emptyList(),
    val hasEnoughData: Boolean = false,
    val isServiceRunning: Boolean = false,
    val lastExportPath: String? = null,
    val exportError: String? = null,
    val selectedModel: PredictorModel = PredictorModel.MARKOV
)
