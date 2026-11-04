package com.fluid.dropx.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.fluid.dropx.MainActivity
import com.fluid.dropx.R
import com.fluid.dropx.core.FileRegistry
import com.fluid.dropx.core.SessionStore
import com.fluid.dropx.core.ThumbnailManager
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

class TransferService : Service() {
    // Isolated Coroutine Scope: Uses SupervisorJob so that if an individual network request
    // or file stream crashes, the entire background engine doesn't fall over.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val channelId = "transfer_service_channel"        // silent, ongoing "server is active"
    private val alertChannelId = "transfer_alert_channel"      // audible, for "sharing paused"
    private val notificationId = 1001
    private val pausedNotificationId = 1002

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var locksHeld = false

    @Volatile
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    // True from the moment a start is accepted until the engine is fully stopped.
    // Prevents two quick start intents from booting two servers.
    private val engineRequested = AtomicBoolean(false)

    private val thumbnailManager by lazy { ThumbnailManager(applicationContext) }

    // Caches the web portal's index.html frontend layout into memory so we aren't hammering disk I/O on every request
    private val indexHtmlBytes by lazy {
        assets.open("index.html").use { it.readBytes() }
    }

    companion object {
        const val ACTION_START = "ACTION_START_SERVER"
        const val ACTION_STOP = "ACTION_STOP_SERVER"
        const val ACTION_RESUME_SHARING = "com.fluid.dropx.ACTION_RESUME_SHARING"
        const val EXTRA_IP = "EXTRA_IP"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_SESSION = "EXTRA_SESSION"

        // Globally readable state flags for the UI layers (written from background threads -> @Volatile)
        @Volatile
        var currentSessionId: String? = null
            private set

        @Volatile
        var activePort: Int = 0
            private set

        @Volatile
        var isEngineRunning: Boolean = false
            private set

        /**
         * Starts (or resumes) the server. For a normal start leave port/sessionId at their defaults.
         * To bring back a paused session with the same link, pass the saved port and session code.
         */
        fun start(context: Context, ip: String, port: Int = 0, sessionId: String? = null) {
            val intent = Intent(context, TransferService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_IP, ip)
                if (port > 0) putExtra(EXTRA_PORT, port)
                if (sessionId != null) putExtra(EXTRA_SESSION, sessionId)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopServerGracefully()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val ip = intent.getStringExtra(EXTRA_IP) ?: "0.0.0.0"
                val wantedPort = intent.getIntExtra(EXTRA_PORT, 0)
                val wantedSession = intent.getStringExtra(EXTRA_SESSION)

                val notification = createNotification()
                try {
                    // Android 14 (API 34)+ requires the foreground type to be declared at runtime
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    } else {
                        startForeground(notificationId, notification)
                    }
                } catch (e: Exception) {
                    // e.g. Android 15 "time limit already exhausted for dataSync" when not visible.
                    // Don't crash: just stop. The app UI will offer Resume.
                    stopSelf()
                    return START_NOT_STICKY
                }

                // The user is (re)starting: the "paused" alert is no longer relevant
                getSystemService(NotificationManager::class.java)?.cancel(pausedNotificationId)

                acquireHardwareLocks()
                startServer(ip, wantedPort, wantedSession)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Android 15+ (API 35): the dataSync time budget (about 6 h per 24 h in the background) is used up.
     * The service has only a few seconds to call stopSelf(), otherwise the system throws
     * "A foreground service of type dataSync did not stop within its timeout".
     *
     * So we: remember the session (so Resume restores the same link), tell the user, and stop right away.
     * Real shutdown of the server happens in onDestroy().
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val port = activePort
        val session = currentSessionId
        if (port > 0 && session != null) {
            SessionStore.savePaused(this, port, session)
            postPausedNotification()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val ongoing = NotificationChannel(
            channelId,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(ongoing)

        val alerts = NotificationChannel(
            alertChannelId,
            getString(R.string.notification_alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_alert_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(alerts)
    }

    private fun createNotification(): android.app.Notification {
        val stopIntent = Intent(this, TransferService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Tapping the notification body opens the app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_dropx_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .setOngoing(true) // Blocks the user from swiping the notification away while transfers are alive
            .addAction(
                R.drawable.ic_dropx_share_off,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()
    }

    // "Sharing paused" alert. Tapping it opens the app with ACTION_RESUME_SHARING; because the user
    // brings the app to the foreground, Android resets the dataSync timer and we may start again.
    private fun postPausedNotification() {
        val resumeIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_RESUME_SHARING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 2, resumeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = getString(R.string.notification_paused_text)
        val notification = NotificationCompat.Builder(this, alertChannelId)
            .setContentTitle(getString(R.string.notification_paused_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_dropx_share_off)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(pausedNotificationId, notification)
    }

    private fun newSessionId(): String = java.util.UUID.randomUUID().toString().take(6)

    private fun startServer(hostIp: String, wantedPort: Int, wantedSession: String?) {
        // Already running or a start is already in progress -> ignore duplicate requests
        if (isEngineRunning || !engineRequested.compareAndSet(false, true)) return

        serviceScope.launch {
            try {
                // Resume keeps the old port if it is still free; otherwise pick a fresh one
                activePort = if (wantedPort > 0 && NetworkManager.isPortFree(wantedPort)) {
                    wantedPort
                } else {
                    NetworkManager.findAvailablePort()
                }

                // Resume keeps the old session code so existing links keep working
                val sessionId = wantedSession ?: newSessionId()
                currentSessionId = sessionId

                val engine = embeddedServer(CIO, port = activePort, host = hostIp) {
                    // Installs essential Ktor features for streaming large chunks and files
                    install(PartialContent)      // Handles pause/resume and partial chunks for fast seeking (vital for videos)
                    install(AutoHeadResponse)    // Instantly handles basic browser pre-flight validation checks
                    install(ConditionalHeaders)  // Leverages browser caching mechanics
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                        })
                    }

                    routing {
                        // All communication endpoints hide behind the dynamic randomized session id sub-path
                        route("/share/$sessionId") {
                            // Route: Serves the web browser portal interface
                            get {
                                try {
                                    call.respondBytes(indexHtmlBytes, ContentType.Text.Html)
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, getString(R.string.error_portal_unavailable))
                                }
                            }

                            // Route: Delivers the entire payload file metadata catalog as JSON
                            get("/api/files") {
                                val allFiles = FileRegistry.getAllMetadata()
                                call.respond(allFiles)
                            }

                            // Route: Streams requested image/media thumbnails safely from your manager's cache layers
                            get("/preview/{fileId}") {
                                val id = call.parameters["fileId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                                val metadata = FileRegistry.getMetadata(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                                val uri = FileRegistry.getUri(id) ?: return@get call.respond(HttpStatusCode.NotFound)

                                val thumbBytes = thumbnailManager.getThumbnail(metadata.name, uri, metadata.size, metadata.lastModified)

                                if (thumbBytes != null) {
                                    // Instructs the remote browser to cache the thumbnail image locally for exactly 24 hours
                                    call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                                    return@get call.respondBytes(thumbBytes, ContentType.Image.JPEG)
                                }
                                call.respond(HttpStatusCode.NoContent)
                            }

                            // Route: Stream-optimised pipeline for downloading the raw files (supports instant seeking)
                            get("/file/{fileId}") {
                                val id = call.parameters["fileId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                                val metadata = FileRegistry.getMetadata(id)
                                val uri = FileRegistry.getUri(id)

                                if (metadata == null || uri == null) {
                                    return@get call.respond(HttpStatusCode.NotFound)
                                }

                                // Safe MIME parsing: a weird mime string must not crash the request
                                val type = runCatching { ContentType.parse(metadata.mimeType) }
                                    .getOrDefault(ContentType.Application.OctetStream)

                                // ?download=1  -> force "Save as";  otherwise inline (video/audio/pdf preview)
                                val asAttachment = call.request.queryParameters["download"] == "1"
                                call.response.header(HttpHeaders.ContentDisposition, contentDisposition(metadata.name, asAttachment))

                                call.respond(UriFileContent(applicationContext, uri, type, metadata.size))
                            }
                        }
                    }
                }

                server = engine
                engine.start(wait = false)   // Boot the server asynchronously without freezing the execution block
                isEngineRunning = true       // only flip AFTER start() succeeded

                // A fresh/resumed session is live, so any remembered "paused" state is obsolete
                SessionStore.clear(applicationContext)

            } catch (e: Exception) {
                runCatching { server?.stop(0, 500) }
                server = null
                isEngineRunning = false
                activePort = 0
                currentSessionId = null
                engineRequested.set(false)
                releaseHardwareLocks()
                stopSelf()
            }
        }
    }

    /** Stops the Ktor engine and resets all state. Safe to call more than once. */
    private fun stopEngineNow(graceMs: Long, timeoutMs: Long) {
        val s = server
        server = null
        runCatching { s?.stop(graceMs, timeoutMs) }
        activePort = 0
        currentSessionId = null
        isEngineRunning = false
        engineRequested.set(false)
        releaseHardwareLocks()
    }

    // User pressed Stop (app button or notification action): forget any paused session and shut down.
    private fun stopServerGracefully() {
        SessionStore.clear(this)
        getSystemService(NotificationManager::class.java)?.cancel(pausedNotificationId)

        serviceScope.launch {
            // Gives active connections a short moment to wrap up before closing sockets
            stopEngineNow(1000, 2000)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // Hardware locks: keep CPU and Wi-Fi radio active while serving. The OS releases both automatically
    // if the process dies, and we release them explicitly on every normal stop path.
    private fun acquireHardwareLocks() {
        if (locksHeld) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dropX:WakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        val wm = getSystemService(WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(
            WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
            "dropX:WifiLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
        locksHeld = true
    }

    private fun releaseHardwareLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock = null
        locksHeld = false
    }

    override fun onDestroy() {
        // Short grace: onDestroy runs on the main thread, so don't block it for long
        stopEngineNow(200, 800)
        serviceScope.cancel()
        super.onDestroy()
    }
}

private fun contentDisposition(name: String, attachment: Boolean): String {
    val kind = if (attachment) "attachment" else "inline"
    // ASCII-only fallback for old clients
    val ascii = name.map { if (it.code in 32..126 && it != '"' && it != '\\') it else '_' }.joinToString("")
    // RFC 5987 UTF-8 version for modern browsers/VLC
    val utf8 = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    return "$kind; filename=\"$ascii\"; filename*=UTF-8''$utf8"
}