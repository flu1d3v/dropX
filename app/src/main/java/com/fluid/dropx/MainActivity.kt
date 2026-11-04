package com.fluid.dropx

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluid.dropx.core.FileRegistry
import com.fluid.dropx.core.SessionStore
import com.fluid.dropx.core.TransferManager
import com.fluid.dropx.network.NetworkManager
import com.fluid.dropx.network.TransferService
import com.fluid.dropx.ui.configure.ConfigureScreen
import com.fluid.dropx.ui.configure.QrDialog
import com.fluid.dropx.ui.files.FilesScreen
import com.fluid.dropx.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Screen { Configure, Files }

class MainActivity : ComponentActivity() {

    private lateinit var transferManager: TransferManager
    private var onPickResult: ((List<android.net.Uri>) -> Unit)? = null

    // Set when the user taps the "Sharing paused" notification
    private var resumeRequested by mutableStateOf(false)

    // System Picker Contract: Launches the standard system media/document selection frame asynchronously
    private val pickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            onPickResult?.invoke(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13 (API 33) and higher demands runtime clearance before posting notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        transferManager = TransferManager(this)
        enableEdgeToEdge() // Injects content padding maps behind structural status bars

        resumeRequested = intent?.action == TransferService.ACTION_RESUME_SHARING

        setContent {
            DropXTheme {
                DropXApp(
                    transferManager = transferManager,
                    resumeRequested = resumeRequested,
                    onResumeHandled = { resumeRequested = false },
                    onRequestPick = { callback ->
                        onPickResult = callback
                        pickerLauncher.launch("*/*") // Explicit wildcards accept any structural data asset format
                    }
                )
            }
        }
    }

    // The app is already open and the user taps the "Sharing paused" notification
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == TransferService.ACTION_RESUME_SHARING) {
            resumeRequested = true
        }
    }
}

