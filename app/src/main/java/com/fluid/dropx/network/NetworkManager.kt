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

    // Dynamically checks if 50505 is free. If another app is using it, falls back to 0
    // which tells the OS to assign any random open ephemeral port so the server still boots.
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

    // Loops through all device hardware network cards to trap local IPv4 addresses.
    private fun getLocalNetworkData(): NetworkResult {
        var hotspotIp: String? = null
        var wifiIp: String? = null
        var unknownAdapterIp: String? = null

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                // Skip dead adapters, local loopbacks (127.0.0.1), software VPNs, or direct P2P cellular links
                if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual || networkInterface.isPointToPoint) {
                    continue
                }
                val name = networkInterface.name.lowercase()
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    // Filters specifically for valid, site-local IPv4 addresses (like 192.168.x.x or 10.x.x.x)
                    if (address is Inet4Address &&
                        !address.isLoopbackAddress && !address.isAnyLocalAddress && !address.isLinkLocalAddress && !address.isMulticastAddress
                        && address.isSiteLocalAddress) {
                        val ip = address.hostAddress

                        // Hardware-specific naming heuristic patterns used across different Android vendors
                        when {
                            // "ap"/"swlan"/"softap" usually points to an active native Wi-Fi tethering Hotspot interface
                            name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> hotspotIp = hotspotIp ?: ip
                            // "wlan" is the standard wireless card connected to a local home/work Wi-Fi router
                            name.startsWith("wlan") -> wifiIp = wifiIp ?: ip
                            // Fallback catch-all for bridged ethernet ports or emulators (like AVD cellular simulation adapters)
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