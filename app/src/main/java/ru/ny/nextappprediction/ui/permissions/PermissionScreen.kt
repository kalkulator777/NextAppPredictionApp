package ru.ny.nextappprediction.ui.permissions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Composable
fun PermissionScreen(
    permissionChecker: PermissionChecker,
    onAllPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(permissionChecker.checkAll()) }

    // Launcher для запроса разрешения уведомлений (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionState = permissionChecker.checkAll()
    }

    // Launcher для запроса разрешения активности (Android 10+)
    val activityPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionState = permissionChecker.checkAll()
    }

    // Автоматическая перепроверка разрешений при возврате из настроек
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = permissionChecker.checkAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Заголовок
        Text(
            text = "📱 NextAppPrediction",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Для работы приложения необходимы\nследующие разрешения:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Карточка: Статистика использования
        PermissionCard(
            title = "Статистика использования",
            isGranted = permissionState.usageStats,
            buttonText = "Открыть настройки",
            onButtonClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                context.startActivity(intent)
            }
        )

        // Карточка: Уведомления (только для Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionCard(
                title = "Уведомления",
                isGranted = permissionState.notifications,
                buttonText = "Разрешить",
                onButtonClick = {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        // Карточка: Распознавание активности (только для Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PermissionCard(
                title = "Физическая активность",
                isGranted = permissionState.activityRecognition,
                buttonText = "Разрешить",
                onButtonClick = {
                    activityPermissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION)
                }
            )
        }

        // Карточка: Работа в фоне
        PermissionCard(
            title = "Работа в фоне",
            isGranted = permissionState.batteryOptimization,
            buttonText = "Настроить",
            onButtonClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Кнопка "Продолжить"
        Button(
            onClick = onAllPermissionsGranted,
            enabled = permissionChecker.allGranted(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Продолжить",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun PermissionCard(
    title: String,
    isGranted: Boolean,
    buttonText: String,
    onButtonClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isGranted) "✓" else "✗",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (!isGranted) {
                Button(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(buttonText)
                }
            }
        }
    }
}
