package com.fluid.dropx.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.fluid.dropx.R
import com.fluid.dropx.core.FileRegistry
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
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class TransferService : Service() {
    // Isolated Coroutine Scope: Uses SupervisorJob so that if an individual network request
    // or file stream crashes, the entire background engine doesn't fall over.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channelId = "transfer_service_channel"
    private val notificationId = 1001

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var locksHeld = false

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val thumbnailManager by lazy { ThumbnailManager(applicationContext) }

    // Caches the web portal's index.html frontend layout into memory so we aren't hammering disk I/O on every request
    private val indexHtmlBytes by lazy {
        assets.open("index.html").use { it.readBytes() }
    }

    // Globally accessible atomic state flags for the UI layers
    companion object {
        var currentSessionId: String? = null
            private set

        var activePort: Int = 0
            private set

        var isEngineRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_STOP_SERVER" -> {
                stopServerGracefully()
                return START_NOT_STICKY
            }
            "ACTION_START_SERVER" -> {
                val ip = intent.getStringExtra("EXTRA_IP") ?: "0.0.0.0"

                val notification = createNotification()
                // Android 14 (API 34) and higher strictly requires identifying foreground types explicitly at runtime
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        notificationId,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(notificationId, notification)
                }

                acquireHardwareLocks()
                startServer(ip)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun createNotification(): android.app.Notification {
        val stopIntent = Intent(this, TransferService::class.java).apply {
            action = "ACTION_STOP_SERVER"
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_dropx_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true) // Blocks the user from swiping the notification away while transfers are alive
            .addAction(
                R.drawable.ic_dropx_share_off,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun startServer(hostIp: String) {
        if (server != null || isEngineRunning) return

        serviceScope.launch {
            try {
                activePort = NetworkManager.findAvailablePort()
                // Generates an short, ephemeral 6-character session sub-route to obscure the web endpoints
                val sessionId = java.util.UUID.randomUUID().toString().take(6)
                currentSessionId = sessionId

                server = embeddedServer(CIO, port = activePort, host = hostIp) {
                    // Installs essential Ktor features for streaming large chunks and files
                    install(PartialContent)      // Handles pause/resume and partial chunks for fast seeking (vital for videos)
                    install(AutoHeadResponse)    // Instantly handles basic browser pre-flight validation checks
                    install(ConditionalHeaders) // Leverages browser caching mechanics
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

                            // Route: Stream-optimised pipeline for downloading the raw files
                            get("/file/{fileId}") {
                                val id = call.parameters["fileId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                                val metadata = FileRegistry.getMetadata(id)
                                val uri = FileRegistry.getUri(id)

                                if (metadata == null || uri == null) {
                                    return@get call.respond(HttpStatusCode.NotFound)
                                }

                                // High Performance Channel: Reads directly from Android contentResolver input stream
                                // and transforms it into a non-blocking ByteReadChannel. This bypasses RAM fragmentation,
                                // allowing multi-gigabyte 4K movies to stream out without crashing with OutOfMemory errors.
                                val content = object : io.ktor.http.content.OutgoingContent.ReadChannelContent() {
                                    override val contentType = ContentType.parse(metadata.mimeType)
                                    override val contentLength = metadata.size
                                    override fun readFrom() = contentResolver.openInputStream(uri)
                                        ?.toByteReadChannel(context = Dispatchers.IO)
                                        ?: throw Exception(getString(R.string.error_stream_unavailable))
                                }

                                // Forces the client browser to treat the incoming bytes as a downloadable attachment asset
                                call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${metadata.name}\"")
                                call.respond(content)
                            }
                        }
                    }
                }

                isEngineRunning = true
                server?.start(wait = false) // Boot the server asynchronously without freezing the execution block

            } catch (e: Exception) {
                isEngineRunning = false
                activePort = 0
                currentSessionId = null
                releaseHardwareLocks()
                stopSelf()
            }
        }
    }

    private fun stopServerGracefully() {
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Gives active connections up to 1 second to wrap up operations before severing socket roots
                    server?.stop(1000, 2000)
                }
            } catch (e: Exception) {
            } finally {
                server = null
                activePort = 0
                currentSessionId = null
                isEngineRunning = false
                releaseHardwareLocks()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // Hardware locks: Tells Android OS that even if the screen turns off or the phone drops into deep
    // battery-saving sleep mode, it must keep CPU processing cycles active and the Wi-Fi radio antenna armed.
    private fun acquireHardwareLocks() {
        if (locksHeld) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dropX:WakeLock").apply {
            setReferenceCounted(false) // Disable reference counting so a single release() call guarantees execution shutdown
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
        server?.stop(500, 1000)
        releaseHardwareLocks()
        serviceScope.cancel() // Kills all active coroutines immediately to prevent background memory leaks
        isEngineRunning = false
        super.onDestroy()
    }
}