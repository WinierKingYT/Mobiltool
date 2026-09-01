package com.personaltool.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.personaltool.app.PersonalToolApplication

class AppViewModelFactory(
    private val application: PersonalToolApplication
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = application.database
        return when {
            modelClass.isAssignableFrom(CallsViewModel::class.java) -> {
                CallsViewModel(application = application, callDao = db.callDao()) as T
            }
            modelClass.isAssignableFrom(MediaIntakeViewModel::class.java) -> {
                MediaIntakeViewModel(application = application, mediaDao = db.mediaDao()) as T
            }
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(callDao = db.callDao(), mediaDao = db.mediaDao()) as T
            }
            modelClass.isAssignableFrom(TranscriptViewModel::class.java) -> {
                TranscriptViewModel(
                    transcriptDao = db.transcriptDao(),
                    callDao = db.callDao(),
                    mediaDao = db.mediaDao()
                ) as T
            }
            modelClass.isAssignableFrom(SystemStatusViewModel::class.java) -> {
                SystemStatusViewModel(
                    powerThermalBudgetManager = application.powerThermalBudgetManager,
                    callDao = db.callDao(),
                    mediaDao = db.mediaDao(),
                    transcriptDao = db.transcriptDao(),
                    stagingDir = application.stagingDirectory
                ) as T
            }
            modelClass.isAssignableFrom(RemoteDevViewModel::class.java) -> {
                RemoteDevViewModel() as T
            }
            modelClass.isAssignableFrom(RemoteDesktopViewModel::class.java) -> {
                RemoteDesktopViewModel() as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