@Composable
private fun DropXApp(
    transferManager: TransferManager,
    resumeRequested: Boolean,
    onResumeHandled: () -> Unit,
    onRequestPick: (callback: (List<android.net.Uri>) -> Unit) -> Unit
) {
    var screen        by remember { mutableStateOf(Screen.Configure) }
    var running       by remember { mutableStateOf(false) }
    var showQr        by remember { mutableStateOf(false) }
    var netData       by remember { mutableStateOf(NetworkManager.currentResult) }
    var port          by remember { mutableIntStateOf(0) }
    var selectedIp    by remember { mutableStateOf<String?>(null) }
    var files         by remember { mutableStateOf(FileRegistry.getAllMetadata()) }
    var resumeTrigger by remember { mutableIntStateOf(0) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var pausedByLimit by remember { mutableStateOf(SessionStore.isPaused(context)) }

    // Master State Loop: polls the real hardware/service layers every 2 seconds
    // so the UI always matches the Ktor engine.
    LaunchedEffect(Unit) {
        var lastBoundIp: String? = selectedIp
            ?: NetworkManager.currentResult.hotspotIp
            ?: NetworkManager.currentResult.wifiIp
            ?: NetworkManager.currentResult.unknownIp

        while (true) {
            val freshNetData = NetworkManager.refreshNetworkData()
            val newEffectiveIp = selectedIp
                ?: freshNetData.hotspotIp
                ?: freshNetData.wifiIp
                ?: freshNetData.unknownIp

            val isCurrentlyRunning = TransferService.isEngineRunning

            // Network Drop Protection: if the phone hops network adapters mid-stream, stop the service
            // instead of exposing the files on a different network.
            if (isCurrentlyRunning && newEffectiveIp != lastBoundIp) {
                TransferService.stop(context)
                transferManager.stopTransferSession()
                lastBoundIp = newEffectiveIp
            } else if (!isCurrentlyRunning) {
                lastBoundIp = newEffectiveIp
            }

            netData       = freshNetData
            port          = TransferService.activePort
            running       = isCurrentlyRunning
            pausedByLimit = !isCurrentlyRunning && SessionStore.isPaused(context)
            files         = FileRegistry.getAllMetadata()

            delay(2000)
        }
    }

    // A notification tap just asks for a resume; the real work happens in the effect below
    LaunchedEffect(resumeRequested) {
        if (resumeRequested) {
            resumeTrigger++
            onResumeHandled()
        }
    }

    // Resume a paused session with the SAME port and session code (so existing links keep working).
    // This runs while the app is on screen, which is what lets Android start a dataSync service again.
    LaunchedEffect(resumeTrigger) {
        if (resumeTrigger == 0) return@LaunchedEffect
        val saved = SessionStore.loadPaused(context) ?: return@LaunchedEffect
        if (TransferService.isEngineRunning) return@LaunchedEffect

        val fresh = withContext(Dispatchers.IO) { NetworkManager.refreshNetworkData() }
        val ip = selectedIp ?: fresh.hotspotIp ?: fresh.wifiIp ?: fresh.unknownIp
        if (ip != null) {
            TransferService.start(context, ip, saved.port, saved.sessionId)
        }
    }

    val effectiveIp = selectedIp
        ?: netData.hotspotIp ?: netData.wifiIp ?: netData.unknownIp

    // Dynamic Route URL Assembler
    val shareUrl = if (running && port > 0 && effectiveIp != null && TransferService.currentSessionId != null) {
        "http://$effectiveIp:$port/share/${TransferService.currentSessionId}"
    } else null

    Scaffold(
        containerColor = BackgroundCanvas,
        bottomBar = {
            DropXBottomBar(current = screen, onSelect = { screen = it })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (screen) {
                Screen.Configure -> ConfigureScreen(
                    netData = netData,
                    port = port,
                    running = running,
                    shareUrl = shareUrl,
                    selectedIp = selectedIp,
                    pausedByLimit = pausedByLimit,
                    onResume = { resumeTrigger++ },
                    // Block adapter changes while the server is active
                    onSelectIp = { ip -> if (!running) selectedIp = if (selectedIp == ip) null else ip },
                    onStartStop = {
                        if (running) {
                            TransferService.stop(context)
                            transferManager.stopTransferSession()
                            files = FileRegistry.getAllMetadata()
                        } else {
                            if (effectiveIp != null) {
                                // Foreground initiation through the lifecycle controllers (new session)
                                TransferService.start(context, effectiveIp)
                            }
                        }
                    },
                    onShowQr = { showQr = true }
                )
                Screen.Files -> FilesScreen(
                    files = files,
                    serverRunning = running,
                    shareUrl = shareUrl,
                    onPickFiles = {
                        onRequestPick { uris ->
                            if (uris.isNotEmpty()) {
                                // If the server is running, append to the live catalog without
                                // knocking existing downloads offline.
                                if (running) {
                                    transferManager.appendTransferSession(uris)
                                } else {
                                    transferManager.startTransferSession(uris)
                                }
                                files = FileRegistry.getAllMetadata()
                            }
                        }
                    },
                    onRemoveFile = { id ->
                        FileRegistry.removeFile(id)
                        files = FileRegistry.getAllMetadata()
                    }
                )
            }
        }
    }

    if (showQr && shareUrl != null) {
        QrDialog(url = shareUrl, onDismiss = { showQr = false })
    }
}

// Custom Navigation Bar for switching layout panels cleanly
@Composable
private fun DropXBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(
        containerColor = SurfacePrimary,
        tonalElevation = 0.dp,
        modifier = Modifier.border(androidx.compose.foundation.BorderStroke(1.dp, BorderHairline))
    ) {
        listOf(
            Triple(Screen.Configure, Icons.Default.Tune, stringResource(R.string.nav_tab_configure)),
            Triple(Screen.Files, Icons.Default.Folder, stringResource(R.string.nav_tab_files))
        ).forEach { (screenTarget, iconVector, labelText) ->
            NavigationBarItem(
                selected = current == screenTarget,
                onClick = { onSelect(screenTarget) },
                icon = { Icon(iconVector, null) },
                label = { Text(labelText, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandCharcoal,
                    selectedTextColor = BrandCharcoal,
                    indicatorColor = BrandContainer,
                    unselectedIconColor = TextPrimary,
                    unselectedTextColor = TextPrimary
                )
            )
        }
    }
}