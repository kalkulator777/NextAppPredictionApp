package ru.ny.nextappprediction.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.ny.nextappprediction.util.AppUtils

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val eventCount by viewModel.eventCount.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Заголовок
        Text(
            text = "📊 Статистика",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Карточка статистики
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Собрано событий: $eventCount")
            }
        }

        // Заголовок предсказаний
        Text(
            text = "🎯 Предсказание",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Переключатель моделей
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = "Модель: ${if (uiState.selectedModel == PredictorModel.MARKOV) "Markov Chain" else "Naive Bayes"}",
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = { viewModel.switchModel() }) {
                    Text("Переключить")
                }
            }
        }

        // Карточка предсказаний
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "Предсказания",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { viewModel.refreshPredictions() }) {
                        Text("Обновить")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!uiState.hasEnoughData) {
                    Text("Недостаточно данных. Нужно минимум 50 записей.")
                } else if (uiState.predictions.isEmpty()) {
                    Text("Нет предсказаний")
                } else {
                    uiState.predictions.forEachIndexed { index, (packageName, probability) ->
                        val appName = AppUtils.getAppName(context, packageName)
                        val percent = (probability * 100).toInt()
                        Text("${index + 1}. $appName ($percent%)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Кнопки управления
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (uiState.isServiceRunning) viewModel.stopService()
                    else viewModel.startService()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (uiState.isServiceRunning) "Стоп" else "Старт")
            }

            Button(
                onClick = { viewModel.exportData() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Экспорт")
            }
        }

        // Показываем путь к экспортированному файлу или ошибку
        uiState.lastExportPath?.let { path ->
            Text(
                text = "Экспортировано: $path",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        uiState.exportError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
