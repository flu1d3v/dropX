package com.fluid.dropx

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fluid.dropx.core.TransferManager


class MainActivity : ComponentActivity() {
    private lateinit var transferManager: TransferManager

    private val pickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {uris ->
        if (uris.isNotEmpty()) {
            transferManager.startTransferSession(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        transferManager = TransferManager(this)
        enableEdgeToEdge()
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { pickerLauncher.launch("*/*") }) {
                    Text("Select Files & Start dropX")
                }
            }
        }
    }
}

