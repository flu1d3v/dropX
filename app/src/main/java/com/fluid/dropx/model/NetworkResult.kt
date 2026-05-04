package com.fluid.dropx.model

/*
* a pure container for identified IP addresses
* */
data class NetworkResult(
    val hotspotIp: String? = null,
    val wifiIp: String? = null,
    val unknownIp: String? = null
) {
    // helper to quickly check if the phone is totally offline
    fun hasAnyConnection(): Boolean = hotspotIp != null || wifiIp != null || unknownIp != null
}