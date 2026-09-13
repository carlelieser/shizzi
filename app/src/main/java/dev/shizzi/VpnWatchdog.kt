package dev.shizzi

import java.util.concurrent.atomic.AtomicBoolean

sealed interface VpnBinding {

    data class Adopted(val handle: Long) : VpnBinding

    data class Lost(val problem: String) : VpnBinding
}

fun interface VpnLocator {

    fun currentVpnHandle(): Long
}

class VpnWatchdog(
    private val locator: VpnLocator,
    private val onChange: (VpnBinding) -> Unit,
) {

    private val isRunning = AtomicBoolean(false)
    private var thread: Thread? = null

    private var boundHandle = UNBOUND

    private var consecutiveMisses = 0

    fun adoptCurrentVpn(): Long {
        boundHandle = locator.currentVpnHandle()
        return boundHandle
    }

    fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        thread = Thread({ monitor() }, "vpn-watchdog").apply {
            isDaemon = true
            start()
        }
    }

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

    internal fun evaluate(): VpnBinding? {
        val observed = locator.currentVpnHandle()

        if (observed != UNBOUND) return adopt(observed)

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

    private fun adopt(observed: Long): VpnBinding? {
        consecutiveMisses = 0
        if (observed == boundHandle) return null

        val previous = boundHandle
        boundHandle = observed
        SessionLog.info(adoptionMessage(previous, observed))
        return VpnBinding.Adopted(observed)
    }

    private fun adoptionMessage(previous: Long, adopted: Long): String = when (previous) {
        UNBOUND -> "vpn adopted: pinning the datapath to handle $adopted"
        else -> "vpn replaced: handle $previous is gone, re-pinning to $adopted"
    }

    private companion object {
        const val UNBOUND = 0L
        const val POLL_INTERVAL_MS = 5_000L

        const val MISSES_BEFORE_TEARDOWN = 2

        const val VPN_LOST_DETAIL =
            "VPN disconnected. Session stopped to keep devices protected."
    }
}
