package dev.shizzi

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class ConnectivityVpnLocator(
    private val connectivityManager: ConnectivityManager,
) : VpnLocator {

    override fun currentVpn(): Network? = runCatching {
        connectivityManager.allNetworks.firstOrNull(::isVpn)
    }.getOrElse { failure ->
        SessionLog.warn("could not read the VPN list: ${failure.message}")
        null
    }

    private fun isVpn(network: Network): Boolean =
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
}
