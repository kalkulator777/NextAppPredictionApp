package ru.ny.nextappprediction

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.ny.nextappprediction.service.RetrainWorker
import ru.ny.nextappprediction.ui.main.MainScreen
import ru.ny.nextappprediction.ui.permissions.PermissionChecker
import ru.ny.nextappprediction.ui.permissions.PermissionScreen
import ru.ny.nextappprediction.ui.theme.NextAppPredictionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Планируем ночное переобучение
        RetrainWorker.schedule(this)

        setContent {
            NextAppPredictionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val permissionChecker = remember { PermissionChecker(context) }

    // Состояние: все разрешения даны?
    var allPermissionsGranted by remember {
        mutableStateOf(permissionChecker.allGranted())
    }

    if (allPermissionsGranted) {
        MainScreen()
    } else {
        PermissionScreen(
            permissionChecker = permissionChecker,
            onAllPermissionsGranted = { allPermissionsGranted = true }
        )
    }
}
