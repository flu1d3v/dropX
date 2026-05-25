package com.fluid.dropx.network

import java.net.NetworkInterface
import java.net.Inet4Address
import com.fluid.dropx.model.NetworkResult

object NetworkManager {
    private const val PREFERRED_PORT = 50505
    var activePort: Int = PREFERRED_PORT
        private set
    @Volatile
    var currentResult: NetworkResult = NetworkResult()
        private set

    fun findAvailablePort(): Int {
        activePort = try {
            val socket = java.net.ServerSocket(PREFERRED_PORT)
            val port = socket.localPort
            socket.close()
            port
        } catch (e: Exception) {
            val socket = java.net.ServerSocket(0)
            val port = socket.localPort
            socket.close()
            port
        }
        return activePort
    }

    fun refreshNetworkData(): NetworkResult {
        val result = getLocalNetworkData()
        currentResult = result
        return result
    }


    private fun getLocalNetworkData(): NetworkResult {
        var hotspotIp: String? = null
        var wifiIp: String? = null
        var unknownAdapterIp: String? = null

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual || networkInterface.isPointToPoint) {
                    continue
                }
                val name = networkInterface.name.lowercase()
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (address is Inet4Address &&
                        !address.isLoopbackAddress && !address.isAnyLocalAddress && !address.isLinkLocalAddress && !address.isMulticastAddress
                        && address.isSiteLocalAddress) {
                        val ip = address.hostAddress
                        when {
                            name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> hotspotIp = hotspotIp ?: ip
                            name.startsWith("wlan") -> wifiIp = wifiIp ?: ip
                            networkInterface.supportsMulticast() -> unknownAdapterIp = unknownAdapterIp ?: ip
                        }
                    }
                }
            }
        }
        catch (e: Exception){
            null
        }
        return NetworkResult(hotspotIp, wifiIp, unknownAdapterIp)
    }
}