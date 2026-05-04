package com.fluid.dropx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fluid.dropx.core.FileRegistry
import com.fluid.dropx.network.NetworkManager
import com.fluid.dropx.network.TransferService


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {

        }
    }

    private fun startTransfer(selectedUris: List<Uri>) {
        val networkManager = NetworkManager()
        val networkResult = networkManager.getLocalNetworkData()

        if (networkResult.hasAnyConnection()) {
            val serverIp = networkResult.wifiIp ?: networkResult.hotspotIp ?: networkResult.unknownIp

            selectedUris.forEach { uri ->
                FileRegistry.addFile(uri, "File_${System.currentTimeMillis()}", 0L, "application/octet-stream")
            }

            val intent = Intent(this, TransferService::class.java)
            startService(intent)
        }
        else {
            //
        }
    }
}



