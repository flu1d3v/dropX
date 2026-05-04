package com.fluid.dropx.network

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fluid.dropx.core.FileRegistry
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
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
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
    private val CHANNEL_ID = "transfer_service"
    private val NOTIFICATION_ID = 101
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>?=null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this,CHANNEL_ID)
            .setContentTitle("dropX-test")
            .setContentText("dropX-test")
            .setSmallIcon(R.drawable.stat_sys_upload)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        /*
        android 14+ requires explicitly declaring the foreground service type
        DATA_SYNC is appropriate for network-based file transfer operations
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startServer()

        // restart the service if the system kills it
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // required on Android 8.0+ before posting notifications
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Transfer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = this@TransferService.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startServer(){
        if (server!=null) return

        serviceScope.launch {
            try {
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
                        get("/api/files") {
                            val allFiles = FileRegistry.getAllMetadata()
                            // ContentNegotiation intercepts this and turns the list into JSON automatically
                            call.respond(allFiles)
                        }
                        get("/download/{fileId}"){
                            val id = call.parameters["fileId"] ?: return@get call.respond(
                                HttpStatusCode.Companion.BadRequest)
                            val uri = FileRegistry.getUri(id)

                            if (uri != null) {
                                try {
                                    val inputStream = contentResolver.openInputStream(uri)
                                    val descriptor = contentResolver.openAssetFileDescriptor(uri, "r")
                                    val fileSize = descriptor?.length ?: -1L
                                    descriptor?.close()

                                    if (inputStream != null) {
                                        val channel = inputStream.toByteReadChannel() // wraps InputStream into a non-blocking, coroutine based reader

                                        call.response.header(HttpHeaders.ContentLength,fileSize.toString())
                                        call.response.header(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())

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

                server?.start(wait = true)

            } catch (e: Exception){
            }
        }
    }

    private fun stopServer(){
        server?.stop(1000, 2000)
        server = null
    }

    override fun onDestroy() {
        stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }
}