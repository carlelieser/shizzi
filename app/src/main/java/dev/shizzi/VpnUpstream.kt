package dev.shizzi

import android.content.Context

/**
 * Keeps the datapath pinned to a VPN for as long as the session has one.
 *
 * Owns the adoption rule so TetherSession is left running the lifecycle rather
 * than also arbitrating what counts as a VPN worth binding to.
 *
 * Absence is not a failure: a session with no VPN runs unbound and tethers
 * exactly as it did before this existed. Refusing to start without one would
 * make the app useless to anyone not running a VPN, which is not what this is
 * for. What binding buys is that once a VPN *is* adopted, losing it fails the
 * dials instead of quietly falling back to the physical network.
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
     * Binds [group] to whatever VPN is up now, then follows it.
     *
     * Called before the downstream comes up so nothing can dial unbound: the
     * datapath exists by this point, and no client can be attached yet.
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
