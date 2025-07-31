package com.ramasofts.ramablemanager

data class RamaBleManagerResponse(
    var statusCode: Int = 100,
    var message: String? = null,
    var data: Any? = null
)
