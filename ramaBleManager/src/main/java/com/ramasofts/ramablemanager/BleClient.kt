package com.ramasofts.ramablemanager

import kotlinx.coroutines.flow.Flow
import java.util.UUID


interface BleClient {
    fun isBluetoothSupported(): Boolean
    fun isBluetoothEnabled(): Boolean

    fun hasRequiredPermissions(): Boolean
    fun missingPermissions(): List<String>

    fun startScan(): Flow<BleScanDevice>
    fun stopScan()

    fun services(): Flow<List<GattServiceInfo>>


    suspend fun connect(address: String, autoConnect: Boolean = false): ConnectionState
    fun connectionState(): Flow<ConnectionState>
    fun disconnect()

    suspend fun read(service: UUID, characteristic: UUID): ByteArray
    suspend fun write(service: UUID, characteristic: UUID, data: ByteArray, withResponse: Boolean = true)

    fun notifications(service: UUID, characteristic: UUID): Flow<ByteArray>
}