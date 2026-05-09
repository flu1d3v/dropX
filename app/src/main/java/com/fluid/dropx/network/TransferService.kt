package com.fluid.dropx.network


import android.app.NotificationChannel
import android.app.NotificationManager
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
import com.fluid.dropx.model.FileMetadata
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.file
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.receive
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json



/*
* Foreground service responsible for handling file transfers
* */
class TransferService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channelId = "transfer_service"
    private val notificationId = 101

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var locksHeld = false

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>?=null
    private val thumbnailManager = ThumbnailManager(this)

    companion object {
        var currentSessionId: String? = null
            private set

    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this,channelId)
            .setContentTitle("dropX-test")
            .setContentText("dropX-test")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

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
                "File Transfer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startServer(){
        if (server!=null) return

        serviceScope.launch {
            try {
                val sessionId = java.util.UUID.randomUUID().toString()
                currentSessionId = sessionId


                server = embeddedServer(CIO, port = 1234, host = "0.0.0.0") {
                    install(CallLogging) // logs incoming requests
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
//                                    serveDefaultIcon(call)
                                }
                            }
                            get("/file/{fileId}") {
                                val id = call.parameters["fileId"] ?: return@get call.respond(
                                    HttpStatusCode.BadRequest)

                                val metadata = FileRegistry.getMetadata(id)
                                val uri = FileRegistry.getUri(id)

                                if (metadata != null && uri != null) {
                                    try {
                                        val inputStream = contentResolver.openInputStream(uri)

                                        if (inputStream != null) {
                                            val channel = inputStream.toByteReadChannel() // wraps InputStream into a non-blocking, coroutine based reader

                                            call.response.header(HttpHeaders.ContentDisposition, "inline; filename=\"${metadata.name}\"")
                                            call.response.header(HttpHeaders.ContentLength,metadata.size.toString())
                                            call.response.header(HttpHeaders.ContentType, metadata.mimeType.toString())

                                            call.respond(channel)
                                        } else {
                                            call.respond(HttpStatusCode.Companion.InternalServerError, "Stream unavailable")
                                        }
                                    } catch (e: Exception) {
                                        call.respond(HttpStatusCode.Companion.InternalServerError, "Transfer Interrupted")
                                    }
                                } else {
                                    call.respond(HttpStatusCode.Companion.NotFound, "Invalid link")
                                }

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
            val iconBytes = assets.open("").readBytes()
            call.respondBytes(iconBytes, ContentType.Image.PNG)
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