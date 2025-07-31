package com.ramasofts.ramablemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job

class MainActivity : ComponentActivity() {

    private val ble by lazy { Ble.create(this) }
    private var scanJob: Job? = null

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AppNavigator(bleClient = ble)
            }
        }
    }

    override fun onStop() {
        scanJob?.cancel()
        ble.stopScan()
        super.onStop()
    }

    override fun onDestroy() {
        scanJob?.cancel()
        ble.stopScan()
        super.onDestroy()
    }

    private fun log(msg: String) = android.util.Log.d("MainActivityCompose", msg)
}
