package com.fluid.dropx

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    private val pickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            onPickResult?.invoke(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        transferManager = TransferManager(this)
        enableEdgeToEdge()

        setContent {
            DropXTheme {
                DropXApp(
                    transferManager = transferManager,
                    onRequestPick = { callback ->
                        onPickResult = callback
                        pickerLauncher.launch("*/*")
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

    // ── CENTRAL OS STATE COUPLING LOOP ───────────────────────────────────────
    LaunchedEffect(Unit) {
        while (true) {
            netData = NetworkManager.refreshNetworkData()
            port    = TransferService.activePort
            running = port > 0
            files   = FileRegistry.getAllMetadata()
            delay(2000)
        }
    }

    val effectiveIp = selectedIp
        ?: netData.hotspotIp ?: netData.wifiIp ?: netData.unknownIp

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
                    effectiveIp = effectiveIp,
                    onSelectIp = { ip -> if (!running) selectedIp = if (selectedIp == ip) null else ip },
                    onStartStop = {
                        if (running) {
                            val stopIntent = Intent(context, TransferService::class.java).apply {
                                action = "ACTION_STOP_SERVER"
                            }
                            context.stopService(stopIntent)
                            running = false
                        } else {
                            if (files.isNotEmpty() && effectiveIp != null) {
                                val startIntent = Intent(context, TransferService::class.java).apply {
                                    putExtra("EXTRA_IP", effectiveIp)
                                    putExtra("EXTRA_PORT", 50505)
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(startIntent)
                                } else {
                                    context.startService(startIntent)
                                }
                                running = true
                            } else if (files.isEmpty()) {
                                screen = Screen.Files
                            }
                        }
                    },
                    onRefresh = { if (!running) netData = NetworkManager.refreshNetworkData() },
                    onShowQr = { showQr = true }
                )
                Screen.Files -> FilesScreen(
                    files = files,
                    serverRunning = running,
                    onPickFiles = {
                        onRequestPick { uris ->
                            if (uris.isNotEmpty()) {
                                transferManager.startTransferSession(uris)
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

@Composable
private fun DropXBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(
        containerColor = SurfacePrimary,
        tonalElevation = 0.dp,
        modifier = Modifier.border(androidx.compose.foundation.BorderStroke(1.dp, BorderHairline))
    ) {
        listOf(
            Triple(Screen.Configure, androidx.compose.material.icons.Icons.Default.Tune, "Configure"),
            Triple(Screen.Files, androidx.compose.material.icons.Icons.Default.Folder, "Files")
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
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary
                )
            )
        }
    }
}