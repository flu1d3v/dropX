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
import com.fluid.dropx.core.TransferManager
import com.fluid.dropx.network.NetworkManager
import com.fluid.dropx.network.TransferService
import com.fluid.dropx.ui.configure.ConfigureScreen
import com.fluid.dropx.ui.configure.QrDialog
import com.fluid.dropx.ui.files.FilesScreen
import com.fluid.dropx.ui.theme.*
import kotlinx.coroutines.delay

enum class Screen { Configure, Files }

class MainActivity : ComponentActivity() {

    private lateinit var transferManager: TransferManager
    private var onPickResult: ((List<android.net.Uri>) -> Unit)? = null

    // System Picker Contract: Launches the standard system media/document selection frame asynchronously
    private val pickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            onPickResult?.invoke(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13 (API 33) and higher demands runtime clearance before posting active foreground status notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        transferManager = TransferManager(this)
        enableEdgeToEdge() // Injects content padding maps behind structural status bars

        setContent {
            DropXTheme {
                DropXApp(
                    transferManager = transferManager,
                    onRequestPick = { callback ->
                        onPickResult = callback
                        pickerLauncher.launch("*/*") // Explicit wildcards accept any structural data asset format
                    }
                )
            }
        }
    }
}

@Composable
private fun DropXApp(
    transferManager: TransferManager,
    onRequestPick: (callback: (List<android.net.Uri>) -> Unit) -> Unit
) {
    var screen      by remember { mutableStateOf(Screen.Configure) }
    var running     by remember { mutableStateOf(false) }
    var showQr      by remember { mutableStateOf(false) }
    var netData     by remember { mutableStateOf(NetworkManager.currentResult) }
    var port        by remember { mutableIntStateOf(0) }
    var selectedIp  by remember { mutableStateOf<String?>(null) }
    var files       by remember { mutableStateOf(FileRegistry.getAllMetadata()) }

    val context = androidx.compose.ui.platform.LocalContext.current

    val actionStart = stringResource(R.string.intent_action_start_server)
    val actionStop  = stringResource(R.string.intent_action_stop_server)
    val extraIp     = stringResource(R.string.intent_extra_ip)

    // Master State Loop: Loops in the background every 2000ms to poll the real hardware/service layers.
    // This serves as a heartbeat tracking loop keeping your UI state perfectly matched to the Ktor engine.
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

            // Network Drop Protection: If the phone hops network adaptors mid-stream (e.g., leaving local Wi-Fi router bounds),
            // instantly kill the service and dump active links before unauthorized external entities scrape endpoints.
            if (isCurrentlyRunning && newEffectiveIp != lastBoundIp) {
                val stopIntent = Intent(context, TransferService::class.java).apply {
                    action = actionStop
                }
                context.startService(stopIntent)

                transferManager.stopTransferSession()
                lastBoundIp = newEffectiveIp
            } else if (!isCurrentlyRunning) {
                lastBoundIp = newEffectiveIp
            }

            // Flush changes straight to structural Compose state variables to invoke clean recompositions
            netData = freshNetData
            port    = TransferService.activePort
            running = isCurrentlyRunning
            files   = FileRegistry.getAllMetadata()

            delay(2000)
        }
    }

    val effectiveIp = selectedIp
        ?: netData.hotspotIp ?: netData.wifiIp ?: netData.unknownIp

    // Dynamic Route URL Assembler tracking line
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
                    // Block adaptor modification choices if the server socket pipeline is active
                    onSelectIp = { ip -> if (!running) selectedIp = if (selectedIp == ip) null else ip },
                    onStartStop = {
                        if (running) {
                            val stopIntent = Intent(context, TransferService::class.java).apply {
                                action = actionStop
                            }
                            context.startService(stopIntent)

                            transferManager.stopTransferSession()
                            files = FileRegistry.getAllMetadata()
                        } else {
                            if (effectiveIp != null) {
                                // Foreground Initiation: Boots the service context through the system lifecycle controllers
                                val startIntent = Intent(context, TransferService::class.java).apply {
                                    action = actionStart
                                    putExtra(extraIp, effectiveIp)
                                }
                                context.startForegroundService(startIntent)
                            }
                        }
                    },
                    onShowQr = { showQr = true }
                )
                Screen.Files -> FilesScreen(
                    files = files,
                    serverRunning = running,
                    onPickFiles = {
                        onRequestPick { uris ->
                            if (uris.isNotEmpty()) {
                                // UI-Level Appending Lock: If the server is actively running, append extra files
                                // to the active catalog without knocking existing downloads offline.
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