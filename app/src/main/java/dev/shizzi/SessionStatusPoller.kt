package dev.shizzi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SessionStatusPoller(
    private val scope: CoroutineScope,
    private val controller: TetherClient,
) {

    private var job: Job? = null

    fun follow(isConnected: () -> Boolean, onStatus: (Result<String>) -> Unit) {
        stop()
        if (!isConnected()) return

        job = scope.launch {
            while (isConnected()) {
                delay(POLL_INTERVAL_MS)
                if (!isConnected()) return@launch

                val outcome = runCatching { controller.status() }
                if (outcome.isSuccess) onStatus(outcome)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {

        const val POLL_INTERVAL_MS = 1_000L
    }
}
