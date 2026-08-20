package dev.shizzi

import android.content.Context

/**
 * The ordering-sensitive release of what the framework owns — upstream
 * selection, downstream, and the shutdown hook for an abrupt exit. Split from
 * TetherSession so that class stays about the lifecycle it runs.
 */
class SessionTeardown(private val context: Context) {

    private val inspector = UpstreamInspector()
    private var shutdownHook: Thread? = null

    /**
     * Covers exactly one path: ShizukuServiceStarter calling System.exit when
     * its server goes away, which is how the 13.5.4 crash took this process
     * down.
     *
     * ART does not unwind hooks on signal death, so SIGTERM and SIGKILL bypass
     * this — a device test left the hotspot tethered with no hook output.
     * Recovery for those lives in the app process, which outlives this one.
     */
    fun installShutdownHook() {
        val hook = Thread {
            SessionLog.warn("process exiting with session active; dropping downstream")
            runCatching { DownstreamControl(context).stopWifiTethering() }
        }

        runCatching { Runtime.getRuntime().addShutdownHook(hook) }
            .onSuccess { shutdownHook = hook }
            .onFailure { SessionLog.warn("shutdown hook not installed: ${it.message}") }
    }

    fun removeShutdownHook() {
        shutdownHook?.let { hook ->
            // Throws if the JVM is already shutting down, which is exactly when
            // the hook should stay installed.
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
        shutdownHook = null
    }

    /**
     * Hands the upstream back *before* the TUN is destroyed. This ordering is
     * load-bearing: tethering's own teardown needs the interface to exist, so
     * destroying it first leaves the framework logging
     *
     *     [BpfCoordinator] Could not detach program: Fail to get interface
     *     params for interface testtunNN
     *
     * and still naming that dead interface as the upstream. Every later start
     * then fails verifyUpstream against a ghost — permanently, unclearable
     * short of a reboot. A device test wedged a phone this way for eleven
     * consecutive sessions.
     *
     * The wait is bounded and best effort: past the deadline, destroying the
     * interface still beats leaking it.
     */
    fun releaseUpstreamSelection(interfaceName: String?) {
        val cleared = runCatching { TetheringPreferenceApi(context).setPreferTestNetworks(false) }
            .onFailure {
                SessionLog.error(
                    "could not clear the test-network preference: ${it.message}; " +
                        "the upstream stays selected and the next start may fail",
                )
            }
        if (cleared.isFailure) return

        val name = interfaceName ?: return
        val deadline = System.currentTimeMillis() + UPSTREAM_RELEASE_MS

        while (System.currentTimeMillis() < deadline) {
            val observed = runCatching { inspector.observe().interfaceNames }.getOrDefault(emptyList())
            if (observed.none { it == name }) {
                SessionLog.info("upstream released: tethering moved off $name")
                return
            }
            Thread.sleep(UPSTREAM_POLL_MS)
        }

        SessionLog.warn("upstream still reads $name after ${UPSTREAM_RELEASE_MS}ms; releasing anyway")
    }

    /** @return null once no downstream is tethered, else what is still up. */
    fun releaseDownstream(): String? {
        val control = DownstreamControl(context)

        val didAccept = runCatching { control.stopWifiTethering() }
            .getOrElse { failure ->
                SessionLog.error("stopping the hotspot failed: ${failure.message}")
                false
            }

        val stillTethered = runCatching { DownstreamInspector().findTetheredDownstream() }
            .getOrElse { failure -> "could not verify downstream: ${failure.message}" }

        return when {
            stillTethered != null -> stillTethered
            didAccept -> null
            else -> "stopTethering was rejected, but no downstream remains tethered"
        }
    }

    private companion object {
        const val UPSTREAM_POLL_MS = 500L

        /**
         * Shorter than the settle deadline: letting go of an interface is not
         * selecting and validating a new one, and the session is on its way out.
         */
        const val UPSTREAM_RELEASE_MS = 4_000L
    }
}
