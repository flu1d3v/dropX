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
import kotlinx.serialization.json.Json



/*
* Foreground service responsible for handling file transfers
* */
class TransferService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channelId = "transfer_service_channel"
    private val notificationId = 1001

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var locksHeld = false

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>?=null
    private val thumbnailManager by lazy { ThumbnailManager(applicationContext) }

    private val defaultPreviewBytes by lazy {
        assets.open("no_preview_available.png").use {
            it.readBytes()
        }
    }

    private val indexHtmlBytes by lazy {
        assets.open("index.html").use { it.readBytes() }
    }


    companion object {
        var currentSessionId: String? = null
            private set

        var activePort: Int = 0
            private set

        var activeIp: String? = null
            private set

        var isEngineRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_STOP_SERVER") {
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = createNotification()


        /*
        android 14+ requires explicitly declaring the foreground service type
        DATA_SYNC is appropriate for network-based file transfer operations
         */
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
        startServer()


        // restart the service if the system kills it
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // required on Android 8.0+ before posting notifications
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "File Transfers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains the connection while transferring files."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val stopIntent = Intent(this, TransferService::class.java).apply {
            action = "ACTION_STOP_SERVER"
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE // Required for Android 12+
        )


        return NotificationCompat.Builder(this,channelId)
            .setContentTitle("dropX")
            .setContentText("File sharing server is active")
            .setSmallIcon(com.fluid.dropx.R.drawable.ic_dropx_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(
                com.fluid.dropx.R.drawable.ic_dropx_share_off,
                "stop server",
                stopPendingIntent
            )
            .build()
    }
    private fun startServer(){
        if (server!=null) return

        serviceScope.launch {
            try {
                activePort = NetworkManager.findAvailablePort()

                val sessionId = java.util.UUID.randomUUID().toString()
                currentSessionId = sessionId


                server = embeddedServer(CIO, port = activePort, host = "0.0.0.0") {
                    install(PartialContent) // enables HTTP range requests
                    install(AutoHeadResponse) // automatically handles HEAD requests
                    install(ConditionalHeaders) // adds caching support via ETag/Last-Modified
                    install(ContentNegotiation) {
                        json(Json {
                            prettyPrint = true
                            isLenient = true
                        })
                    } // enables serialization for request/response bodies

                    routing {
                        route("/share/$sessionId") {
                            get {
                                try {
                                    call.respondBytes(indexHtmlBytes, ContentType.Text.Html)
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "Portal Unavailable")
                                }
                            }
                            get("/api/files") {
                                val allFiles = FileRegistry.getAllMetadata()
                                // ContentNegotiation intercepts this and turns the list into JSON automatically
                                call.respond(allFiles)
                            }
                            get("/preview/{fileId}") {
                                val id = call.parameters["fileId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
                                val metadata = FileRegistry.getMetadata(id) ?: return@get call.respond(HttpStatusCode.NotFound)
                                val uri = FileRegistry.getUri(id) ?: return@get call.respond(HttpStatusCode.NotFound)

                                val thumbBytes = thumbnailManager.getThumbnail(metadata.name,uri, metadata.size, metadata.lastModified)

                                if (thumbBytes != null) {
                                    call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
                                    call.respondBytes(thumbBytes, ContentType.Image.JPEG)
                                } else {
                                    serveDefaultIcon(call)
                                }
                            }
                            get("/file/{fileId}") {
                                val id = call.parameters["fileId"] ?: return@get call.respond(
                                    HttpStatusCode.BadRequest)

                                val metadata = FileRegistry.getMetadata(id)
                                val uri = FileRegistry.getUri(id)

                                if (metadata == null || uri == null) {
                                    return@get call.respond(HttpStatusCode.NotFound)
                                }

                                val content = object : io.ktor.http.content.OutgoingContent.ReadChannelContent() {
                                    override val contentType = ContentType.parse(metadata.mimeType)
                                    override val contentLength = metadata.size
                                    override fun readFrom() = contentResolver.openInputStream(uri)
                                        ?.toByteReadChannel(context = Dispatchers.IO)
                                        ?: throw Exception("Stream unavailable")
                                }

                                call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${metadata.name}\"")
                                call.respond(content)
                            }
                        }
                    }
                }

                server?.start(wait = false)

            } catch (e: Exception){
                releaseHardwareLocks()
                stopSelf()
            }
        }
    }


    private fun acquireHardwareLocks() {
        if (locksHeld) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dropX:WakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        val wm = getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_HIGH_PERF
            else WifiManager.WIFI_MODE_FULL,
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


    private suspend fun serveDefaultIcon(call: io.ktor.server.application.ApplicationCall) {
        try {
            call.respondBytes(defaultPreviewBytes, ContentType.Image.PNG)
        } catch (e: Exception) {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private fun stopServer(){
        server?.stop(1000, 2000)
        server = null
    }

    override fun onDestroy() {
        stopServer()
        releaseHardwareLocks()
        serviceScope.cancel()
        super.onDestroy()
    }
}