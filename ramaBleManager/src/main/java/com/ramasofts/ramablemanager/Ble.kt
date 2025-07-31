package com.ramasofts.ramablemanager


import android.content.Context

object Ble {
    fun create(context: Context): BleClient =
        AndroidBleClient(context.applicationContext)
}