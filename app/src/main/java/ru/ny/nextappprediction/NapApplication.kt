package ru.ny.nextappprediction

import android.app.Application
import ru.ny.nextappprediction.data.db.AppDatabase

class NapApplication : Application() {
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
}
