package com.personaltool.app

import android.app.Application
import com.personaltool.core.jobs.power.PowerThermalBudgetManager
import com.personaltool.core.storage.cleanup.StagingCleaner
import com.personaltool.core.storage.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class PersonalToolApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val powerThermalBudgetManager: PowerThermalBudgetManager by lazy {
        PowerThermalBudgetManager(this)
    }

    val stagingDirectory: File by lazy {
        File(filesDir, "staging").apply { mkdirs() }
    }

    override fun onCreate() {
        super.onCreate()

        // Asynchronous Staging Cleanup & Call Crash Safety Recovery at startup
        CoroutineScope(Dispatchers.IO).launch {
            StagingCleaner.cleanStagingDirectory(stagingDirectory)
            com.personaltool.app.capture.CallRecordingJournal.recoverPendingSessions(this@PersonalToolApplication)
        }
    }
}
