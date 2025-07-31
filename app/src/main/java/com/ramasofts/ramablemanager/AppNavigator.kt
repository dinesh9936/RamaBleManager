package com.ramasofts.ramablemanager

import DeviceDetailScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@Composable
fun AppNavigator(bleClient: BleClient) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var scanJob by remember { mutableStateOf<Job?>(null) }

    NavHost(navController, startDestination = "scanner") {
        composable("scanner") {
            BleScannerScreen(
                ble = bleClient,
                onRequestPermissions = { /* no-op here, handled in MainActivity */ },
                onStartScan = { timeoutMs, onDevice ->
                    scanJob?.cancel()
                    bleClient.stopScan()

                    scanJob = coroutineScope.launch {
                        val seen = hashSetOf<String>()
                        val collector = launch {
                            bleClient.startScan()
                                .onStart { android.util.Log.d("Scan", "Started") }
                                .catch { e -> android.util.Log.e("Scan", "Error: ${e.message}") }
                                .collect { device ->
                                    if (seen.add(device.address)) {
                                        onDevice(device)
                                    }
                                }
                        }
                        delay(timeoutMs)
                        bleClient.stopScan()
                        collector.join()
                        android.util.Log.d("Scan", "Stopped after timeout")
                    }
                },
                onStopScan = {
                    scanJob?.cancel()
                    bleClient.stopScan()
                },
                onDeviceClick = { device ->
                    navController.navigate(
                        "detail/${device.name ?: "UNKNOWN"}/${device.address}/${device.rssi}/${device.isConnectable}"
                    )
                }
            )
        }

        // Detail screen remains unchanged...
    }
}

