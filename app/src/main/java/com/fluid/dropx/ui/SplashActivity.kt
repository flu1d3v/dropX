package com.fluid.dropx.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.fluid.dropx.MainActivity
import com.fluid.dropx.network.NetworkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launches a coroutine bound directly to this window's lifecycle scope.
        // It guarantees background operations stop immediately if the app window closes mid-boot.
        lifecycleScope.launch {
            // Dispatches to the IO thread pool to scan network hardware interfaces.
            // This prevents freezing the main main rendering thread during startup calculations.
            withContext(Dispatchers.IO) {
                NetworkManager.refreshNetworkData()
            }

            // Once the network configurations are loaded into memory, swap directly to the main workspace layout
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))

            // Kill this activity context immediately so hitting the physical back button
            // inside the main workspace drops the user home instead of looping back into the splash screen.
            finish()
        }
    }
}