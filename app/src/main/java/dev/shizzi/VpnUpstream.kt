package dev.shizzi

import android.content.Context

class VpnUpstream(
    private val context: Context,
    private val onLost: (String) -> Unit,
) {

    private var watchdog: VpnWatchdog? = null

    var handle = UNBOUND
        private set

    val isBound: Boolean get() = handle != UNBOUND

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

        const val UNBOUND = 0L
    }
}
