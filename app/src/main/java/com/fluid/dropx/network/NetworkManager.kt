package com.fluid.dropx.network

import java.net.NetworkInterface
import java.net.Inet4Address
import android.util.Log
import com.fluid.dropx.model.NetworkResult

/*
* manages local network discovery
* responsible for identifying valid IPv4 addresses for hosting the ktor server
* */
class NetworkManager {

    /*
    * scans all physical network interfaces and returns a labelled result of potential ip addresses based on trust hierarchy
    * */
    fun getLocalNetworkData(): NetworkResult {
        var hotspotIp: String? = null
        var wifiIp: String? = null
        var unknownAdapterIp: String? = null // fallback, if unknown adapter names

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                // skip interfaces that are down, internal(loopback), virtual, ptp(cellular/vpn) to ensure local connectivity
                if (!networkInterface.isUp || networkInterface.isLoopback || networkInterface.isVirtual || networkInterface.isPointToPoint) {
                    continue
                }
                val name = networkInterface.name.lowercase()
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    // filter for high-quality, routable site-local IPv4 addresses
                    // we explicitly exclude Link-Local (169.254) and Multicast ranges
                    if (address is Inet4Address &&
                        !address.isLoopbackAddress && !address.isAnyLocalAddress && !address.isLinkLocalAddress && !address.isMulticastAddress
                        && address.isSiteLocalAddress) {
                        val ip = address.hostAddress
                        when {
                            // detect mobile hotspot
                            name.startsWith("ap") || name.startsWith("swlan") || name.startsWith("softap") -> hotspotIp = hotspotIp ?: ip
                            // detect Wi-Fi
                            name.startsWith("wlan") -> wifiIp = wifiIp ?: ip
                            // Fallback: if it supports multicast, it is likely a valid local network
                            networkInterface.supportsMulticast() -> unknownAdapterIp = unknownAdapterIp ?: ip
                            else -> {
                                Log.d("NetworkManager", "Ignoring non-multicast interface: $name")
                            }
                        }
                    }
                }
            }
        }
        catch (e: Exception){
            Log.e("NetworkManager", "Failed to scan physical network interfaces", e)
        }
        return NetworkResult(hotspotIp, wifiIp, unknownAdapterIp)
    }
}