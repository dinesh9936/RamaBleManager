package com.ramasofts.ramablemanager

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

object BlePermissionHelper {
    fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasAllPermissions(context: Context): Boolean =
        requiredPermissions().all { hasPermission(context, it) }

    fun missing(context: Context): List<String> =
        requiredPermissions().filterNot { hasPermission(context, it) }

    private fun hasPermission(context: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PermissionChecker.PERMISSION_GRANTED

    fun isLocationEnabledIfRequired(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}