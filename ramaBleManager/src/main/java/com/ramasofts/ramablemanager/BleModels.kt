package com.ramasofts.ramablemanager

data class BleScanDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
    val isConnectable: Boolean = false
)

data class GattCharacteristicInfo(
    val uuid: java.util.UUID,
    val properties: Int
)

data class GattServiceInfo(
    val uuid: java.util.UUID,
    val characteristics: List<GattCharacteristicInfo>
)
