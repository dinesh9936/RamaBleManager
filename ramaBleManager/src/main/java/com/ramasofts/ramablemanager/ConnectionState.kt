package com.ramasofts.ramablemanager

// In your library (shared)
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()            // connecting / discovering
    data object Connected : ConnectionState()
    data class Failed(val error: Throwable) : ConnectionState()
}
