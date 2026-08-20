package dev.shizzi

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.atomic.AtomicBoolean

/** What the poller last observed, as the session needs to act on it. */
sealed interface VpnBinding {

    /** A VPN to pin the datapath to; [handle] feeds Session.setNetwork. */
    data class Adopted(val handle: Long) : VpnBinding

    /** The adopted VPN is gone and has stayed gone. Fail closed. */
    data class Lost(val problem: String) : VpnBinding
}

/**
 * Follows the VPN the datapath's sockets are pinned to, and reports its loss.
 * The datapath dials from this process, so the question is only authoritative
 * here.
 *
 * Polls rather than registering a callback: from the shell UID
 * registerNetworkCallback is rejected with "Package android does not belong to
 * 2000", while getAllNetworks carries no such check.
 *
 * A session that never sees a VPN runs unbound and is never torn down by this.
 * Losing an adopted one is a failure — and binding is what makes the promise
 * safe, since those sockets stop working rather than falling back, so the
 * window before this notices carries no untunnelled traffic.
 */
class VpnWatchdog(
    private val connectivityManager: ConnectivityManager,
    private val onChange: (VpnBinding) -> Unit,
) {

    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    /** 0 while unbound, a handle once adopted. Touched only on the poll thread. */
    private var boundHandle = UNBOUND

    /**
     * One miss is not evidence: a VPN app restarting can leave a single poll
     * empty, and a one-sample teardown turns that into a dropped hotspot.
     */
    private var consecutiveMisses = 0

    /**
     * Adopts whatever VPN is up now, so a session starting under one is bound
     * before any client can dial. Mutates [boundHandle] rather than only
     * reporting it, or the first poll reads the same VPN as a change.
     */
    fun adoptCurrentVpn(): Long {
        boundHandle = currentVpnHandle()
        return boundHandle
    }

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        thread = Thread({ monitor() }, "vpn-watchdog").apply {
            isDaemon = true
            start()
        }
    }

    /** Safe to call from [onChange], for the reason SessionWatchdog.stop documents. */
    fun stop() {
        isRunning.set(false)

        val watcher = thread
        thread = null
        if (watcher != null && watcher != Thread.currentThread()) watcher.interrupt()
    }

    private fun monitor() {
        while (isRunning.get()) {
            val didSleep = runCatching { Thread.sleep(POLL_INTERVAL_MS) }.isSuccess
            if (!didSleep || !isRunning.get()) return

            val binding = evaluate() ?: continue
            if (binding is VpnBinding.Lost) isRunning.set(false)

            onChange(binding)
            if (binding is VpnBinding.Lost) return
        }
    }

    /**
     * @return null while nothing needs to change, else what the session must do.
     *
     * The strike counter counts *absence*, never *change*: a reconnecting VPN
     * comes back as a new Network with a new handle and no stable identity
     * across the gap, so treating a changed handle as loss would tear the
     * session down on every reconnect.
     */
    private fun evaluate(): VpnBinding? {
        val observed = currentVpnHandle()

        if (observed != UNBOUND) return adopt(observed)

        // Never adopted: this session makes no VPN promise to break.
        if (boundHandle == UNBOUND) return null

        consecutiveMisses++
        if (consecutiveMisses < MISSES_BEFORE_TEARDOWN) {
            SessionLog.warn(
                "vpn strike $consecutiveMisses/$MISSES_BEFORE_TEARDOWN: " +
                    "no VPN present, session is bound to $boundHandle",
            )
            return null
        }
        return VpnBinding.Lost(VPN_LOST_DETAIL)
    }

    /** @return null when [observed] is already the bound network. */
    private fun adopt(observed: Long): VpnBinding? {
        consecutiveMisses = 0
        if (observed == boundHandle) return null

        val previous = boundHandle
        boundHandle = observed
        SessionLog.info(adoptionMessage(previous, observed))
        return VpnBinding.Adopted(observed)
    }

    private fun currentVpnHandle(): Long = findVpn()?.let(::handleOf) ?: UNBOUND

    /**
     * The first network reporting TRANSPORT_VPN; the session's own TUN carries
     * TRANSPORT_TEST and cannot be mistaken for one. Two VPNs can coexist
     * mid-handover, and picking the first beats refusing to pick — the handover
     * then reads as a re-adopt next poll.
     *
     * A throw returns null without counting as a strike: a binder hiccup is not
     * evidence the VPN is gone.
     */
    private fun findVpn(): Network? = runCatching {
        connectivityManager.allNetworks.firstOrNull { candidate ->
            connectivityManager.getNetworkCapabilities(candidate)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    }.getOrElse { failure ->
        SessionLog.warn("could not read the VPN list: ${failure.message}")
        null
    }

    private fun handleOf(network: Network): Long =
        runCatching { network.networkHandle }.getOrDefault(UNBOUND)

    private fun adoptionMessage(previous: Long, adopted: Long): String = when (previous) {
        UNBOUND -> "vpn adopted: pinning the datapath to handle $adopted"
        else -> "vpn replaced: handle $previous is gone, re-pinning to $adopted"
    }

    private companion object {
        const val UNBOUND = 0L
        const val POLL_INTERVAL_MS = 5_000L

        /** Two strikes, matching SessionWatchdog: one miss is not evidence. */
        const val MISSES_BEFORE_TEARDOWN = 2

        /** Reaches the user verbatim, through the session's detail string. */
        const val VPN_LOST_DETAIL =
            "VPN disconnected. Session stopped to keep devices protected."
    }
}
