package dev.shizzi

import android.content.Context

/**
 * Stops the hotspot, retrying while it stays tethered.
 *
 * stopTethering lands asynchronously, so a single immediate read can still see
 * the downstream up. See https://github.com/carlelieser/shizzi/issues/22
 */
fun releaseDownstreamWith(
    stop: () -> Boolean,
    findTethered: () -> String?,
    onRetry: (String) -> Unit = {},
): String? {
    var didAccept = false

    for (attempt in 1..DOWNSTREAM_STOP_ATTEMPTS) {
        didAccept = stop() || didAccept

        val stillTethered = findTethered()
        if (stillTethered == null) {
            return when {
                didAccept -> null
                else -> "stopTethering was rejected, but no downstream remains tethered"
            }
        }
        if (attempt == DOWNSTREAM_STOP_ATTEMPTS) return stillTethered

        onRetry("$stillTethered; retrying stopTethering")
    }
    return "downstream did not release after $DOWNSTREAM_STOP_ATTEMPTS attempts"
}

const val DOWNSTREAM_STOP_ATTEMPTS = 3

class SessionTeardown(private val context: Context) {

    private val inspector = UpstreamInspector()
    private var shutdownHook: Thread? = null

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

            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
        shutdownHook = null
    }

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

    fun releaseDownstream(): String? {
        val control = DownstreamControl(context)
        val inspector = DownstreamInspector()

        return releaseDownstreamWith(
            stop = { attemptStop(control) },
            findTethered = {
                runCatching { inspector.findTetheredDownstream() }
                    .getOrElse { failure -> "could not verify downstream: ${failure.message}" }
            },
            onRetry = SessionLog::warn,
        )
    }

    private fun attemptStop(control: DownstreamControl): Boolean =
        runCatching { control.stopWifiTethering() }
            .getOrElse { failure ->
                SessionLog.error("stopping the hotspot failed: ${failure.message}")
                false
            }

    private companion object {
        const val UPSTREAM_POLL_MS = 500L

        const val UPSTREAM_RELEASE_MS = 4_000L
    }
}
