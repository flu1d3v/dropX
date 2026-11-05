package com.fluid.dropx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat
import com.fluid.dropx.core.FileRegistry
import com.fluid.dropx.core.SessionStore
import com.fluid.dropx.core.TransferManager
import com.fluid.dropx.network.NetworkManager
import com.fluid.dropx.network.TransferService
import com.fluid.dropx.ui.configure.ConfigureScreen
import com.fluid.dropx.ui.configure.QrDialog
import com.fluid.dropx.ui.files.FilesScreen
import com.fluid.dropx.ui.theme.DropXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class Screen { Configure, Files }

class MainActivity : ComponentActivity() {

    private lateinit var transferManager: TransferManager
    private var onPickResult: ((List<android.net.Uri>) -> Unit)? = null

    // Set when the user taps the "Sharing paused" notification
    private var resumeRequested by mutableStateOf(false)

    // True while the app is on screen; the status polling only runs then (saves battery)
    private var appVisible by mutableStateOf(false)

    // System file picker
    private val pickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            onPickResult?.invoke(uris)
        }

    // Android 13+ notification permission (asked only if it isn't granted yet)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* nothing to do */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        transferManager = TransferManager(this)
        enableEdgeToEdge()

        resumeRequested = intent?.action == TransferService.ACTION_RESUME_SHARING

        setContent {
            DropXTheme {
                DropXApp(
                    transferManager = transferManager,
                    visible = appVisible,
                    resumeRequested = resumeRequested,
                    onResumeHandled = { resumeRequested = false },
                    onRequestPick = { callback ->
                        onPickResult = callback
                        pickerLauncher.launch("*/*")
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appVisible = true
    }

    override fun onStop() {
        appVisible = false
        super.onStop()
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
    visible: Boolean,
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
    val resources = androidx.compose.ui.platform.LocalResources.current

    var pausedByLimit by remember { mutableStateOf(SessionStore.isPaused(context)) }

    // Status loop: mirrors the real network/service state into the UI every 2 seconds,
    // but only while the app is visible. (Network-loss protection lives in the service itself.)
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            val freshNetData = withContext(Dispatchers.IO) { NetworkManager.refreshNetworkData() }
            val isCurrentlyRunning = TransferService.isEngineRunning

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
        val currentIps = listOfNotNull(fresh.hotspotIp, fresh.wifiIp, fresh.unknownIp)
        // Same link only works if the original IP still exists on this phone.
        // Otherwise fall back to the preferred IP (the link then necessarily changes).
        val ip = if (saved.ip in currentIps) saved.ip
        else selectedIp ?: fresh.hotspotIp ?: fresh.wifiIp ?: fresh.unknownIp
        if (ip != null) {
            TransferService.start(context, ip, saved.port, saved.sessionId)
            if (ip != saved.ip) {
                Toast.makeText(context, R.string.toast_resume_new_link, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, R.string.toast_no_network, Toast.LENGTH_SHORT).show()
        }
    }

    val effectiveIp = selectedIp
        ?: netData.hotspotIp ?: netData.wifiIp ?: netData.unknownIp

    // The link must use the IP the server is REALLY bound to, not the currently preferred one
    val linkHost = TransferService.boundIp ?: effectiveIp
    val shareUrl = if (running && port > 0 && linkHost != null && TransferService.currentSessionId != null) {
        "http://$linkHost:$port/share/${TransferService.currentSessionId}"
    } else null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                            TransferService.stop(context)   // files stay selected; remove them individually when stopped
                        } else if (effectiveIp != null) {
                            TransferService.start(context, effectiveIp)   // new session
                        } else {
                            Toast.makeText(context, R.string.toast_no_network, Toast.LENGTH_SHORT).show()
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
                                val skipped = transferManager.appendTransferSession(uris)
                                files = FileRegistry.getAllMetadata()
                                if (skipped > 0) {
                                    Toast.makeText(
                                        context,
                                        resources.getString(R.string.toast_files_skipped, skipped),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    onRemoveFile = { id ->
                        FileRegistry.removeFile(id)
                        files = FileRegistry.getAllMetadata()
                    },
                    onClearAll = {
                        FileRegistry.clear()
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

// Bottom navigation, fully driven by the theme so light and dark mode both look right
@Composable
private fun DropXBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        modifier = Modifier.border(BorderStroke(1.dp, colors.outline))
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
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.surfaceVariant,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant
                )
            )
        }
    }
}