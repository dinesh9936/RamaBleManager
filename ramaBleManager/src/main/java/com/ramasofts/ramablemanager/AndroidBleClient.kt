package com.ramasofts.ramablemanager

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidBleClient(
    private val appContext: Context
) : BleClient {

    companion object {
        private const val TAG = "AndroidBleClient"
    }

    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }

    private val connState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private var gatt: BluetoothGatt? = null
    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())

    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var isScanning: Boolean = false

    override fun isBluetoothSupported() = adapter != null
    override fun isBluetoothEnabled() = adapter?.isEnabled == true
    override fun hasRequiredPermissions() = BlePermissionHelper.hasAllPermissions(appContext)
    override fun missingPermissions() = BlePermissionHelper.missing(appContext)

    @SuppressLint("MissingPermission")
    override fun startScan(): Flow<BleScanDevice> = callbackFlow {
        // Preconditions
        if (!isBluetoothSupported()) {
            close(IllegalStateException("Bluetooth unsupported on your device."))
            return@callbackFlow
        }
        if (!isBluetoothEnabled()) {
            close(IllegalStateException("Bluetooth disabled on your device."))
            return@callbackFlow
        }
        if (!hasRequiredPermissions()) {
            close(IllegalStateException("Missing permissions: ${missingPermissions()}"))
            return@callbackFlow
        }
        if (!BlePermissionHelper.isLocationEnabledIfRequired(appContext)) {
            close(IllegalStateException("Location disabled."))
            return@callbackFlow
        }

        // Stop any previous scan defensively
        stopScanInternal()

        bleScanner = adapter!!.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result.isConnectable
                } else false

                trySend(
                    BleScanDevice(
                        name = result.device?.name,  // requires BLUETOOTH_CONNECT on API 31+
                        address = result.device?.address ?: return,
                        rssi = result.rssi,
                        isConnectable = connectable
                    )
                ).isSuccess
            }

            override fun onScanFailed(errorCode: Int) {
                Log.d(TAG, "onScanFailed: $errorCode")
                close(IllegalStateException("Scan failed: $errorCode"))
            }
        }

        scanCallback = cb
        bleScanner?.startScan(null, settings, cb)
        isScanning = true

        awaitClose { stopScanInternal() }
    }

    override fun stopScan() {
        stopScanInternal()
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        if (!isScanning) return
        runCatching {
            scanCallback?.let { cb -> bleScanner?.stopScan(cb) }
        }.onFailure { e ->
            Log.w(TAG, "stopScan error: ${e.message}")
        }
        isScanning = false
        scanCallback = null
        bleScanner = null
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String, autoConnect: Boolean): ConnectionState =
        suspendCancellableCoroutine { cont ->
            val device = adapter?.getRemoteDevice(address)
                ?: run {
                    val err = IllegalArgumentException("Device not found: $address")
                    connState.value = ConnectionState.Failed(err)
                    cont.resume(ConnectionState.Failed(err))
                    return@suspendCancellableCoroutine
                }

            connState.value = ConnectionState.Connecting

            val callback = object : BluetoothGattCallback() {

                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    // Non-success and not connected => fail
                    if (status != BluetoothGatt.GATT_SUCCESS &&
                        newState != BluetoothProfile.STATE_CONNECTED
                    ) {
                        val err = IllegalStateException("GATT status=$status state=$newState")
                        connState.value = ConnectionState.Failed(err)
                        if (cont.isActive) cont.resume(ConnectionState.Failed(err))
                        g.close()
                        return
                    }

                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            // Continue to discover services
                            g.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            connState.value = ConnectionState.Disconnected
                            _services.value = emptyList()
                            if (cont.isActive) cont.resume(ConnectionState.Disconnected)
                            g.close()
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // Map services -> characteristics
                        val infos = g.services.map { s ->
                            GattServiceInfo(
                                uuid = s.uuid,
                                characteristics = s.characteristics.map { c ->
                                    GattCharacteristicInfo(c.uuid, c.properties)
                                }
                            )
                        }
                        _services.value = infos
                        connState.value = ConnectionState.Connected
                        if (cont.isActive) cont.resume(ConnectionState.Connected)
                    } else {
                        val err = IllegalStateException("Discover failed: $status")
                        connState.value = ConnectionState.Failed(err)
                        if (cont.isActive) cont.resume(ConnectionState.Failed(err))
                    }
                }
            }

            // IMPORTANT: assign to the FIELD, not a new local
            gatt = device.connectGatt(appContext, autoConnect, callback)

            cont.invokeOnCancellation {
                runCatching {
                    gatt?.disconnect()
                    gatt?.close()
                }
                gatt = null
                connState.value = ConnectionState.Disconnected
                _services.value = emptyList()
            }
        }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        runCatching {
            gatt?.disconnect()
            gatt?.close()
        }
        gatt = null
        _services.value = emptyList()
        connState.value = ConnectionState.Disconnected
    }

    override fun connectionState(): Flow<ConnectionState> = connState

    // Optional: expose discovered services to UI
    override fun services(): Flow<List<GattServiceInfo>> = _services

    override suspend fun read(service: java.util.UUID, characteristic: java.util.UUID): ByteArray =
        error("Not implemented yet")

    override suspend fun write(
        service: java.util.UUID,
        characteristic: java.util.UUID,
        data: ByteArray,
        withResponse: Boolean
    ) = error("Not implemented yet")

    override fun notifications(service: java.util.UUID, characteristic: java.util.UUID): Flow<ByteArray> =
        error("Not implemented yet")
}
