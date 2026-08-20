package dev.shizzi

import android.content.Context

/**
 * Keeps the datapath pinned to a VPN for as long as the session has one, so
 * TetherSession runs the lifecycle without also arbitrating what counts as a
 * VPN worth binding to.
 *
 * Absence is not a failure — a session with no VPN runs unbound and tethers
 * normally. What binding buys is that once one *is* adopted, losing it fails
 * the dials instead of quietly falling back to the physical network.
 */
class VpnUpstream(
    private val context: Context,
    private val onLost: (String) -> Unit,
) {

    private var watchdog: VpnWatchdog? = null

    /** 0 while unbound; the adopted VPN's handle once one is in use. */
    var handle = UNBOUND
        private set

    val isBound: Boolean get() = handle != UNBOUND

    /**
     * Called before the downstream comes up so nothing can dial unbound: the
     * datapath exists by now, and no client can be attached yet.
     */
    fun follow(group: SessionResources) {
        val watcher = VpnWatchdog(context.connectivityManager()) { binding ->
            apply(group, binding)
        }
        watchdog = watcher

        val adopted = watcher.adoptCurrentVpn()
        if (adopted != UNBOUND) {
            group.bindDatapathTo(adopted)
            handle = adopted
            SessionLog.info("vpn present at start: datapath pinned to handle $adopted")
        }
        watcher.start()
    }

    fun stop() {
        watchdog?.stop()
        watchdog = null
        handle = UNBOUND
    }

    private fun apply(group: SessionResources, binding: VpnBinding) {
        when (binding) {
            is VpnBinding.Adopted -> {
                group.bindDatapathTo(binding.handle)
                handle = binding.handle
            }

            is VpnBinding.Lost -> {
                SessionLog.warn("vpn lost: ${binding.problem}")
                handle = UNBOUND
                onLost(binding.problem)
            }
        }
    }

    private companion object {
        /** No VPN adopted; the datapath dials over the default network. */
        const val UNBOUND = 0L
    }
}
