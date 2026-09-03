package com.personaltool.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personaltool.app.PersonalToolApplication
import com.personaltool.app.ui.calls.CallsScreen
import com.personaltool.app.ui.library.LibraryScreen
import com.personaltool.app.ui.media.MediaIntakeScreen
import com.personaltool.app.ui.remotedev.RemoteDesktopViewerScreen
import com.personaltool.app.ui.remotedev.RemoteDevScreen
import com.personaltool.app.ui.system.SystemStatusScreen
import com.personaltool.app.ui.transcript.TranscriptViewerSheet
import com.personaltool.app.viewmodel.AppViewModelFactory
import com.personaltool.app.viewmodel.CallsViewModel
import com.personaltool.app.viewmodel.LibraryViewModel
import com.personaltool.app.viewmodel.MediaIntakeViewModel
import com.personaltool.app.viewmodel.RemoteDesktopViewModel
import com.personaltool.app.viewmodel.RemoteDevViewModel
import com.personaltool.app.viewmodel.SystemStatusViewModel
import com.personaltool.app.viewmodel.TranscriptViewModel
import com.personaltool.core.designsystem.components.CopperDivider
import com.personaltool.core.designsystem.components.InstrumentButton
import com.personaltool.core.designsystem.components.InstrumentButtonStyle
import com.personaltool.core.designsystem.components.TechnicalTopBar
import com.personaltool.core.designsystem.theme.IndustrialTheme

enum class MainNavigationTab(val index: String, val title: String, val systemTag: String) {
    CALLS("01", "CALL ARCHIVE", "CAPTURE // UNVERIFIED"),
    MEDIA("02", "MEDIA INTAKE", "EXTRACTOR // LIMITED"),
    LIBRARY("03", "UNIFIED VAULT", "STORAGE // LOCAL"),
    SYSTEM("04", "SYSTEM & POWER", "STATUS // LOCAL"),
    REMOTE_DEV("05", "REMOTE DEV", "LABS // UNAVAILABLE")
}

@Composable
fun MainScreen(
    sharedUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PersonalToolApplication
    val factory = remember { AppViewModelFactory(app) }

    val callsViewModel: CallsViewModel = viewModel(factory = factory)
    val mediaIntakeViewModel: MediaIntakeViewModel = viewModel(factory = factory)
    val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
    val transcriptViewModel: TranscriptViewModel = viewModel(factory = factory)
    val systemStatusViewModel: SystemStatusViewModel = viewModel(factory = factory)
    val remoteDevViewModel: RemoteDevViewModel = viewModel(factory = factory)
    val remoteDesktopViewModel: RemoteDesktopViewModel = viewModel(factory = factory)

    val desktopState by remoteDesktopViewModel.uiState.collectAsState()

    var currentTab by remember {
        mutableStateOf(if (sharedUrl != null) MainNavigationTab.MEDIA else MainNavigationTab.CALLS)
    }

    val colors = IndustrialTheme.colors
    val typography = IndustrialTheme.typography

    if (desktopState.isViewerOpen) {
        // Fullscreen Remote Desktop LAN Viewer (M9)
        RemoteDesktopViewerScreen(viewModel = remoteDesktopViewModel)
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            // Technical Retro-Industrial Header
            TechnicalTopBar(
                title = currentTab.title,
                systemTag = "${currentTab.index} // ${currentTab.systemTag}"
            )

            // Screen Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    MainNavigationTab.CALLS -> CallsScreen(
                        viewModel = callsViewModel,
                        onOpenTranscript = { targetId, title, audioPath, durationMs ->
                            transcriptViewModel.openTranscript(targetId, title, audioPath, durationMs)
                        }
                    )
                    MainNavigationTab.MEDIA -> MediaIntakeScreen(
                        viewModel = mediaIntakeViewModel,
                        initialUrl = sharedUrl
                    )
                    MainNavigationTab.LIBRARY -> LibraryScreen(
                        viewModel = libraryViewModel,
                        onPlayAudio = { targetId, title, audioPath, durationMs ->
                            // Explicit audio playback request boundary (audio engine surface bound in P3-E04)
                        },
                        onPlayVideo = { targetId, title, filePath ->
                            // Explicit video playback request boundary (video engine surface bound in P3-E05)
                        },
                        onOpenTranscript = { targetId, title, audioPath, durationMs ->
                            transcriptViewModel.openTranscript(targetId, title, audioPath, durationMs)
                        }
                    )
                    MainNavigationTab.SYSTEM -> SystemStatusScreen(
                        viewModel = systemStatusViewModel
                    )
                    MainNavigationTab.REMOTE_DEV -> RemoteDevScreen(
                        viewModel = remoteDevViewModel,
                        onOpenRemoteDesktop = { remoteDesktopViewModel.openViewer() }
                    )
                }
            }

            CopperDivider()

            // Industrial Technical Tab Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainNavigationTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    InstrumentButton(
                        onClick = { currentTab = tab },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        style = if (isSelected) InstrumentButtonStyle.PRIMARY else InstrumentButtonStyle.GHOST
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tab.index,
                                style = typography.monoSmall,
                                color = if (isSelected) colors.accent else colors.textMuted
                            )
                            Text(
                                text = when (tab) {
                                    MainNavigationTab.CALLS -> "CALLS"
                                    MainNavigationTab.MEDIA -> "MEDIA"
                                    MainNavigationTab.LIBRARY -> "VAULT"
                                    MainNavigationTab.SYSTEM -> "SYSTEM"
                                    MainNavigationTab.REMOTE_DEV -> "DEV"
                                },
                                style = typography.monoSmall,
                                color = if (isSelected) colors.textPrimary else colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        // Modal Transcript Viewer Sheet
        TranscriptViewerSheet(viewModel = transcriptViewModel)
    }
}
