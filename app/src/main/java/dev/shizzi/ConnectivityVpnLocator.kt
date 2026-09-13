package dev.shizzi

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

internal const val UNBOUND = 0L

internal data class VpnCandidate(val handle: Long, val isVpn: Boolean, val isActive: Boolean)

internal fun selectVpnHandle(candidates: List<VpnCandidate>): Long {
    val active = candidates.firstOrNull { it.isActive } ?: return UNBOUND
    if (!active.isVpn) return UNBOUND

    return active.handle
}

class ConnectivityVpnLocator(
    private val connectivityManager: ConnectivityManager,
) : VpnLocator {

    override fun currentVpnHandle(): Long = runCatching {
        selectVpnHandle(candidates())
    }.getOrElse { failure ->
        SessionLog.warn("could not read the active network: ${failure.message}")
        UNBOUND
    }

    private fun candidates(): List<VpnCandidate> {
        val active = connectivityManager.activeNetwork

        return connectivityManager.allNetworks.map { network ->
            VpnCandidate(
                handle = handleOf(network),
                isVpn = isVpn(network),
                isActive = network == active,
            )
        }
    }

    private fun isVpn(network: Network): Boolean =
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    private fun handleOf(network: Network): Long =
        runCatching { network.networkHandle }.getOrDefault(UNBOUND)
}
