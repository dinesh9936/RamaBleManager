import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ramasofts.ramablemanager.BleClient
import com.ramasofts.ramablemanager.ConnectionState
import com.ramasofts.ramablemanager.GattServiceInfo
import androidx.compose.foundation.lazy.items


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    ble: BleClient,
    name: String,
    address: String,
    rssi: Int,
    isConnectable: Boolean,
    onNavigateUp: () -> Unit
) {
    val connectionState by ble.connectionState().collectAsState(initial = ConnectionState.Disconnected)
    val services by ble.services().collectAsState(initial = emptyList())

    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var triggerConnect by remember { mutableStateOf(false) }

    // Launch BLE connect when triggerConnect is set to true
    LaunchedEffect(triggerConnect) {
        if (triggerConnect) {
            connecting = true
            error = null
            val result = ble.connect(address, autoConnect = false)
            if (result is ConnectionState.Failed) {
                error = result.error.message ?: "Connect failed"
            }
            connecting = false
            triggerConnect = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(name.ifBlank { "UNKNOWN DEVICE" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Address: $address")
            Text("RSSI: $rssi dBm")
            Text(if (isConnectable) "Connectable" else "Not Connectable")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !connecting && connectionState !is ConnectionState.Connected,
                    onClick = { triggerConnect = true }
                ) { Text(if (connecting) "Connecting..." else "Connect") }

                OutlinedButton(
                    enabled = connectionState is ConnectionState.Connected,
                    onClick = { ble.disconnect() }
                ) { Text("Disconnect") }
            }

            when (val state = connectionState) {
                is ConnectionState.Disconnected -> Text("Disconnected", color = MaterialTheme.colorScheme.error)
                is ConnectionState.Connecting -> Text("Connecting…")
                is ConnectionState.Connected -> Text("Connected", color = MaterialTheme.colorScheme.primary)
                is ConnectionState.Failed -> Text("Failed: ${state.error.message}", color = MaterialTheme.colorScheme.error)
            }

            error?.let {
                Text("Error: $it", color = MaterialTheme.colorScheme.error)
            }

            Divider()

            Text("Services (${services.size})", style = MaterialTheme.typography.titleMedium)

            if (services.isEmpty() && connectionState is ConnectionState.Connected) {
                Text("No services discovered.")
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(services) { svc ->
                    ServiceCard(svc)
                }
            }

        }
    }
}

@Composable
private fun ServiceCard(svc: GattServiceInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Service: ${svc.uuid}", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            svc.characteristics.forEach { ch ->
                Text(
                    text = "• Characteristic: ${ch.uuid} (props=${ch.properties})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
