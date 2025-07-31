package com.ramasofts.ramablemanager

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun BleScannerScreen(
    ble: BleClient,
    defaultTimeoutMs: Long = 10_000L,
    onRequestPermissions: () -> Unit,
    onStartScan: (timeoutMs: Long, onDevice: (BleScanDevice) -> Unit) -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (BleScanDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    var isScanning by remember { mutableStateOf(false) }
    var timeoutMs by remember { mutableLongStateOf(defaultTimeoutMs) }
    val devices = remember { mutableStateListOf<BleScanDevice>() }
    val seen = remember { hashSetOf<String>() }

    LaunchedEffect(Unit) {
        if (ble.missingPermissions().isNotEmpty()) onRequestPermissions()
    }

    LaunchedEffect(isScanning) {
        if (isScanning) {
            devices.clear()
            seen.clear()
            onStartScan(timeoutMs) { dev ->
                if (seen.add(dev.address)) devices.add(dev)
            }
        } else {
            onStopScan()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "BLE Scanner",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = (timeoutMs / 1000).toString(),
                onValueChange = { s ->
                    s.toLongOrNull()?.let { secs -> timeoutMs = secs * 1000 }
                },
                label = { Text("Timeout (sec)") },
                singleLine = true,
                modifier = Modifier.width(160.dp)
            )

            Button(
                enabled = !isScanning && ble.missingPermissions().isEmpty() && ble.isBluetoothEnabled(),
                onClick = { isScanning = true }
            ) { Text("Start") }

            OutlinedButton(
                enabled = isScanning,
                onClick = { isScanning = false }
            ) { Text("Stop") }
        }

        Text(
            text = "Found devices (${devices.size})",
            style = MaterialTheme.typography.titleMedium
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = devices,
                key = { it.address }
            ) { dev ->
                DeviceRow(
                    dev = dev,
                    onClick = {
                        onDeviceClick(dev)
                    }
                )
            }
        }
    }
}

@Composable
fun DeviceRow(
    dev: BleScanDevice,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .border(1.dp, Color.Blue, shape = MaterialTheme.shapes.medium)
            .then(clickableModifier),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dev.name ?: "UNKNOWN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (dev.isConnectable) "( Connectable )" else "( Not Connectable )",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (dev.isConnectable) Color.Green else Color.Gray
                )
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = dev.address,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // RSSI ProgressBar
            LinearProgressIndicator(
                progress = ((dev.rssi + 100) / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = Color.Blue,
                trackColor = Color.LightGray
            )

            Text(
                text = "RSSI: ${dev.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
