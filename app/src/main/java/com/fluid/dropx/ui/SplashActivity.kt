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


        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                NetworkManager.refreshNetworkData()
            }

            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }
}