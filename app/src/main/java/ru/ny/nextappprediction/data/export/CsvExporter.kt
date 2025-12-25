package ru.ny.nextappprediction.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ny.nextappprediction.data.db.AppLaunchEntity
import ru.ny.nextappprediction.data.repository.AppLaunchRepository
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class CsvExporter(
    private val context: Context,
    private val repository: AppLaunchRepository
) {
    /**
     * Экспортирует все данные в CSV файл
     * @return File или null при ошибке
     */
    suspend fun export(): File? {
        return withContext(Dispatchers.IO) {
            try {
                val launches = repository.getAllForExport()
                if (launches.isEmpty()) return@withContext null

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(Date())
                val fileName = "nap_export_$timestamp.csv"

                // Для Android 10+ используем MediaStore API
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    return@withContext exportViaMediaStore(fileName, launches)
                } else {
                    // Для старых версий используем прямой доступ к Downloads
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )
                    val file = File(downloadsDir, fileName)
                    writeCsvData(file.outputStream(), launches)
                    return@withContext file
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Экспорт через MediaStore API для Android 10+
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportViaMediaStore(fileName: String, launches: List<AppLaunchEntity>): File? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        resolver.openOutputStream(uri)?.use { outputStream ->
            writeCsvData(outputStream, launches)
        }

        // Возвращаем фейковый File с путем для UI
        return File(Environment.DIRECTORY_DOWNLOADS, fileName)
    }

    /**
     * Записывает CSV данные в поток
     */
    private fun writeCsvData(outputStream: OutputStream, launches: List<AppLaunchEntity>) {
        outputStream.bufferedWriter().use { writer ->
            // Заголовок
            writer.write("id,timestamp,packageName,hour,dayOfWeek,timeSlot,previousApp,isCharging,batteryLevel")
            writer.newLine()

            // Данные
            for (launch in launches) {
                writer.write(buildString {
                    append(launch.id).append(',')
                    append(launch.timestamp).append(',')
                    append(launch.packageName).append(',')
                    append(launch.hour).append(',')
                    append(launch.dayOfWeek).append(',')
                    append(launch.timeSlot).append(',')
                    append(launch.previousApp ?: "").append(',')
                    append(launch.isCharging).append(',')
                    append(launch.batteryLevel)
                })
                writer.newLine()
            }
        }
    }
}
