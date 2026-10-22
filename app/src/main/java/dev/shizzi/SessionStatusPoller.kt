package dev.shizzi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SessionStatusPoller(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun follow(
        isConnected: () -> Boolean,
        status: suspend () -> String,
        onStatus: (Result<String>) -> Unit,
    ) {
        stop()
        if (!isConnected()) return

        job = scope.launch {
            while (isConnected()) {
                delay(POLL_INTERVAL_MS)
                if (!isConnected()) return@launch
                val outcome = runCatching { status() }
                if (outcome.isSuccess) onStatus(outcome)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object { const val POLL_INTERVAL_MS = 1_000L }
}
