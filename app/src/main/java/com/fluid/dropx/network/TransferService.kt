package com.fluid.dropx.network

import android.R
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.os.Build
import android.os.IBinder
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startServer(){
        if (server!=null) return

        serviceScope.launch {
            try {
                server = embeddedServer(CIO, port = 1234, host = "0.0.0.0") {
                    install(CallLogging)

                    routing {
                        get("/"){
                            call.respondText("/ success")
                        }
                        get("/ping"){
                            call.respondText("ping success")
                        }
                    }
                }

                Log.d("TransferService", "passed on ${Thread.currentThread().name}")
                server?.start(wait = true)

            } catch (e: Exception){
                Log.e("TransferService","failed",e)
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