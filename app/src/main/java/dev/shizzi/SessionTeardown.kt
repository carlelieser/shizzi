package dev.shizzi

import android.content.Context
import android.util.Log

/**
 * Releases what a session holds outside its own resource group.
 *
 * Split from TetherSession so that class stays about the lifecycle it runs;
 * this is the ordering-sensitive release of things the framework owns — the
 * upstream selection, the downstream, and the shutdown hook that covers an
 * abrupt process exit.
 */
class SessionTeardown(private val context: Context) {

    private val inspector = UpstreamInspector()
    private var shutdownHook: Thread? = null

    /**
     * Drops the downstream if this process exits through System.exit.
     *
     * Covers exactly one path: ShizukuServiceStarter calling System.exit when
     * the server it depends on goes away. That is a real path — it is how the
     * 13.5.4 crash took this process down — but it is the only one.
     *
     * ART does not unwind shutdown hooks on signal death, so SIGTERM and
     * SIGKILL both bypass this entirely; a device test confirmed SIGTERM
     * leaves the hotspot tethered with no hook output. Recovery for those
     * cases lives in the app process, which outlives this one (see
     * SessionViewModel's session-lost handling).
     */
    fun installShutdownHook() {
        val hook = Thread {
            Log.w(TAG, "process exiting with session active; dropping downstream")
            runCatching { DownstreamControl(context).stopWifiTethering() }
        }

        runCatching { Runtime.getRuntime().addShutdownHook(hook) }
            .onSuccess { shutdownHook = hook }
            .onFailure { Log.w(TAG, "installShutdownHook: ${it.message}") }
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
     * Hands the upstream back to the framework *before* the TUN is destroyed.
     *
     * This ordering is load-bearing. Destroying the interface first leaves
     * tethering holding a reference it can no longer act on: its own teardown
     * needs the interface to exist, so it logs
     *
     *     [BpfCoordinator] Could not detach program: Fail to get interface
     *     params for interface testtunNN
     *
     * and keeps naming that dead interface as the current upstream. Every
     * subsequent start then fails verifyUpstream against a ghost, permanently,
     * with nothing the app can do about it afterward and no way for a user to
     * clear it short of a reboot. A device test wedged a phone exactly this way
     * for eleven consecutive sessions.
     *
     * So the preference is cleared while the TUN is still up, and this waits
     * for tethering to actually move off it. The wait is bounded and best
     * effort: if the framework has not let go by the deadline, destroying the
     * interface is still better than leaking it, and the next start reselects
     * from whatever remains.
     */
    fun releaseUpstreamSelection(interfaceName: String?) {
        val cleared = runCatching { TetheringPreferenceApi(context).setPreferTestNetworks(false) }
            .onFailure { Log.w(TAG, "stop: preference ${it.message}") }
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
                Log.w(TAG, "stop: downstream ${failure.message}")
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
        const val TAG = "SessionTeardown"
        const val UPSTREAM_POLL_MS = 500L

        /**
         * How long teardown waits for tethering to release the owned TUN.
         *
         * Shorter than the settle deadline: this is the framework letting go of
         * an interface rather than selecting and validating a new one, and the
         * session is already on its way out. Exceeding it is logged and the
         * interface destroyed regardless — leaking the TUN would be worse.
         */
        const val UPSTREAM_RELEASE_MS = 4_000L
    }
}
